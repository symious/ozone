/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.  The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.apache.hadoop.ozone.container.diskbalancer;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.hadoop.hdds.HddsConfigKeys;
import org.apache.hadoop.hdds.conf.ConfigurationSource;
import org.apache.hadoop.hdds.protocol.DatanodeDetails;
import org.apache.hadoop.hdds.protocol.proto.StorageContainerDatanodeProtocolProtos.DiskBalancerReportProto;
import org.apache.hadoop.hdds.scm.storage.DiskBalancerConfiguration;
import org.apache.hadoop.hdds.server.ServerUtils;
import org.apache.hadoop.hdds.utils.BackgroundService;
import org.apache.hadoop.hdds.utils.BackgroundTaskQueue;
import org.apache.hadoop.ozone.OzoneConsts;
import org.apache.hadoop.ozone.container.common.statemachine.DatanodeConfiguration;
import org.apache.hadoop.ozone.container.ozoneimpl.OzoneContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


public class DiskBalancerService extends BackgroundService {

  private static final Logger LOG =
      LoggerFactory.getLogger(DiskBalancerService.class);

  private OzoneContainer ozoneContainer;
  private final ConfigurationSource conf;

  private boolean shouldRun = false;
  private double threshold;
  private long bandwidthInMB;
  private int parallelThread;

  private final File diskBalancerInfoFile;

  public DiskBalancerService(OzoneContainer ozoneContainer,
      long serviceCheckInterval, long serviceCheckTimeout, TimeUnit timeUnit,
      int workerSize, ConfigurationSource conf) throws IOException {
    super("DiskBalancerService", serviceCheckInterval, timeUnit, workerSize,
        serviceCheckTimeout);
    this.ozoneContainer = ozoneContainer;
    this.conf = conf;

    String diskBalancerInfoPath = getDiskBalancerInfoPath(conf);
    Preconditions.checkNotNull(diskBalancerInfoPath);
    diskBalancerInfoFile = new File(diskBalancerInfoPath);

    loadDiskBalancerInfo();
  }

  /**
   * Update DiskBalancerService based on new DiskBalancerInfo
   * @param diskBalancerInfo
   * @throws IOException
   */
  public void refresh(DiskBalancerInfo diskBalancerInfo) throws IOException {
    applyDiskBalancerInfo(diskBalancerInfo);
  }

  /**
   * If the diskBalancer.info file exists,
   * @throws IOException
   */
  private void loadDiskBalancerInfo() throws IOException {
    DiskBalancerInfo diskBalancerInfo = null;
    try {
      if (diskBalancerInfoFile.exists()) {
        diskBalancerInfo = readDiskBalancerInfoFile(diskBalancerInfoFile);
      }
    } catch (IOException e) {
      LOG.warn("Can not load diskBalancerInfo from diskBalancer.info file. " +
          "Falling back to default configs", e);
    } finally {
      if (diskBalancerInfo == null) {
        boolean shouldRunDefault = conf.getObject(DatanodeConfiguration.class)
            .getDiskBalancerShouldRun();
        diskBalancerInfo = new DiskBalancerInfo(shouldRunDefault,
            new DiskBalancerConfiguration());
      }
    }

    applyDiskBalancerInfo(diskBalancerInfo);
  }

  private void applyDiskBalancerInfo(DiskBalancerInfo diskBalancerInfo)
      throws IOException {
    // First store in local file, then update in memory variables
    writeDiskBalancerInfoTo(diskBalancerInfo, diskBalancerInfoFile);

    setShouldRun(diskBalancerInfo.isShouldRun());
    setThreshold(diskBalancerInfo.getThreshold());
    setBandwidthInMB(diskBalancerInfo.getBandwidthInMB());
    setParallelThread(diskBalancerInfo.getParallelThread());

    // Default executorService is ScheduledThreadPoolExecutor, so we can
    // update the poll size by setting corePoolSize.
    if ((getExecutorService() instanceof ScheduledThreadPoolExecutor)) {
      ((ScheduledThreadPoolExecutor) getExecutorService())
          .setCorePoolSize(parallelThread);
    }
  }

  private String getDiskBalancerInfoPath(ConfigurationSource conf) {
    String diskBalancerInfoDir =
        conf.getTrimmed(HddsConfigKeys.HDDS_DATANODE_DISK_BALANCER_INFO_DIR);
    if (Strings.isNullOrEmpty(diskBalancerInfoDir)) {
      File metaDirPath = ServerUtils.getOzoneMetaDirPath(conf);
      if (metaDirPath == null) {
        // this means meta data is not found, in theory should not happen at
        // this point because should've failed earlier.
        throw new IllegalArgumentException("Unable to locate meta data" +
            "directory when getting datanode disk balancer file path");
      }
      diskBalancerInfoDir = metaDirPath.toString();
    }
    // Use default datanode disk balancer file name for file path
    return new File(diskBalancerInfoDir,
        OzoneConsts.OZONE_SCM_DATANODE_DISK_BALANCER_INFO_DEFAULT).toString();
  }

  /**
   * Read {@link DiskBalancerInfo} from a local info file.
   *
   * @param path DiskBalancerInfo file local path
   * @return {@link DatanodeDetails}
   * @throws IOException If the conf file is malformed or other I/O exceptions
   */
  private synchronized DiskBalancerInfo readDiskBalancerInfoFile(
      File path) throws IOException {
    if (!path.exists()) {
      throw new IOException("DiskBalancerConf file not found.");
    }
    try {
      return DiskBalancerYaml.readDiskBalancerInfoFile(path);
    } catch (IOException e) {
      LOG.warn("Error loading DiskBalancerInfo yaml from {}",
          path.getAbsolutePath(), e);
      throw new IOException("Failed to parse DiskBalancerInfo from "
          + path.getAbsolutePath(), e);
    }
  }

  /**
   * Persistent a {@link DiskBalancerInfo} to a local file.
   *
   * @throws IOException when read/write error occurs
   */
  private synchronized void writeDiskBalancerInfoTo(
      DiskBalancerInfo diskBalancerInfo, File path)
      throws IOException {
    if (path.exists()) {
      if (!path.delete() || !path.createNewFile()) {
        throw new IOException("Unable to overwrite the DiskBalancerInfo file.");
      }
    } else {
      if (!path.getParentFile().exists() &&
          !path.getParentFile().mkdirs()) {
        throw new IOException("Unable to create DiskBalancerInfo directories.");
      }
    }
    DiskBalancerYaml.createDiskBalancerInfoFile(diskBalancerInfo, path);
  }



  public void setShouldRun(boolean shouldRun) {
    this.shouldRun = shouldRun;
  }

  private void setThreshold(double threshold) {
    this.threshold = threshold;
  }

  private void setBandwidthInMB(long bandwidthInMB) {
    this.bandwidthInMB = bandwidthInMB;
  }

  private void setParallelThread(int parallelThread) {
    this.parallelThread = parallelThread;
  }

  public DiskBalancerInfo getDiskBalancerInfo() {
    return new DiskBalancerInfo(shouldRun, threshold, bandwidthInMB,
        parallelThread);
  }

  public DiskBalancerReportProto getDiskBalancerReportProto() {
    return null;
  }

  @Override
  public BackgroundTaskQueue getTasks() {
    return null;
  }
}
