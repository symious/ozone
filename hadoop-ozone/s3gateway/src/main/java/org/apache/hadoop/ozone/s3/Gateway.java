/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.ozone.s3;

import java.io.IOException;

import org.apache.hadoop.hdds.StringUtils;
import org.apache.hadoop.hdds.cli.GenericCli;
import org.apache.hadoop.hdds.cli.HddsVersionProvider;
import org.apache.hadoop.hdds.conf.OzoneConfiguration;
import org.apache.hadoop.hdds.tracing.TracingUtil;
import org.apache.hadoop.ozone.util.OzoneVersionInfo;

import org.apache.hadoop.ozone.util.ShutdownHookManager;
import org.apache.hadoop.security.UserGroupInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;

import static org.apache.hadoop.ozone.conf.OzoneServiceConfig.DEFAULT_SHUTDOWN_HOOK_PRIORITY;

/**
 * This class is used to start/stop S3 compatible rest server.
 */
@Command(name = "ozone s3g",
    hidden = true, description = "S3 compatible rest server.",
    versionProvider = HddsVersionProvider.class,
    mixinStandardHelpOptions = true)
public class Gateway extends GenericCli {

  private static final Logger LOG = LoggerFactory.getLogger(Gateway.class);

  private S3GatewayHttpServer httpServer;

  public static void main(String[] args) throws Exception {
    new Gateway().run(args);
  }

  @Override
  public Void call() throws Exception {
    OzoneConfiguration ozoneConfiguration = createOzoneConfiguration();
    TracingUtil.initTracing("S3gateway", ozoneConfiguration);
    OzoneConfigurationHolder.setConfiguration(ozoneConfiguration);
    UserGroupInformation.setConfiguration(ozoneConfiguration);
    httpServer = new S3GatewayHttpServer(ozoneConfiguration, "s3gateway");
    start();

    ShutdownHookManager.get().addShutdownHook(() -> {
      try {
        stop();
      } catch (Exception e) {
        LOG.error("Error during stop S3Gateway", e);
      }
    }, DEFAULT_SHUTDOWN_HOOK_PRIORITY);
    return null;
  }

  public void start() throws IOException {
    String[] originalArgs = getCmd().getParseResult().originalArgs()
        .toArray(new String[0]);
    StringUtils.startupShutdownMessage(OzoneVersionInfo.OZONE_VERSION_INFO,
        Gateway.class, originalArgs, LOG);

    LOG.info("Starting Ozone S3 gateway");
    httpServer.start();
  }

  public void stop() throws Exception {
    LOG.info("Stopping Ozone S3 gateway");
    httpServer.stop();
  }

}
