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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.apache.hadoop.ozone.om.helpers.BucketLayout;
import org.apache.hadoop.ozone.om.helpers.BucketVersioningStatus;
import org.apache.hadoop.ozone.om.helpers.OmBucketInfo;
import org.apache.hadoop.ozone.om.helpers.OmKeyInfo;
import org.apache.hadoop.ozone.om.request.OMRequestTestUtils;
import org.apache.hadoop.ozone.om.response.OMClientResponse;
import org.apache.hadoop.ozone.protocol.proto.OzoneManagerProtocolProtos;
import org.apache.hadoop.ozone.protocol.proto.OzoneManagerProtocolProtos.CommitKeyRequest;
import org.apache.hadoop.ozone.protocol.proto.OzoneManagerProtocolProtos.DeleteKeyRequest;
import org.apache.hadoop.ozone.protocol.proto.OzoneManagerProtocolProtos.KeyArgs;
import org.apache.hadoop.ozone.protocol.proto.OzoneManagerProtocolProtos.OMRequest;
import org.apache.hadoop.util.Time;
import org.junit.jupiter.api.Test;

/**
 * Tests the S3-compatible versioning behavior of key commit and delete
 * requests on a versioning-enabled OBJECT_STORE bucket: versioned overwrites,
 * delete markers, permanent deletes by versionId and version promotion.
 */
public class TestOMKeyVersioningRequests extends TestOMKeyRequest {

  @Override
  public BucketLayout getBucketLayout() {
    return BucketLayout.OBJECT_STORE;
  }

  private void setupVersionedBucket() throws Exception {
    OMRequestTestUtils.addVolumeToDB(volumeName, omMetadataManager);
    OMRequestTestUtils.addBucketToDB(omMetadataManager,
        OmBucketInfo.newBuilder()
            .setVolumeName(volumeName)
            .setBucketName(bucketName)
            .setBucketLayout(BucketLayout.OBJECT_STORE)
            .setVersioningStatus(BucketVersioningStatus.ENABLED));
    // versionId assignment derives from the transaction index; the mocked
    // OzoneManager would otherwise return 0, which is the reserved
    // NULL_VERSION_ID
    when(ozoneManager.getObjectIdFromTxId(anyLong()))
        .thenAnswer(inv -> inv.getArgument(0));
  }

  private String seedCurrentVersion(Long versionId, boolean deleteMarker)
      throws Exception {
    OmKeyInfo keyInfo = OMRequestTestUtils.createOmKeyInfo(
            volumeName, bucketName, keyName, replicationConfig)
        .setVersionId(versionId)
        .setDeleteMarker(deleteMarker)
        .build();
    String ozoneKey = omMetadataManager.getOzoneKey(
        volumeName, bucketName, keyName);
    omMetadataManager.getKeyTable(getBucketLayout()).put(ozoneKey, keyInfo);
    return ozoneKey;
  }

  private String seedNoncurrentVersion(long versionId) throws Exception {
    OmKeyInfo keyInfo = OMRequestTestUtils.createOmKeyInfo(
            volumeName, bucketName, keyName, replicationConfig)
        .setVersionId(versionId)
        .build();
    String dbKey = omMetadataManager.getVersionedOzoneKey(
        volumeName, bucketName, keyName, versionId);
    omMetadataManager.getVersionedKeyTable().put(dbKey, keyInfo);
    return dbKey;
  }

  private OMRequest deleteRequest(Long versionId) {
    KeyArgs.Builder keyArgs = KeyArgs.newBuilder()
        .setVolumeName(volumeName)
        .setBucketName(bucketName)
        .setKeyName(keyName)
        .setModificationTime(Time.now());
    if (versionId != null) {
      keyArgs.setVersionId(versionId);
    }
    return OMRequest.newBuilder()
        .setDeleteKeyRequest(DeleteKeyRequest.newBuilder().setKeyArgs(keyArgs))
        .setCmdType(OzoneManagerProtocolProtos.Type.DeleteKey)
        .setClientId(UUID.randomUUID().toString()).build();
  }

  private OMRequest commitRequest() {
    KeyArgs keyArgs = KeyArgs.newBuilder()
        .setVolumeName(volumeName)
        .setBucketName(bucketName)
        .setKeyName(keyName)
        .setModificationTime(Time.now())
        .setDataSize(0)
        .build();
    return OMRequest.newBuilder()
        .setCommitKeyRequest(CommitKeyRequest.newBuilder()
            .setKeyArgs(keyArgs)
            .setClientID(clientID))
        .setCmdType(OzoneManagerProtocolProtos.Type.CommitKey)
        .setClientId(UUID.randomUUID().toString()).build();
  }

  @Test
  public void testVersionedCommitAssignsVersionIdAndKeepsPreviousVersion()
      throws Exception {
    setupVersionedBucket();
    String ozoneKey = seedCurrentVersion(100L, false);
    OMRequestTestUtils.addKeyToTable(true, volumeName, bucketName, keyName,
        clientID, replicationConfig, omMetadataManager);

    OMClientResponse response = new OMKeyCommitRequest(commitRequest(),
        getBucketLayout()).validateAndUpdateCache(ozoneManager, 500L);
    assertEquals(OzoneManagerProtocolProtos.Status.OK,
        response.getOMResponse().getStatus());

    OmKeyInfo current =
        omMetadataManager.getKeyTable(getBucketLayout()).get(ozoneKey);
    assertNotNull(current);
    assertEquals(500L, current.getVersionId());
    assertFalse(current.isDeleteMarker());

    OmKeyInfo noncurrent = omMetadataManager.getVersionedKeyTable().get(
        omMetadataManager.getVersionedOzoneKey(
            volumeName, bucketName, keyName, 100L));
    assertNotNull(noncurrent);
    assertEquals(100L, noncurrent.getVersionId());
  }

  @Test
  public void testVersionedCommitOfNewKeyHasNoNoncurrentVersion()
      throws Exception {
    setupVersionedBucket();
    OMRequestTestUtils.addKeyToTable(true, volumeName, bucketName, keyName,
        clientID, replicationConfig, omMetadataManager);

    OMClientResponse response = new OMKeyCommitRequest(commitRequest(),
        getBucketLayout()).validateAndUpdateCache(ozoneManager, 500L);
    assertEquals(OzoneManagerProtocolProtos.Status.OK,
        response.getOMResponse().getStatus());

    OmKeyInfo current = omMetadataManager.getKeyTable(getBucketLayout()).get(
        omMetadataManager.getOzoneKey(volumeName, bucketName, keyName));
    assertEquals(500L, current.getVersionId());
  }

  @Test
  public void testDeleteInsertsMarkerAndKeepsPreviousVersion()
      throws Exception {
    setupVersionedBucket();
    String ozoneKey = seedCurrentVersion(100L, false);

    OMClientResponse response = new OMKeyDeleteRequest(deleteRequest(null),
        getBucketLayout()).validateAndUpdateCache(ozoneManager, 200L);
    assertEquals(OzoneManagerProtocolProtos.Status.OK,
        response.getOMResponse().getStatus());
    assertTrue(response.getOMResponse().getDeleteKeyResponse().getDeleteMarker());
    assertEquals(200L,
        response.getOMResponse().getDeleteKeyResponse().getVersionId());

    OmKeyInfo current =
        omMetadataManager.getKeyTable(getBucketLayout()).get(ozoneKey);
    assertNotNull(current);
    assertTrue(current.isDeleteMarker());
    assertEquals(200L, current.getVersionId());

    OmKeyInfo noncurrent = omMetadataManager.getVersionedKeyTable().get(
        omMetadataManager.getVersionedOzoneKey(
            volumeName, bucketName, keyName, 100L));
    assertNotNull(noncurrent);
    assertEquals(100L, noncurrent.getVersionId());
  }

  @Test
  public void testDeleteOfMissingKeyStillInsertsMarker() throws Exception {
    setupVersionedBucket();

    OMClientResponse response = new OMKeyDeleteRequest(deleteRequest(null),
        getBucketLayout()).validateAndUpdateCache(ozoneManager, 200L);
    assertEquals(OzoneManagerProtocolProtos.Status.OK,
        response.getOMResponse().getStatus());

    OmKeyInfo current = omMetadataManager.getKeyTable(getBucketLayout()).get(
        omMetadataManager.getOzoneKey(volumeName, bucketName, keyName));
    assertNotNull(current);
    assertTrue(current.isDeleteMarker());
  }

  @Test
  public void testDeleteOfCurrentVersionPromotesPreviousVersion()
      throws Exception {
    setupVersionedBucket();
    // current version is a delete marker; deleting it by versionId is the
    // S3 "restore the object" path
    String ozoneKey = seedCurrentVersion(200L, true);
    String noncurrentDbKey = seedNoncurrentVersion(100L);

    OMClientResponse response = new OMKeyDeleteRequest(deleteRequest(200L),
        getBucketLayout()).validateAndUpdateCache(ozoneManager, 300L);
    assertEquals(OzoneManagerProtocolProtos.Status.OK,
        response.getOMResponse().getStatus());
    assertFalse(response.getOMResponse().getDeleteKeyResponse().getDeleteMarker());

    OmKeyInfo current =
        omMetadataManager.getKeyTable(getBucketLayout()).get(ozoneKey);
    assertNotNull(current);
    assertEquals(100L, current.getVersionId());
    assertFalse(current.isDeleteMarker());

    assertNull(omMetadataManager.getVersionedKeyTable().get(noncurrentDbKey));
  }

  @Test
  public void testDeleteOfLastVersionRemovesKey() throws Exception {
    setupVersionedBucket();
    String ozoneKey = seedCurrentVersion(100L, false);

    OMClientResponse response = new OMKeyDeleteRequest(deleteRequest(100L),
        getBucketLayout()).validateAndUpdateCache(ozoneManager, 300L);
    assertEquals(OzoneManagerProtocolProtos.Status.OK,
        response.getOMResponse().getStatus());

    assertNull(omMetadataManager.getKeyTable(getBucketLayout()).get(ozoneKey));
  }

  @Test
  public void testDeleteOfNoncurrentVersionKeepsCurrent() throws Exception {
    setupVersionedBucket();
    String ozoneKey = seedCurrentVersion(200L, false);
    String noncurrentDbKey = seedNoncurrentVersion(100L);

    OMClientResponse response = new OMKeyDeleteRequest(deleteRequest(100L),
        getBucketLayout()).validateAndUpdateCache(ozoneManager, 300L);
    assertEquals(OzoneManagerProtocolProtos.Status.OK,
        response.getOMResponse().getStatus());

    OmKeyInfo current =
        omMetadataManager.getKeyTable(getBucketLayout()).get(ozoneKey);
    assertEquals(200L, current.getVersionId());
    assertNull(omMetadataManager.getVersionedKeyTable().get(noncurrentDbKey));
  }

  @Test
  public void testDeleteOfUnknownVersionFails() throws Exception {
    setupVersionedBucket();
    seedCurrentVersion(200L, false);

    OMClientResponse response = new OMKeyDeleteRequest(deleteRequest(150L),
        getBucketLayout()).validateAndUpdateCache(ozoneManager, 300L);
    assertEquals(OzoneManagerProtocolProtos.Status.KEY_NOT_FOUND,
        response.getOMResponse().getStatus());
  }

  @Test
  public void testDeleteByVersionIdRejectedOnUnversionedBucket()
      throws Exception {
    OMRequestTestUtils.addVolumeToDB(volumeName, omMetadataManager);
    OMRequestTestUtils.addBucketToDB(omMetadataManager,
        OmBucketInfo.newBuilder()
            .setVolumeName(volumeName)
            .setBucketName(bucketName)
            .setBucketLayout(BucketLayout.OBJECT_STORE));
    seedCurrentVersion(null, false);

    OMClientResponse response = new OMKeyDeleteRequest(deleteRequest(100L),
        getBucketLayout()).validateAndUpdateCache(ozoneManager, 300L);
    assertEquals(OzoneManagerProtocolProtos.Status.INVALID_REQUEST,
        response.getOMResponse().getStatus());
  }

  @Test
  public void testDeleteMovesLegacyCurrentToNullVersionSlot() throws Exception {
    setupVersionedBucket();
    // record that predates versioning: no versionId, becomes the null version
    seedCurrentVersion(null, false);

    OMClientResponse response = new OMKeyDeleteRequest(deleteRequest(null),
        getBucketLayout()).validateAndUpdateCache(ozoneManager, 200L);
    assertEquals(OzoneManagerProtocolProtos.Status.OK,
        response.getOMResponse().getStatus());

    OmKeyInfo nullVersion = omMetadataManager.getVersionedKeyTable().get(
        omMetadataManager.getVersionedOzoneKey(
            volumeName, bucketName, keyName, OmKeyInfo.NULL_VERSION_ID));
    assertNotNull(nullVersion);
    assertTrue(nullVersion.isNullVersion());
    assertEquals(OmKeyInfo.NULL_VERSION_ID, nullVersion.getVersionId());
  }
}
