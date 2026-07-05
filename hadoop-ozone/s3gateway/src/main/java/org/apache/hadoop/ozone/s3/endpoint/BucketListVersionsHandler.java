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

package org.apache.hadoop.ozone.s3.endpoint;

import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.hadoop.ozone.OzoneConsts;
import org.apache.hadoop.ozone.audit.S3GAction;
import org.apache.hadoop.ozone.client.OzoneBucket;
import org.apache.hadoop.ozone.om.helpers.ListObjectVersionsResult;
import org.apache.hadoop.ozone.om.helpers.OmKeyInfo;
import org.apache.hadoop.ozone.s3.exception.OS3Exception;
import org.apache.hadoop.ozone.s3.util.S3Consts.QueryParams;
import org.apache.hadoop.ozone.s3.util.S3StorageType;
import org.apache.hadoop.ozone.s3.util.S3VersionIds;

/**
 * Handles ListObjectVersions ({@code GET /bucket?versions}), S3-compatible
 * object versioning.
 */
public class BucketListVersionsHandler extends BucketOperationHandler {

  private static final int DEFAULT_MAX_KEYS = 1000;

  /**
   * See https://docs.aws.amazon.com/AmazonS3/latest/API/API_ListObjectVersions.html.
   */
  @Override
  Response handleGetRequest(S3RequestContext context, String bucketName)
      throws IOException, OS3Exception {
    if (queryParams().get(QueryParams.VERSIONS) == null) {
      return null;
    }

    context.setAction(S3GAction.LIST_OBJECT_VERSIONS);

    final String prefix = queryParams().get(QueryParams.PREFIX, "");
    final String keyMarker = queryParams().get(QueryParams.KEY_MARKER);
    final String versionIdMarkerParam =
        queryParams().get(QueryParams.VERSION_ID_MARKER);
    final Long versionIdMarker = S3VersionIds.decode(versionIdMarkerParam);
    final int maxKeys = Math.min(
        queryParams().getInt(QueryParams.MAX_KEYS, DEFAULT_MAX_KEYS),
        DEFAULT_MAX_KEYS);

    OzoneBucket bucket = context.getVolume().getBucket(bucketName);
    S3Owner.verifyBucketOwnerCondition(getHeaders(), bucketName, bucket.getOwner());

    ListObjectVersionsResult result = getClientProtocol().listObjectVersions(
        context.getVolume().getName(), bucketName, prefix, keyMarker,
        versionIdMarker, maxKeys);

    ListVersionsResponse response = new ListVersionsResponse();
    response.setName(bucketName);
    response.setPrefix(prefix);
    response.setKeyMarker(keyMarker == null ? "" : keyMarker);
    response.setVersionIdMarker(
        versionIdMarkerParam == null ? "" : versionIdMarkerParam);
    response.setMaxKeys(maxKeys);
    response.setTruncated(result.isTruncated());
    if (result.isTruncated()) {
      response.setNextKeyMarker(result.getNextKeyMarker());
      response.setNextVersionIdMarker(
          S3VersionIds.encode(result.getNextVersionIdMarker()));
    }

    String previousKey = null;
    for (OmKeyInfo version : result.getVersions()) {
      String keyName = version.getKeyName();
      // the first entry of a key is its latest version, unless this page
      // resumed inside keyMarker after versionIdMarker
      boolean latest = !keyName.equals(previousKey)
          && !(keyName.equals(keyMarker) && versionIdMarker != null);
      previousKey = keyName;

      ListVersionsResponse.DeleteMarkerMetadata entry;
      if (version.isDeleteMarker()) {
        entry = new ListVersionsResponse.DeleteMarkerMetadata();
        response.addDeleteMarker(entry);
      } else {
        ListVersionsResponse.VersionMetadata versionEntry =
            new ListVersionsResponse.VersionMetadata();
        versionEntry.setETag(version.getMetadata().get(OzoneConsts.ETAG));
        versionEntry.setSize(version.getDataSize());
        versionEntry.setStorageClass(version.getReplicationConfig() == null
            ? S3StorageType.STANDARD.toString()
            : S3StorageType.fromReplicationConfig(
                version.getReplicationConfig()).toString());
        response.addVersion(versionEntry);
        entry = versionEntry;
      }
      entry.setKey(keyName);
      entry.setVersionId(S3VersionIds.encode(
          version.isNullVersionRecord() ? null : version.getVersionId()));
      entry.setLatest(latest);
      entry.setLastModified(Instant.ofEpochMilli(version.getModificationTime()));
      if (Objects.nonNull(version.getOwnerName())) {
        entry.setOwner(S3Owner.of(version.getOwnerName()));
      }
    }

    return Response.ok(response, MediaType.APPLICATION_XML_TYPE).build();
  }
}
