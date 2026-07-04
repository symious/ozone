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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import org.apache.hadoop.ozone.s3.util.S3Consts;

/**
 * Request/response body of GetBucketVersioning and PutBucketVersioning.
 * An unversioned bucket returns the configuration without a Status element.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "VersioningConfiguration",
    namespace = S3Consts.S3_XML_NAMESPACE)
public class S3VersioningConfiguration {

  public static final String ENABLED = "Enabled";
  public static final String SUSPENDED = "Suspended";

  @XmlElement(name = "Status")
  private String status;

  @XmlElement(name = "MfaDelete")
  private String mfaDelete;

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getMfaDelete() {
    return mfaDelete;
  }

  public void setMfaDelete(String mfaDelete) {
    this.mfaDelete = mfaDelete;
  }
}
