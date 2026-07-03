/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.ozone.om.request.key;

import static org.apache.hadoop.ozone.OzoneConsts.DELETED_HSYNC_KEY;
import static org.apache.hadoop.ozone.om.exceptions.OMException.ResultCodes.KEY_NOT_FOUND;
import static org.apache.hadoop.ozone.om.lock.OzoneManagerLock.LeveledResource.BUCKET_LOCK;
import static org.apache.hadoop.ozone.util.MetricUtil.captureLatencyNs;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.hadoop.hdds.client.RatisReplicationConfig;
import org.apache.hadoop.hdds.protocol.proto.HddsProtos.ReplicationFactor;
import org.apache.hadoop.hdds.utils.db.Table;
import org.apache.hadoop.hdds.utils.db.cache.CacheKey;
import org.apache.hadoop.hdds.utils.db.cache.CacheValue;
import org.apache.hadoop.ozone.OmUtils;
import org.apache.hadoop.ozone.OzoneConsts;
import org.apache.hadoop.ozone.audit.AuditLogger;
import org.apache.hadoop.ozone.audit.OMAction;
import org.apache.hadoop.ozone.om.OMMetadataManager;
import org.apache.hadoop.ozone.om.OMMetrics;
import org.apache.hadoop.ozone.om.OMPerformanceMetrics;
import org.apache.hadoop.ozone.om.OzoneManager;
import org.apache.hadoop.ozone.om.exceptions.OMException;
import org.apache.hadoop.ozone.om.execution.flowcontrol.ExecutionContext;
import org.apache.hadoop.ozone.om.helpers.BucketLayout;
import org.apache.hadoop.ozone.om.helpers.BucketVersioningStatus;
import org.apache.hadoop.ozone.om.helpers.OmBucketInfo;
import org.apache.hadoop.ozone.om.helpers.OmKeyInfo;
import org.apache.hadoop.ozone.om.helpers.OmKeyLocationInfoGroup;
import org.apache.hadoop.ozone.om.request.util.OmResponseUtil;
import org.apache.hadoop.ozone.om.request.validation.RequestFeatureValidator;
import org.apache.hadoop.ozone.om.request.validation.ValidationCondition;
import org.apache.hadoop.ozone.om.request.validation.ValidationContext;
import org.apache.hadoop.ozone.om.response.OMClientResponse;
import org.apache.hadoop.ozone.om.response.key.OMKeyDeleteMarkerResponse;
import org.apache.hadoop.ozone.om.response.key.OMKeyDeleteResponse;
import org.apache.hadoop.ozone.om.response.key.OMKeyVersionDeleteResponse;
import org.apache.hadoop.ozone.om.upgrade.OMLayoutFeature;
import org.apache.hadoop.ozone.protocol.proto.OzoneManagerProtocolProtos;
import org.apache.hadoop.ozone.protocol.proto.OzoneManagerProtocolProtos.DeleteKeyRequest;
import org.apache.hadoop.ozone.protocol.proto.OzoneManagerProtocolProtos.DeleteKeyResponse;
import org.apache.hadoop.ozone.protocol.proto.OzoneManagerProtocolProtos.KeyArgs;
import org.apache.hadoop.ozone.protocol.proto.OzoneManagerProtocolProtos.OMRequest;
import org.apache.hadoop.ozone.protocol.proto.OzoneManagerProtocolProtos.OMResponse;
import org.apache.hadoop.ozone.protocol.proto.OzoneManagerProtocolProtos.Type;
import org.apache.hadoop.ozone.request.validation.RequestProcessingPhase;
import org.apache.hadoop.ozone.security.acl.IAccessAuthorizer.ACLType;
import org.apache.hadoop.util.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles DeleteKey request.
 */
public class OMKeyDeleteRequest extends OMKeyRequest {

  private static final Logger LOG =
      LoggerFactory.getLogger(OMKeyDeleteRequest.class);

  public OMKeyDeleteRequest(OMRequest omRequest, BucketLayout bucketLayout) {
    super(omRequest, bucketLayout);
  }

  @Override
  public OMRequest preExecute(OzoneManager ozoneManager) throws IOException {
    DeleteKeyRequest deleteKeyRequest = super.preExecute(ozoneManager)
        .getDeleteKeyRequest();
    Objects.requireNonNull(deleteKeyRequest, "deleteKeyRequest == null");

    OzoneManagerProtocolProtos.KeyArgs keyArgs = deleteKeyRequest.getKeyArgs();
    String keyPath = keyArgs.getKeyName();

    OmUtils.verifyKeyNameWithSnapshotReservedWordForDeletion(keyPath);
    keyPath = normalizeKeyPath(ozoneManager.getEnableFileSystemPaths(), keyPath, getBucketLayout());

    OzoneManagerProtocolProtos.KeyArgs.Builder newKeyArgs =
        keyArgs.toBuilder().setModificationTime(Time.now()).setKeyName(keyPath);

    KeyArgs resolvedArgs = resolveBucketAndCheckAcls(ozoneManager, newKeyArgs);
    return getOmRequest().toBuilder()
        .setDeleteKeyRequest(deleteKeyRequest.toBuilder()
            .setKeyArgs(resolvedArgs))
        .build();
  }

  protected KeyArgs resolveBucketAndCheckAcls(OzoneManager ozoneManager,
      KeyArgs.Builder newKeyArgs) throws IOException {
    return captureLatencyNs(
          ozoneManager.getPerfMetrics().getDeleteKeyResolveBucketAndAclCheckLatencyNs(),
          () -> resolveBucketAndCheckKeyAcls(newKeyArgs.build(), ozoneManager, ACLType.DELETE));
  }

  @Override
  @SuppressWarnings("methodlength")
  public OMClientResponse validateAndUpdateCache(OzoneManager ozoneManager, ExecutionContext context) {
    final long trxnLogIndex = context.getIndex();
    DeleteKeyRequest deleteKeyRequest = getOmRequest().getDeleteKeyRequest();

    OzoneManagerProtocolProtos.KeyArgs keyArgs = deleteKeyRequest.getKeyArgs();
    Map<String, String> auditMap = buildLightKeyArgsAuditMap(keyArgs);

    String volumeName = keyArgs.getVolumeName();
    String bucketName = keyArgs.getBucketName();
    String keyName = keyArgs.getKeyName();

    OMMetrics omMetrics = ozoneManager.getMetrics();
    omMetrics.incNumKeyDeletes();
    OMPerformanceMetrics perfMetrics = ozoneManager.getPerfMetrics();
    AuditLogger auditLogger = ozoneManager.getAuditLogger();
    OzoneManagerProtocolProtos.UserInfo userInfo = getOmRequest().getUserInfo();

    OMResponse.Builder omResponse =
        OmResponseUtil.getOMResponseBuilder(getOmRequest());
    OMMetadataManager omMetadataManager = ozoneManager.getMetadataManager();
    Exception exception = null;
    boolean acquiredLock = false;
    OMClientResponse omClientResponse = null;
    Result result = null;
    boolean visibleKeyRemoved = false;
    long startNanos = Time.monotonicNowNanos();
    try {
      String objectKey =
          omMetadataManager.getOzoneKey(volumeName, bucketName, keyName);

      mergeOmLockDetails(omMetadataManager.getLock()
          .acquireWriteLock(BUCKET_LOCK, volumeName, bucketName));
      acquiredLock = getOmLockDetails().isLockAcquired();

      // Validate bucket and volume exists or not.
      validateBucketAndVolume(omMetadataManager, volumeName, bucketName);

      OmBucketInfo omBucketInfo =
          getBucketInfo(omMetadataManager, volumeName, bucketName);
      OmKeyInfo omKeyInfo =
          omMetadataManager.getKeyTable(getBucketLayout()).get(objectKey);
      final Long requestedVersionId =
          keyArgs.hasVersionId() ? keyArgs.getVersionId() : null;

      if (requestedVersionId != null) {
        // S3-compatible versioning: permanently delete the addressed version.
        // No delete marker is inserted; when the current version is removed,
        // the newest noncurrent version (if any) becomes current again.
        if (omBucketInfo.getVersioningStatus() == BucketVersioningStatus.UNVERSIONED) {
          throw new OMException("Cannot delete a specific version of key " + keyName
              + ": versioning has never been enabled on bucket " + bucketName,
              OMException.ResultCodes.INVALID_REQUEST);
        }
        boolean deletingCurrent = omKeyInfo != null
            && (omKeyInfo.getVersionId() != null
                ? omKeyInfo.getVersionId() == requestedVersionId.longValue()
                : requestedVersionId == OmKeyInfo.NULL_VERSION_ID);
        String deletedVersionedKeyName = null;
        OmKeyInfo versionToDelete;
        if (deletingCurrent) {
          versionToDelete = omKeyInfo;
        } else {
          deletedVersionedKeyName = omMetadataManager.getVersionedOzoneKey(
              volumeName, bucketName, keyName, requestedVersionId);
          versionToDelete =
              omMetadataManager.getVersionedKeyTable().get(deletedVersionedKeyName);
          if (versionToDelete == null) {
            throw new OMException("Version " + requestedVersionId + " of key "
                + keyName + " not found", KEY_NOT_FOUND);
          }
        }
        versionToDelete = versionToDelete.toBuilder().setUpdateID(trxnLogIndex).build();

        OmKeyInfo promotedKeyInfo = null;
        String promotedVersionedKeyName = null;
        if (deletingCurrent) {
          omMetadataManager.getKeyTable(getBucketLayout()).addCacheEntry(
              new CacheKey<>(objectKey), CacheValue.get(trxnLogIndex));
          Pair<String, OmKeyInfo> promoted = getNewestNoncurrentVersion(
              omMetadataManager, volumeName, bucketName, keyName);
          if (promoted != null) {
            promotedVersionedKeyName = promoted.getKey();
            promotedKeyInfo = promoted.getValue();
            omMetadataManager.getKeyTable(getBucketLayout()).addCacheEntry(
                objectKey, promotedKeyInfo, trxnLogIndex);
            omMetadataManager.getVersionedKeyTable().addCacheEntry(
                new CacheKey<>(promotedVersionedKeyName), CacheValue.get(trxnLogIndex));
          }
        } else {
          omMetadataManager.getVersionedKeyTable().addCacheEntry(
              new CacheKey<>(deletedVersionedKeyName), CacheValue.get(trxnLogIndex));
        }

        long quotaReleased = sumBlockLengths(versionToDelete);
        boolean isVersionNonEmpty = !OmKeyInfo.isKeyEmpty(versionToDelete);
        omBucketInfo.decrUsedBytes(quotaReleased, isVersionNonEmpty);
        omBucketInfo.decrUsedNamespace(1L, isVersionNonEmpty);

        // versions of a key share the objectID, so the deletedTable key is
        // built with a transaction-unique id to avoid overwriting a pending
        // deletion of another version of the same key
        String deleteTableKeyName = omMetadataManager.getOzoneDeletePathKey(
            ozoneManager.getObjectIdFromTxId(trxnLogIndex), objectKey);
        visibleKeyRemoved = deletingCurrent && !versionToDelete.isDeleteMarker()
            && (promotedKeyInfo == null || promotedKeyInfo.isDeleteMarker());
        omClientResponse = new OMKeyVersionDeleteResponse(
            omResponse.setDeleteKeyResponse(DeleteKeyResponse.newBuilder()
                .setDeleteMarker(false)
                .setVersionId(requestedVersionId)).build(),
            versionToDelete, objectKey, deletedVersionedKeyName, deleteTableKeyName,
            promotedKeyInfo, promotedVersionedKeyName, omBucketInfo.copyObject());
      } else if (omBucketInfo.isS3VersioningEnabled()) {
        // S3-compatible versioning: a delete without versionId removes no
        // data; a delete marker becomes the current version and the previous
        // current version moves to the versionedKeyTable. Like S3, the marker
        // is inserted even when the key does not exist.
        long markerVersionId = ozoneManager.getObjectIdFromTxId(trxnLogIndex);
        OmKeyInfo.Builder markerBuilder;
        if (omKeyInfo != null) {
          markerBuilder = omKeyInfo.toBuilder()
              .setMetadata(new HashMap<>())
              .setTags(new HashMap<>())
              .setFileChecksum(null);
        } else {
          markerBuilder = new OmKeyInfo.Builder()
              .setVolumeName(volumeName)
              .setBucketName(bucketName)
              .setKeyName(keyName)
              .setReplicationConfig(RatisReplicationConfig.getInstance(ReplicationFactor.ONE))
              .setObjectID(markerVersionId)
              .setOwnerName(omBucketInfo.getOwner());
        }
        OmKeyInfo deleteMarker = markerBuilder
            .setOmKeyLocationInfos(Collections.singletonList(
                new OmKeyLocationInfoGroup(0, new ArrayList<>())))
            .setDataSize(0L)
            .setCreationTime(keyArgs.getModificationTime())
            .setModificationTime(keyArgs.getModificationTime())
            .setUpdateID(trxnLogIndex)
            .setVersionId(markerVersionId)
            .setDeleteMarker(true)
            .setNullVersion(false)
            .setFile(true)
            .build();

        String movedVersionedKeyName = null;
        OmKeyInfo movedVersionedKeyInfo = null;
        if (omKeyInfo != null) {
          movedVersionedKeyInfo = omKeyInfo.getVersionId() != null ? omKeyInfo
              : omKeyInfo.toBuilder()
                  .setVersionId(OmKeyInfo.NULL_VERSION_ID)
                  .setNullVersion(true)
                  .build();
          movedVersionedKeyName = omMetadataManager.getVersionedOzoneKey(
              volumeName, bucketName, keyName, movedVersionedKeyInfo.getVersionId());
          omMetadataManager.getVersionedKeyTable().addCacheEntry(
              movedVersionedKeyName, movedVersionedKeyInfo, trxnLogIndex);
        }

        checkBucketQuotaInNamespace(omBucketInfo, 1L);
        omBucketInfo.incrUsedNamespace(1L);

        omMetadataManager.getKeyTable(getBucketLayout()).addCacheEntry(
            objectKey, deleteMarker, trxnLogIndex);

        visibleKeyRemoved = omKeyInfo != null && !omKeyInfo.isDeleteMarker();
        omClientResponse = new OMKeyDeleteMarkerResponse(
            omResponse.setDeleteKeyResponse(DeleteKeyResponse.newBuilder()
                .setDeleteMarker(true)
                .setVersionId(markerVersionId)).build(),
            deleteMarker, objectKey, movedVersionedKeyName, movedVersionedKeyInfo,
            omBucketInfo.copyObject());
      } else {
        if (omKeyInfo == null) {
          throw new OMException("Key not found", KEY_NOT_FOUND);
        }

        validateIfMatchETag(keyArgs, omKeyInfo);

        // Set the UpdateID to current transactionLogIndex
        omKeyInfo = omKeyInfo.toBuilder()
            .setUpdateID(trxnLogIndex)
            .build();

        // Update table cache. Put a tombstone entry
        omMetadataManager.getKeyTable(getBucketLayout()).addCacheEntry(
            new CacheKey<>(objectKey), CacheValue.get(trxnLogIndex));

        long quotaReleased = sumBlockLengths(omKeyInfo);
        // Empty entries won't be added to deleted table so this key shouldn't get added to snapshotUsed space.
        boolean isKeyNonEmpty = !OmKeyInfo.isKeyEmpty(omKeyInfo);
        omBucketInfo.decrUsedBytes(quotaReleased, isKeyNonEmpty);
        omBucketInfo.decrUsedNamespace(1L, isKeyNonEmpty);
        OmKeyInfo deletedOpenKeyInfo = null;

        // If omKeyInfo has hsync metadata, delete its corresponding open key as well
        String dbOpenKey = null;
        String hsyncClientId = omKeyInfo.getMetadata().get(OzoneConsts.HSYNC_CLIENT_ID);
        if (hsyncClientId != null) {
          Table<String, OmKeyInfo> openKeyTable = omMetadataManager.getOpenKeyTable(getBucketLayout());
          dbOpenKey = omMetadataManager.getOpenKey(volumeName, bucketName, keyName, hsyncClientId);
          OmKeyInfo openKeyInfo = openKeyTable.get(dbOpenKey);
          if (openKeyInfo != null) {
            openKeyInfo = openKeyInfo.withMetadataMutations(
                metadata -> metadata.put(DELETED_HSYNC_KEY, "true"));
            openKeyTable.addCacheEntry(dbOpenKey, openKeyInfo, trxnLogIndex);
            deletedOpenKeyInfo = openKeyInfo;
          } else {
            LOG.warn("Potentially inconsistent DB state: open key not found with dbOpenKey '{}'", dbOpenKey);
          }
        }

        visibleKeyRemoved = true;
        omClientResponse = new OMKeyDeleteResponse(
            omResponse.setDeleteKeyResponse(DeleteKeyResponse.newBuilder())
                .build(), omKeyInfo,
            omBucketInfo.copyObject(), deletedOpenKeyInfo);
        if (omKeyInfo.isFile()) {
          auditMap.put(OzoneConsts.DATA_SIZE, String.valueOf(omKeyInfo.getDataSize()));
          auditMap.put(OzoneConsts.REPLICATION_CONFIG, omKeyInfo.getReplicationConfig().toString());
        }
      }

      result = Result.SUCCESS;
      long endNanosDeleteKeySuccessLatencyNs = Time.monotonicNowNanos();
      perfMetrics.setDeleteKeySuccessLatencyNs(endNanosDeleteKeySuccessLatencyNs - startNanos);
    } catch (IOException | InvalidPathException ex) {
      result = Result.FAILURE;
      exception = ex;
      omClientResponse =
          new OMKeyDeleteResponse(createErrorOMResponse(omResponse, exception),
              getBucketLayout());
      long endNanosDeleteKeyFailureLatencyNs = Time.monotonicNowNanos();
      perfMetrics.setDeleteKeyFailureLatencyNs(endNanosDeleteKeyFailureLatencyNs - startNanos);
    } finally {
      if (acquiredLock) {
        mergeOmLockDetails(omMetadataManager.getLock()
            .releaseWriteLock(BUCKET_LOCK, volumeName, bucketName));
      }
      if (omClientResponse != null) {
        omClientResponse.setOmLockDetails(getOmLockDetails());
      }
    }

    // Performing audit logging outside of the lock.
    markForAudit(auditLogger,
        buildAuditMessage(OMAction.DELETE_KEY, auditMap, exception, userInfo));

    switch (result) {
    case SUCCESS:
      if (visibleKeyRemoved) {
        omMetrics.decNumKeys();
      }
      LOG.debug("Key deleted. Volume:{}, Bucket:{}, Key:{}", volumeName,
          bucketName, keyName);
      break;
    case FAILURE:
      omMetrics.incNumKeyDeleteFails();
      LOG.error("Key delete failed. Volume:{}, Bucket:{}, Key:{}.", volumeName,
          bucketName, keyName, exception);
      break;
    default:
      LOG.error("Unrecognized Result for OMKeyDeleteRequest: {}",
          deleteKeyRequest);
    }

    return omClientResponse;
  }

  /**
   * Validates key delete requests.
   * We do not want to allow older clients to delete keys in buckets which use
   * non LEGACY layouts.
   *
   * @param req - the request to validate
   * @param ctx - the validation context
   * @return the validated request
   * @throws OMException if the request is invalid
   */
  @RequestFeatureValidator(
      conditions = ValidationCondition.OLDER_CLIENT_REQUESTS,
      processingPhase = RequestProcessingPhase.PRE_PROCESS,
      requestType = Type.DeleteKey
  )
  public static OMRequest blockDeleteKeyWithBucketLayoutFromOldClient(
      OMRequest req, ValidationContext ctx) throws IOException {
    if (req.getDeleteKeyRequest().hasKeyArgs()) {
      KeyArgs keyArgs = req.getDeleteKeyRequest().getKeyArgs();

      if (keyArgs.hasVolumeName() && keyArgs.hasBucketName()) {
        BucketLayout bucketLayout = ctx.getBucketLayout(
            keyArgs.getVolumeName(), keyArgs.getBucketName());
        bucketLayout.validateSupportedOperation();
      }
    }
    return req;
  }

  @RequestFeatureValidator(
      conditions = ValidationCondition.CLUSTER_NEEDS_FINALIZATION,
      processingPhase = RequestProcessingPhase.PRE_PROCESS,
      requestType = Type.DeleteKey
  )
  public static OMRequest disallowDeleteKeyWithVersionId(
      OMRequest req, ValidationContext ctx) throws OMException {
    if (!ctx.versionManager().isAllowed(OMLayoutFeature.OBJECT_VERSIONING)) {
      if (req.getDeleteKeyRequest().hasKeyArgs()
          && req.getDeleteKeyRequest().getKeyArgs().hasVersionId()) {
        throw new OMException("Cluster does not have the object versioning"
            + " feature finalized yet, but the request contains a versionId."
            + " Rejecting the request, please finalize the cluster upgrade and"
            + " then try again.",
            OMException.ResultCodes.NOT_SUPPORTED_OPERATION_PRIOR_FINALIZATION);
      }
    }
    return req;
  }
}
