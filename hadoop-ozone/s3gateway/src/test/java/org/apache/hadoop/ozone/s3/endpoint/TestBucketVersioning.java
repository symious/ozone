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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.hadoop.ozone.s3.endpoint.EndpointTestUtils.assertErrorResponse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.ws.rs.core.Response;
import org.apache.hadoop.ozone.client.OzoneClient;
import org.apache.hadoop.ozone.client.OzoneClientStub;
import org.apache.hadoop.ozone.om.helpers.BucketVersioningStatus;
import org.apache.hadoop.ozone.s3.exception.S3ErrorTable;
import org.apache.hadoop.ozone.s3.util.S3Consts;
import org.apache.hadoop.ozone.s3.util.S3Consts.QueryParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for GetBucketVersioning / PutBucketVersioning
 * ({@code /bucket?versioning}).
 */
public class TestBucketVersioning {

  private static final String BUCKET_NAME = "versioned-bucket";

  private OzoneClient clientStub;
  private BucketEndpoint bucketEndpoint;

  @BeforeEach
  public void setup() throws IOException {
    clientStub = new OzoneClientStub();
    clientStub.getObjectStore().createS3Bucket(BUCKET_NAME);

    bucketEndpoint = EndpointBuilder.newBucketEndpointBuilder()
        .setClient(clientStub)
        .build();
    bucketEndpoint.queryParamsForTest().set(QueryParams.VERSIONING, "");
  }

  private InputStream body(String status) {
    return new ByteArrayInputStream(
        ("<VersioningConfiguration xmlns=\"" + S3Consts.S3_XML_NAMESPACE + "\">"
            + status + "</VersioningConfiguration>").getBytes(UTF_8));
  }

  @Test
  public void getOnUnversionedBucketReturnsEmptyConfiguration()
      throws Exception {
    Response response = bucketEndpoint.get(BUCKET_NAME);

    assertEquals(200, response.getStatus());
    S3VersioningConfiguration configuration =
        (S3VersioningConfiguration) response.getEntity();
    assertNull(configuration.getStatus());
  }

  @Test
  public void putEnabledThenGetReturnsEnabled() throws Exception {
    Response putResponse = bucketEndpoint.put(BUCKET_NAME,
        body("<Status>Enabled</Status>"));
    assertEquals(200, putResponse.getStatus());
    assertEquals(BucketVersioningStatus.ENABLED,
        clientStub.getObjectStore().getS3Bucket(BUCKET_NAME)
            .getVersioningStatus());

    Response getResponse = bucketEndpoint.get(BUCKET_NAME);
    assertEquals(S3VersioningConfiguration.ENABLED,
        ((S3VersioningConfiguration) getResponse.getEntity()).getStatus());
  }

  @Test
  public void putSuspendedThenGetReturnsSuspended() throws Exception {
    bucketEndpoint.put(BUCKET_NAME, body("<Status>Enabled</Status>"));
    Response putResponse = bucketEndpoint.put(BUCKET_NAME,
        body("<Status>Suspended</Status>"));
    assertEquals(200, putResponse.getStatus());

    Response getResponse = bucketEndpoint.get(BUCKET_NAME);
    assertEquals(S3VersioningConfiguration.SUSPENDED,
        ((S3VersioningConfiguration) getResponse.getEntity()).getStatus());
  }

  @Test
  public void putMfaDeleteIsNotImplemented() {
    assertErrorResponse(S3ErrorTable.NOT_IMPLEMENTED,
        () -> bucketEndpoint.put(BUCKET_NAME,
            body("<Status>Enabled</Status><MfaDelete>Enabled</MfaDelete>")));
  }

  @Test
  public void putInvalidStatusIsRejected() {
    assertErrorResponse(S3ErrorTable.INVALID_ARGUMENT,
        () -> bucketEndpoint.put(BUCKET_NAME, body("<Status>Maybe</Status>")));
  }

  @Test
  public void listObjectVersionsReturnsEmptyResultOnStub() throws Exception {
    bucketEndpoint.queryParamsForTest().unset(QueryParams.VERSIONING);
    bucketEndpoint.queryParamsForTest().set(QueryParams.VERSIONS, "");

    Response response = bucketEndpoint.get(BUCKET_NAME);

    assertEquals(200, response.getStatus());
    ListVersionsResponse listing = (ListVersionsResponse) response.getEntity();
    assertEquals(BUCKET_NAME, listing.getName());
    assertEquals(0, listing.getVersions().size());
    assertEquals(0, listing.getDeleteMarkers().size());
  }
}
