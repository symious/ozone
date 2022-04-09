/**
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
 * distributed under the License is distributed on an "AS IS" BASIS,WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.apache.hadoop.ozone.audit;

import com.google.common.annotations.VisibleForTesting;
import org.apache.hadoop.hdds.conf.OzoneConfiguration;
import org.apache.hadoop.util.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.spi.ExtendedLogger;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;


/**
 * Class to define Audit Logger for Ozone.
 */
public class AuditLogger {

  private ExtendedLogger logger;
  private static final String FQCN = AuditLogger.class.getName();
  private static final Marker WRITE_MARKER = AuditMarker.WRITE.getMarker();
  private static final Marker READ_MARKER = AuditMarker.READ.getMarker();
  private AtomicReference<Set<String>> debugCmdSetRef =
      new AtomicReference<>(new HashSet<>());
  public static String AUDIT_LOG_DEBUG_CMD_PREFIX =
      "ozone.audit.log.debug.cmd.";
  private AuditLoggerType type;

  /**
   * Parametrized Constructor to initialize logger.
   */
  private AuditLogger() {
  }

  /**
   * Initializes the logger with specific type.
   * @param loggerType specified one of the values from enum AuditLoggerType.
   */
  public void initializeLogger(AuditLoggerType loggerType) {
    this.logger = LogManager.getContext(false).getLogger(loggerType.getType());
    this.type = loggerType;
    refreshDebugCmdSet();
  }

  @VisibleForTesting
  public ExtendedLogger getLogger() {
    return logger;
  }

  public void logWriteSuccess(AuditMessage msg) {
    System.out.println("YYYY333" + debugCmdSetRef.get());
    System.out.println("YYYY444" + msg.getOp());
    if (!debugCmdSetRef.get().contains(msg.getOp().toLowerCase(Locale.ROOT))) {
      this.logger.logIfEnabled(FQCN, Level.INFO, WRITE_MARKER, msg, null);
    }
  }

  public void logWriteFailure(AuditMessage msg) {
    this.logger.logIfEnabled(FQCN, Level.ERROR, WRITE_MARKER, msg,
        msg.getThrowable());
  }

  public void logReadSuccess(AuditMessage msg) {
    if (!debugCmdSetRef.get().contains(msg.getOp().toLowerCase(Locale.ROOT))) {
      this.logger.logIfEnabled(FQCN, Level.INFO, READ_MARKER, msg, null);
    }
  }

  public void logReadFailure(AuditMessage msg) {
    this.logger.logIfEnabled(FQCN, Level.ERROR, READ_MARKER, msg,
        msg.getThrowable());
  }

  public void logWrite(AuditMessage auditMessage) {
    if (auditMessage.getThrowable() == null) {
      if (debugCmdSetRef.get().contains(
          auditMessage.getOp().toLowerCase(Locale.ROOT))) {
        this.logger.logIfEnabled(FQCN, Level.INFO, WRITE_MARKER, auditMessage,
            auditMessage.getThrowable());
      }
    } else {
      this.logger.logIfEnabled(FQCN, Level.ERROR, WRITE_MARKER, auditMessage,
          auditMessage.getThrowable());
    }
  }

  public void refreshDebugCmdSet() {
    OzoneConfiguration conf = new OzoneConfiguration();
    refreshDebugCmdSet(conf);
  }

  public synchronized void refreshDebugCmdSet(OzoneConfiguration conf) {
    Collection<String> cmds = conf.getTrimmedStringCollection(
        AUDIT_LOG_DEBUG_CMD_PREFIX + type.getType().toLowerCase(Locale.ROOT));
    debugCmdSetRef = new AtomicReference<>(
          cmds.stream().map(String::toLowerCase).collect(Collectors.toSet()));
  }

  public static AuditLogger instance() {
    return AuditLoggerHolder.instance;
  }

  private static class AuditLoggerHolder {
    private static final AuditLogger instance = new AuditLogger();
  }
}
