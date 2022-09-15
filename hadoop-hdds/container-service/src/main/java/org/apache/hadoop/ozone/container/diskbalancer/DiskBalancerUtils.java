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

import org.apache.commons.lang3.tuple.Pair;
import org.apache.hadoop.ozone.container.common.utils.StorageVolumeUtil;
import org.apache.hadoop.ozone.container.common.volume.HddsVolume;
import org.apache.hadoop.ozone.container.common.volume.MutableVolumeSet;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Util class for DiskBalancer.
 */
public final class DiskBalancerUtils {

  private DiskBalancerUtils() {

  }

  /**
   * Choosing source volume and dest volume.
   * @param volumeSet volumeSet
   * @param threshold threshold of volume density
   * @param deltaMap the delta storage size of in progress balance jobs
   * @return Pair of SourceVolume and DestVolume
   */
  public static Pair<HddsVolume, HddsVolume> getVolumePair(
      MutableVolumeSet volumeSet, double threshold,
      Map<HddsVolume, Long> deltaMap) {

    double idealUsage = volumeSet.getIdealUsage();

    List<HddsVolume> volumes = StorageVolumeUtil
        .getHddsVolumesList(volumeSet.getVolumesList())
        .stream()
        .filter(volume -> Math.abs(
            (double) (volume.getUsedSpace() + deltaMap.getOrDefault(volume, 0L))
                / volume.getCapacity() - idealUsage) >= threshold)
        .sorted((v1, v2) ->
            Double.compare(
                (double) (v2.getUsedSpace() + deltaMap.getOrDefault(v2, 0L)) /
                    v2.getCapacity(),
                (double) (v1.getUsedSpace() + deltaMap.getOrDefault(v1, 0L)) /
                    v1.getCapacity()))
        .collect(Collectors.toList());

    // Can not generate DiskBalancerTask if we have less than 2 results
    if (volumes.size() <= 1) {
      return null;
    }
    return Pair.of(volumes.get(0), volumes.get(volumes.size() - 1));
  }
}