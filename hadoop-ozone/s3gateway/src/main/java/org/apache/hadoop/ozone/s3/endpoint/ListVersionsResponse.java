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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import org.apache.hadoop.ozone.s3.commontypes.IsoDateAdapter;
import org.apache.hadoop.ozone.s3.util.S3Consts;

/**
 * Response body of ListObjectVersions ({@code GET /bucket?versions}).
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "ListVersionsResult",
    namespace = S3Consts.S3_XML_NAMESPACE)
public class ListVersionsResponse {

  @XmlElement(name = "Name")
  private String name;

  @XmlElement(name = "Prefix")
  private String prefix;

  @XmlElement(name = "KeyMarker")
  private String keyMarker;

  @XmlElement(name = "VersionIdMarker")
  private String versionIdMarker;

  @XmlElement(name = "NextKeyMarker")
  private String nextKeyMarker;

  @XmlElement(name = "NextVersionIdMarker")
  private String nextVersionIdMarker;

  @XmlElement(name = "MaxKeys")
  private int maxKeys;

  @XmlElement(name = "IsTruncated")
  private boolean truncated;

  @XmlElement(name = "Version")
  private List<VersionMetadata> versions = new ArrayList<>();

  @XmlElement(name = "DeleteMarker")
  private List<DeleteMarkerMetadata> deleteMarkers = new ArrayList<>();

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }

  public String getKeyMarker() {
    return keyMarker;
  }

  public void setKeyMarker(String keyMarker) {
    this.keyMarker = keyMarker;
  }

  public String getVersionIdMarker() {
    return versionIdMarker;
  }

  public void setVersionIdMarker(String versionIdMarker) {
    this.versionIdMarker = versionIdMarker;
  }

  public String getNextKeyMarker() {
    return nextKeyMarker;
  }

  public void setNextKeyMarker(String nextKeyMarker) {
    this.nextKeyMarker = nextKeyMarker;
  }

  public String getNextVersionIdMarker() {
    return nextVersionIdMarker;
  }

  public void setNextVersionIdMarker(String nextVersionIdMarker) {
    this.nextVersionIdMarker = nextVersionIdMarker;
  }

  public int getMaxKeys() {
    return maxKeys;
  }

  public void setMaxKeys(int maxKeys) {
    this.maxKeys = maxKeys;
  }

  public boolean isTruncated() {
    return truncated;
  }

  public void setTruncated(boolean truncated) {
    this.truncated = truncated;
  }

  public List<VersionMetadata> getVersions() {
    return versions;
  }

  public void addVersion(VersionMetadata version) {
    versions.add(version);
  }

  public List<DeleteMarkerMetadata> getDeleteMarkers() {
    return deleteMarkers;
  }

  public void addDeleteMarker(DeleteMarkerMetadata deleteMarker) {
    deleteMarkers.add(deleteMarker);
  }

  /** A delete marker entry: a version without data. */
  @XmlAccessorType(XmlAccessType.FIELD)
  public static class DeleteMarkerMetadata {

    @XmlElement(name = "Key")
    private String key;

    @XmlElement(name = "VersionId")
    private String versionId;

    @XmlElement(name = "IsLatest")
    private boolean latest;

    @XmlJavaTypeAdapter(IsoDateAdapter.class)
    @XmlElement(name = "LastModified")
    private Instant lastModified;

    @XmlElement(name = "Owner")
    private S3Owner owner;

    public String getKey() {
      return key;
    }

    public void setKey(String key) {
      this.key = key;
    }

    public String getVersionId() {
      return versionId;
    }

    public void setVersionId(String versionId) {
      this.versionId = versionId;
    }

    public boolean isLatest() {
      return latest;
    }

    public void setLatest(boolean latest) {
      this.latest = latest;
    }

    public Instant getLastModified() {
      return lastModified;
    }

    public void setLastModified(Instant lastModified) {
      this.lastModified = lastModified;
    }

    public S3Owner getOwner() {
      return owner;
    }

    public void setOwner(S3Owner owner) {
      this.owner = owner;
    }
  }

  /** An object version entry. */
  @XmlAccessorType(XmlAccessType.FIELD)
  public static class VersionMetadata extends DeleteMarkerMetadata {

    @XmlElement(name = "ETag")
    private String eTag;

    @XmlElement(name = "Size")
    private long size;

    @XmlElement(name = "StorageClass")
    private String storageClass;

    public String getETag() {
      return eTag;
    }

    public void setETag(String tag) {
      this.eTag = tag;
    }

    public long getSize() {
      return size;
    }

    public void setSize(long size) {
      this.size = size;
    }

    public String getStorageClass() {
      return storageClass;
    }

    public void setStorageClass(String storageClass) {
      this.storageClass = storageClass;
    }
  }
}
