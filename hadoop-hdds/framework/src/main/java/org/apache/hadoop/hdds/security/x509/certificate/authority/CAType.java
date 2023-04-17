/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.hadoop.hdds.security.x509.certificate.authority;

/**
 * Certificate authority type. Root certificate is typically a self-signed
 * certificate by the SCM. It can also be specified from an external
 * source.
 */
public enum CAType {
  NONE(""),
  SUBORDINATE("CA-"),
  ROOT("ROOTCA-");

  private final String fileNamePrefix;

  public final String getFileNamePrefix() {
    return fileNamePrefix;
  }

  CAType(String fileNamePrefix) {
    this.fileNamePrefix = fileNamePrefix;
  }
}
