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

package org.apache.hadoop.ozone.s3.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.hadoop.ozone.om.helpers.OmKeyInfo;
import org.apache.hadoop.ozone.s3.exception.OS3Exception;
import org.junit.jupiter.api.Test;

/** Tests the external/internal versionId codec. */
public class TestS3VersionIds {

  @Test
  public void roundTrip() throws Exception {
    long versionId = 0x123456789abcdefL;
    String encoded = S3VersionIds.encode(versionId);
    assertEquals(16, encoded.length());
    assertEquals(versionId, S3VersionIds.decode(encoded));
  }

  @Test
  public void nullVersionMapping() throws Exception {
    assertEquals(S3VersionIds.NULL_VERSION, S3VersionIds.encode(null));
    assertEquals(S3VersionIds.NULL_VERSION,
        S3VersionIds.encode(OmKeyInfo.NULL_VERSION_ID));
    assertEquals(OmKeyInfo.NULL_VERSION_ID,
        S3VersionIds.decode(S3VersionIds.NULL_VERSION));
  }

  @Test
  public void absentParameterDecodesToNull() throws Exception {
    assertNull(S3VersionIds.decode(null));
    assertNull(S3VersionIds.decode(""));
  }

  @Test
  public void malformedParameterIsRejected() {
    assertThrows(OS3Exception.class, () -> S3VersionIds.decode("not-a-version"));
    assertThrows(OS3Exception.class, () -> S3VersionIds.decode("123"));
    assertThrows(OS3Exception.class,
        () -> S3VersionIds.decode("zzzzzzzzzzzzzzzz"));
  }
}
