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

import org.apache.hadoop.ozone.om.helpers.OmKeyInfo;
import org.apache.hadoop.ozone.s3.exception.OS3Exception;
import org.apache.hadoop.ozone.s3.exception.S3ErrorTable;

/**
 * Codec between the external S3 versionId (opaque string; the literal
 * {@code "null"} addresses the null version) and the internal numeric
 * version identity (fixed-width hex; the reserved value 0 is the null
 * version).
 */
public final class S3VersionIds {

  public static final String NULL_VERSION = "null";

  private S3VersionIds() {
    // no instances
  }

  /**
   * @param versionId external versionId query parameter, may be null
   * @return internal version identity, or null when the parameter is absent
   * @throws OS3Exception when the parameter is malformed
   */
  public static Long decode(String versionId) throws OS3Exception {
    if (versionId == null || versionId.isEmpty()) {
      return null;
    }
    if (NULL_VERSION.equals(versionId)) {
      return OmKeyInfo.NULL_VERSION_ID;
    }
    if (versionId.length() == 16) {
      try {
        return Long.parseUnsignedLong(versionId, 16);
      } catch (NumberFormatException ignored) {
        // fall through to the error below
      }
    }
    throw S3ErrorTable.newError(S3ErrorTable.INVALID_ARGUMENT,
        "Invalid version id specified: " + versionId);
  }

  /**
   * @param versionId internal version identity; null and the reserved value 0
   * both denote the null version
   */
  public static String encode(Long versionId) {
    if (versionId == null || versionId == OmKeyInfo.NULL_VERSION_ID) {
      return NULL_VERSION;
    }
    return String.format("%016x", versionId);
  }
}
