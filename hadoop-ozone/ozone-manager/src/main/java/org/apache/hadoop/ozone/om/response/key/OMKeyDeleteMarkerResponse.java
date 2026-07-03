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

package org.apache.hadoop.ozone.om.response.key;

import static org.apache.hadoop.ozone.om.codec.OMDBDefinition.BUCKET_TABLE;
import static org.apache.hadoop.ozone.om.codec.OMDBDefinition.KEY_TABLE;
import static org.apache.hadoop.ozone.om.codec.OMDBDefinition.VERSIONED_KEY_TABLE;

import jakarta.annotation.Nonnull;
import java.io.IOException;
import org.apache.hadoop.hdds.utils.db.BatchOperation;
import org.apache.hadoop.ozone.om.OMMetadataManager;
import org.apache.hadoop.ozone.om.helpers.OmBucketInfo;
import org.apache.hadoop.ozone.om.helpers.OmKeyInfo;
import org.apache.hadoop.ozone.om.response.CleanupTableInfo;
import org.apache.hadoop.ozone.protocol.proto.OzoneManagerProtocolProtos.OMResponse;

/**
 * Response for a DeleteKey request on a bucket with S3-compatible versioning
 * enabled: no data is removed; a delete marker becomes the current version in
 * the keyTable and the previous current version (if any) moves to the
 * versionedKeyTable.
 */
@CleanupTableInfo(cleanupTables = {KEY_TABLE, VERSIONED_KEY_TABLE, BUCKET_TABLE})
public class OMKeyDeleteMarkerResponse extends OmKeyResponse {

  private final OmKeyInfo deleteMarker;
  private final String ozoneKeyName;
  // previous current version moved to the versionedKeyTable; null when the
  // key did not exist (the marker is still inserted, matching S3)
  private final String movedVersionedKeyName;
  private final OmKeyInfo movedVersionedKeyInfo;
  private final OmBucketInfo omBucketInfo;

  public OMKeyDeleteMarkerResponse(@Nonnull OMResponse omResponse,
      @Nonnull OmKeyInfo deleteMarker, @Nonnull String ozoneKeyName,
      String movedVersionedKeyName, OmKeyInfo movedVersionedKeyInfo,
      @Nonnull OmBucketInfo omBucketInfo) {
    super(omResponse, omBucketInfo.getBucketLayout());
    this.deleteMarker = deleteMarker;
    this.ozoneKeyName = ozoneKeyName;
    this.movedVersionedKeyName = movedVersionedKeyName;
    this.movedVersionedKeyInfo = movedVersionedKeyInfo;
    this.omBucketInfo = omBucketInfo;
  }

  @Override
  public void addToDBBatch(OMMetadataManager omMetadataManager,
      BatchOperation batchOperation) throws IOException {
    omMetadataManager.getKeyTable(getBucketLayout())
        .putWithBatch(batchOperation, ozoneKeyName, deleteMarker);

    if (movedVersionedKeyInfo != null) {
      omMetadataManager.getVersionedKeyTable().putWithBatch(batchOperation,
          movedVersionedKeyName, movedVersionedKeyInfo);
    }

    omMetadataManager.getBucketTable().putWithBatch(batchOperation,
        omMetadataManager.getBucketKey(omBucketInfo.getVolumeName(),
            omBucketInfo.getBucketName()), omBucketInfo);
  }
}
