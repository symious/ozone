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

import static org.apache.hadoop.ozone.s3.exception.S3ErrorTable.INVALID_ARGUMENT;
import static org.apache.hadoop.ozone.s3.exception.S3ErrorTable.NOT_IMPLEMENTED;
import static org.apache.hadoop.ozone.s3.exception.S3ErrorTable.newError;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Supplier;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.ozone.audit.S3GAction;
import org.apache.hadoop.ozone.client.OzoneBucket;
import org.apache.hadoop.ozone.om.exceptions.OMException;
import org.apache.hadoop.ozone.om.helpers.BucketVersioningStatus;
import org.apache.hadoop.ozone.s3.exception.OS3Exception;
import org.apache.hadoop.ozone.s3.util.S3Consts.QueryParams;
import org.apache.ratis.util.MemoizedSupplier;

/**
 * Handles the {@code ?versioning} query parameter: GetBucketVersioning and
 * PutBucketVersioning (S3-compatible object versioning).
 */
public class BucketVersioningHandler extends BucketOperationHandler {

  private static final Supplier<MessageUnmarshaller<S3VersioningConfiguration>> UNMARSHALLER =
      MemoizedSupplier.valueOf(() -> new MessageUnmarshaller<>(S3VersioningConfiguration.class));

  private boolean shouldHandle() {
    return queryParams().get(QueryParams.VERSIONING) != null;
  }

  /**
   * See https://docs.aws.amazon.com/AmazonS3/latest/API/API_GetBucketVersioning.html.
   */
  @Override
  Response handleGetRequest(S3RequestContext context, String bucketName)
      throws IOException, OS3Exception {
    if (!shouldHandle()) {
      return null;
    }

    context.setAction(S3GAction.GET_BUCKET_VERSIONING);

    OzoneBucket bucket = context.getVolume().getBucket(bucketName);
    S3Owner.verifyBucketOwnerCondition(getHeaders(), bucketName, bucket.getOwner());

    S3VersioningConfiguration configuration = new S3VersioningConfiguration();
    BucketVersioningStatus status = bucket.getVersioningStatus();
    if (status == BucketVersioningStatus.ENABLED) {
      configuration.setStatus(S3VersioningConfiguration.ENABLED);
    } else if (status == BucketVersioningStatus.SUSPENDED) {
      configuration.setStatus(S3VersioningConfiguration.SUSPENDED);
    }
    // an unversioned bucket returns the configuration without a Status
    return Response.ok(configuration, MediaType.APPLICATION_XML_TYPE).build();
  }

  /**
   * See https://docs.aws.amazon.com/AmazonS3/latest/API/API_PutBucketVersioning.html.
   */
  @Override
  Response handlePutRequest(S3RequestContext context, String bucketName,
      InputStream body) throws IOException, OS3Exception {
    if (!shouldHandle()) {
      return null;
    }

    context.setAction(S3GAction.PUT_BUCKET_VERSIONING);

    S3VersioningConfiguration configuration = UNMARSHALLER.get().readFrom(body);
    if (StringUtils.isNotEmpty(configuration.getMfaDelete())) {
      throw newError(NOT_IMPLEMENTED, "MfaDelete");
    }
    BucketVersioningStatus status;
    if (S3VersioningConfiguration.ENABLED.equals(configuration.getStatus())) {
      status = BucketVersioningStatus.ENABLED;
    } else if (S3VersioningConfiguration.SUSPENDED.equals(configuration.getStatus())) {
      status = BucketVersioningStatus.SUSPENDED;
    } else {
      throw newError(INVALID_ARGUMENT, "Status: " + configuration.getStatus());
    }

    OzoneBucket bucket = context.getVolume().getBucket(bucketName);
    S3Owner.verifyBucketOwnerCondition(getHeaders(), bucketName, bucket.getOwner());

    try {
      bucket.setVersioningStatus(status);
    } catch (OMException ex) {
      if (ex.getResult() == OMException.ResultCodes.NOT_SUPPORTED_OPERATION) {
        // e.g. bucket layout without versioning support
        throw newError(NOT_IMPLEMENTED, "PutBucketVersioning on bucket " + bucketName, ex);
      }
      throw ex;
    }
    return Response.ok().build();
  }
}
