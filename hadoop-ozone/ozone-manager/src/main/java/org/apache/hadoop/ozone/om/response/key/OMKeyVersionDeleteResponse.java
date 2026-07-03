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
import static org.apache.hadoop.ozone.om.codec.OMDBDefinition.DELETED_TABLE;
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
 * Response for a DeleteKey request addressing a specific versionId: the
 * version is permanently removed (its blocks go to the deletedTable). When the
 * removed version was the current one, the newest noncurrent version (if any)
 * is promoted from the versionedKeyTable back into the keyTable - this is the
 * "delete the delete marker to restore the object" path.
 */
@CleanupTableInfo(cleanupTables = {KEY_TABLE, VERSIONED_KEY_TABLE,
    DELETED_TABLE, BUCKET_TABLE})
public class OMKeyVersionDeleteResponse extends AbstractOMKeyDeleteResponse {

  private final OmKeyInfo deletedVersion;
  private final String ozoneKeyName;
  // versionedKeyTable entry of the deleted version; null when the current
  // version (keyTable entry) was deleted
  private final String deletedVersionedKeyName;
  // deletedTable key, pre-built with a transaction-unique id since versions
  // of a key share the objectID
  private final String deleteTableKeyName;
  private final OmKeyInfo promotedKeyInfo;
  private final String promotedVersionedKeyName;
  private final OmBucketInfo omBucketInfo;

  @SuppressWarnings("checkstyle:ParameterNumber")
  public OMKeyVersionDeleteResponse(@Nonnull OMResponse omResponse,
      @Nonnull OmKeyInfo deletedVersion, @Nonnull String ozoneKeyName,
      String deletedVersionedKeyName, @Nonnull String deleteTableKeyName,
      OmKeyInfo promotedKeyInfo, String promotedVersionedKeyName,
      @Nonnull OmBucketInfo omBucketInfo) {
    super(omResponse, omBucketInfo.getBucketLayout());
    this.deletedVersion = deletedVersion;
    this.ozoneKeyName = ozoneKeyName;
    this.deletedVersionedKeyName = deletedVersionedKeyName;
    this.deleteTableKeyName = deleteTableKeyName;
    this.promotedKeyInfo = promotedKeyInfo;
    this.promotedVersionedKeyName = promotedVersionedKeyName;
    this.omBucketInfo = omBucketInfo;
  }

  @Override
  public void addToDBBatch(OMMetadataManager omMetadataManager,
      BatchOperation batchOperation) throws IOException {
    if (deletedVersionedKeyName == null) {
      // the current version was deleted
      addDeletionToBatch(omMetadataManager, batchOperation,
          omMetadataManager.getKeyTable(getBucketLayout()), ozoneKeyName,
          deleteTableKeyName, deletedVersion, omBucketInfo.getObjectID(), true);
      if (promotedKeyInfo != null) {
        // batch operations apply in order: the delete above is superseded
        omMetadataManager.getKeyTable(getBucketLayout())
            .putWithBatch(batchOperation, ozoneKeyName, promotedKeyInfo);
        omMetadataManager.getVersionedKeyTable()
            .deleteWithBatch(batchOperation, promotedVersionedKeyName);
      }
    } else {
      addDeletionToBatch(omMetadataManager, batchOperation,
          omMetadataManager.getVersionedKeyTable(), deletedVersionedKeyName,
          deleteTableKeyName, deletedVersion, omBucketInfo.getObjectID(), true);
    }

    omMetadataManager.getBucketTable().putWithBatch(batchOperation,
        omMetadataManager.getBucketKey(omBucketInfo.getVolumeName(),
            omBucketInfo.getBucketName()), omBucketInfo);
  }
}
