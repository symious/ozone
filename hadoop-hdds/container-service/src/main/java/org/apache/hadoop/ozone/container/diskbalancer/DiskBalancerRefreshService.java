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

import org.apache.hadoop.hdds.conf.ConfigurationSource;
import org.apache.hadoop.hdds.utils.BackgroundService;
import org.apache.hadoop.hdds.utils.BackgroundTaskQueue;
import org.apache.hadoop.ozone.container.ozoneimpl.OzoneContainer;

import java.util.concurrent.TimeUnit;


public class DiskBalancerRefreshService extends BackgroundService {

  private OzoneContainer ozoneContainer;
  private final ConfigurationSource conf;
  private DiskBalancerService diskBalancerService;

  public DiskBalancerRefreshService(OzoneContainer ozoneContainer,
      DiskBalancerService diskBalancerService, long serviceCheckInterval,
      long serviceCheckTimeout, TimeUnit timeUnit, int workerSize,
      ConfigurationSource conf) {
      super("DiskBalancerService", serviceCheckInterval, timeUnit, workerSize,
          serviceCheckTimeout);
      this.diskBalancerService = diskBalancerService;
      this.ozoneContainer = ozoneContainer;
      this.conf = conf;
  }

  @Override
  public BackgroundTaskQueue getTasks() {
    return null;
  }
}
