package org.apache.hadoop.ozone.container.common.helpers;

import org.apache.hadoop.metrics2.MetricsSystem;
import org.apache.hadoop.metrics2.annotation.Metric;
import org.apache.hadoop.metrics2.annotation.Metrics;
import org.apache.hadoop.metrics2.lib.DefaultMetricsSystem;
import org.apache.hadoop.metrics2.lib.MutableCounterLong;
import org.apache.hadoop.ozone.container.keyvalue.statemachine.background.BlockDeletingService;

/**
 * Metrics related to Block Deleting Service running on Datanode.
 */
@Metrics(name = "BlockDeletingService Metrics", about = "Metrics related to "
    + "background block deleting service on Datanode", context = "dfs")
public class BlockDeletingServiceMetrics {

  private static BlockDeletingServiceMetrics instance;
  public static final String SOURCE_NAME =
      BlockDeletingService.class.getSimpleName();

  @Metric(about = "The number of success delete block counts.")
  private MutableCounterLong numBlockDeletionSuccessCount;

  @Metric(about = "The number of success deleted block bytes.")
  private MutableCounterLong numBlockDeletionSuccessBytes;

  @Metric(about = "The number of failure delete block counts.")
  private MutableCounterLong numBlockDeletionFailureCount;

  private BlockDeletingServiceMetrics() {
  }

  public static BlockDeletingServiceMetrics create() {
    if (instance == null) {
      MetricsSystem ms = DefaultMetricsSystem.instance();
      instance = ms.register(SOURCE_NAME, "BlockDeletingService",
          new BlockDeletingServiceMetrics());
    }

    return instance;
  }

  /**
   * Unregister the metrics instance.
   */
  public static void unRegister() {
    instance = null;
    MetricsSystem ms = DefaultMetricsSystem.instance();
    ms.unregisterSource(SOURCE_NAME);
  }

  public void incrBlockDeletionSuccessCount(long count) {
    this.numBlockDeletionSuccessCount.incr(count);
  }

  public void incrBlockDeletionSuccessBytes(long bytes) {
    this.numBlockDeletionSuccessBytes.incr(bytes);
  }

  public void incrBlockDeletionFailureCount() {
    this.numBlockDeletionFailureCount.incr();
  }

  public long getNumBlockDeletionSuccessCount() {
    return numBlockDeletionSuccessCount.value();
  }

  public long getNumBlockDeletionSuccessBytes() {
    return numBlockDeletionSuccessBytes.value();
  }

  public long getBNumBlockDeletionCommandFailure() {
    return numBlockDeletionFailureCount.value();
  }

  @Override
  public String toString() {
    StringBuffer buffer = new StringBuffer();
    buffer.append("numBlockDeletionSuccessCount = "
        + numBlockDeletionSuccessCount.value()).append("\t")
        .append("numBlockDeletionSuccessBytes = "
            + numBlockDeletionSuccessBytes.value()).append("\t")
        .append("numBlockDeletionFailureCount = "
            + numBlockDeletionFailureCount.value()).append("\t");
    return buffer.toString();
  }
}