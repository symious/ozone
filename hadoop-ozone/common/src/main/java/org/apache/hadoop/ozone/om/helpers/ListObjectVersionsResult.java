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

package org.apache.hadoop.ozone.om.helpers;

import java.util.Collections;
import java.util.List;

/**
 * Result of an S3-compatible ListObjectVersions call: all versions of keys
 * (current and noncurrent, including delete markers) ordered by key name and,
 * within a key, newest first. Each entry carries its versionId /
 * isDeleteMarker / isNullVersion fields.
 */
public final class ListObjectVersionsResult {

  private final List<OmKeyInfo> versions;
  private final boolean truncated;
  private final String nextKeyMarker;
  private final long nextVersionIdMarker;

  public ListObjectVersionsResult(List<OmKeyInfo> versions, boolean truncated,
      String nextKeyMarker, long nextVersionIdMarker) {
    this.versions = Collections.unmodifiableList(versions);
    this.truncated = truncated;
    this.nextKeyMarker = nextKeyMarker;
    this.nextVersionIdMarker = nextVersionIdMarker;
  }

  public List<OmKeyInfo> getVersions() {
    return versions;
  }

  public boolean isTruncated() {
    return truncated;
  }

  /** Key to resume from when truncated; null otherwise. */
  public String getNextKeyMarker() {
    return nextKeyMarker;
  }

  /** Version slot to resume after within nextKeyMarker. */
  public long getNextVersionIdMarker() {
    return nextVersionIdMarker;
  }
}
