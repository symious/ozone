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
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.apache.hadoop.hdds.scm.container.placement.algorithms;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.apache.hadoop.hdds.conf.ConfigurationSource;
import org.apache.hadoop.hdds.protocol.DatanodeDetails;
import org.apache.hadoop.hdds.scm.SCMCommonPlacementPolicy;
import org.apache.hadoop.hdds.scm.exceptions.SCMException;
import org.apache.hadoop.hdds.scm.net.InnerNode;
import org.apache.hadoop.hdds.scm.net.NetConstants;
import org.apache.hadoop.hdds.scm.net.NetworkTopology;
import org.apache.hadoop.hdds.scm.net.Node;
import org.apache.hadoop.hdds.scm.node.DatanodeInfo;
import org.apache.hadoop.hdds.scm.node.NodeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Container placement policy that scatter datanodes on different racks
 * , together with the space to satisfy the size constraints.
 * <p>
 * This placement policy will try to distribute datanodes on as many racks as
 * possible.
 * <p>
 * This implementation applies to network topology like "/rack/node". Don't
 * recommend to use this if the network topology has more layers.
 * <p>
 */
public final class SCMContainerPlacementRackScatter
    extends SCMCommonPlacementPolicy {
  @VisibleForTesting
  public static final Logger LOG =
      LoggerFactory.getLogger(SCMContainerPlacementRackScatter.class);
  private final NetworkTopology networkTopology;
  private static final int RACK_LEVEL = 1;
  private static final int MAX_RETRY= 3;
  private final SCMContainerPlacementMetrics metrics;
  // Used to check the placement policy is validated in the parent class
//  private static final int REQUIRED_RACKS = 2;

  /**
   * Constructs a Container Placement with rack awareness.
   *
   * @param nodeManager Node Manager
   * @param conf Configuration
   */
  public SCMContainerPlacementRackScatter(final NodeManager nodeManager,
      final ConfigurationSource conf, final NetworkTopology networkTopology,
      final SCMContainerPlacementMetrics metrics) {
    super(nodeManager, conf);
    this.networkTopology = networkTopology;
    this.metrics = metrics;
  }

  /**
   * Called by SCM to choose datanodes.
   * There are two scenarios, one is choosing all nodes for a new pipeline.
   * Another is choosing node to meet replication requirement.
   *
   *
   * @param excludedNodes - list of the datanodes to exclude.
   * @param favoredNodes - list of nodes preferred. This is a hint to the
   *                     allocator, whether the favored nodes will be used
   *                     depends on whether the nodes meets the allocator's
   *                     requirement.
   * @param nodesRequired - number of datanodes required.
   * @param dataSizeRequired - size required for the container.
   * @param metadataSizeRequired - size required for Ratis metadata.
   * @return List of datanodes.
   * @throws SCMException  SCMException
   */
  @Override
  public List<DatanodeDetails> chooseDatanodes(
      List<DatanodeDetails> excludedNodes, List<DatanodeDetails> favoredNodes,
      int nodesRequired, long metadataSizeRequired, long dataSizeRequired)
      throws SCMException {
    Preconditions.checkArgument(nodesRequired > 0);
    metrics.incrDatanodeRequestCount(nodesRequired);
    int datanodeCount = networkTopology.getNumOfLeafNode(NetConstants.ROOT);
    int excludedNodesCount = excludedNodes == null ? 0 : excludedNodes.size();
    if (datanodeCount < nodesRequired + excludedNodesCount) {
      throw new SCMException("No enough datanodes to choose. " +
          "TotalNode = " + datanodeCount +
          " RequiredNode = " + nodesRequired +
          " ExcludedNode = " + excludedNodesCount, null);
    }
    List<DatanodeDetails> mutableFavoredNodes = favoredNodes;
    // sanity check of favoredNodes
    if (mutableFavoredNodes == null) {
      mutableFavoredNodes = new ArrayList<>();
    }
    if (excludedNodes != null) {
      mutableFavoredNodes.removeAll(excludedNodes);
    }

    int rackLevel = networkTopology.getMaxLevel()-1;
    List<Node> racks = networkTopology.getNodes(rackLevel);

    List<Node> toChooseRack = new ArrayList<>(racks);

    List<Node> chosenNodes = new ArrayList<>();
    List<Node> unavailableNodes = new ArrayList<>();
    if (excludedNodes != null) {
      unavailableNodes.addAll(excludedNodes);
    }

    int retry_count = 0;
    while (nodesRequired > 0) {
      if (retry_count > MAX_RETRY) {
        throw new SCMException("No satisfied datanode to meet the" +
            " excludedNodes and affinityNode constrains.", null);
      }
      int chosenListSize = chosenNodes.size();

      if (toChooseRack.size() == 0) {
         toChooseRack.addAll(racks);
      }

      if (mutableFavoredNodes.size() > 0) {
        for (DatanodeDetails favoredNode : mutableFavoredNodes) {
          Node curRack = getRackOfDatanodeDetails(favoredNode);
          if (toChooseRack.contains(curRack)) {
            chosenNodes.add(favoredNode);
            toChooseRack.remove(curRack);
            mutableFavoredNodes.remove(favoredNode);
            unavailableNodes.add(favoredNode);
            nodesRequired--;
          }
        }
      }

      for (Node rack : toChooseRack) {
        if (((InnerNode)rack).getNodes(2).size() > 0) {
          Node affinityNode = ((InnerNode)rack).getNodes(2).get(0);
          Node node = chooseNode(unavailableNodes, affinityNode,
              metadataSizeRequired, dataSizeRequired);
          if (node != null) {
            chosenNodes.add(node);
            mutableFavoredNodes.remove(node);
            unavailableNodes.add(node);
            nodesRequired--;
          }
        }
        toChooseRack.remove(rack);
      }

      if (chosenListSize == chosenNodes.size()) {
        retry_count ++;
      } else {
        retry_count = 0;
      }
    }

    return Arrays.asList(chosenNodes.toArray(new DatanodeDetails[0]));
  }

  @Override
  public DatanodeDetails chooseNode(List<DatanodeDetails> healthyNodes) {
    return null;
  }

  /**
   * Choose a datanode which meets the requirements. If there is no node which
   * meets all the requirements, there is fallback chosen process depending on
   * whether fallback is allowed when this class is instantiated.
   *
   *
   * @param excludedNodes - list of the datanodes to excluded. Can be null.
   * @param affinityNode - the chosen nodes should be on the same rack as
   *                    affinityNode. Can be null.
   * @param dataSizeRequired - size required for the container.
   * @param metadataSizeRequired - size required for Ratis metadata.
   * @return List of chosen datanodes.
   * @throws SCMException  SCMException
   */
  private Node chooseNode(List<Node> excludedNodes, Node affinityNode,
      long metadataSizeRequired, long dataSizeRequired) throws SCMException {
    int ancestorGen = RACK_LEVEL;
    int maxRetry = MAX_RETRY;
    List<String> excludedNodesForCapacity = null;
    while(true) {
      metrics.incrDatanodeChooseAttemptCount();
      Node node = networkTopology.chooseRandom(NetConstants.ROOT,
          excludedNodesForCapacity, excludedNodes, affinityNode, ancestorGen);
      if (node == null) {
        // cannot find the node which meets all constrains
        LOG.warn("Failed to find the datanode for container. excludedNodes:" +
            (excludedNodes == null ? "" : excludedNodes.toString()) +
            ", affinityNode:" +
            (affinityNode == null ? "" : affinityNode.getNetworkFullPath()));
        return null;
      }

      DatanodeDetails datanodeDetails = (DatanodeDetails)node;
      DatanodeInfo datanodeInfo = (DatanodeInfo)getNodeManager()
          .getNodeByUuid(datanodeDetails.getUuidString());
      if (datanodeInfo == null) {
        LOG.error("Failed to find the DatanodeInfo for datanode {}",
            datanodeDetails);
      } else {
        if (datanodeInfo.getNodeStatus().isNodeWritable() &&
            (hasEnoughSpace(datanodeInfo, metadataSizeRequired,
                dataSizeRequired))) {
          LOG.debug("Datanode {} is chosen. Required metadata size is {} and " +
                  "required data size is {}",
              node.toString(), metadataSizeRequired, dataSizeRequired);
          metrics.incrDatanodeChooseSuccessCount();
          return node;
        }
      }

      maxRetry--;
      if (maxRetry == 0) {
        // avoid the infinite loop
        String errMsg = "No satisfied datanode to meet the space constrains. "
            + "metadatadata size required: " + metadataSizeRequired +
            " data size required: " + dataSizeRequired;
        LOG.info(errMsg);
        return null;
      }
      if (excludedNodesForCapacity == null) {
        excludedNodesForCapacity = new ArrayList<>();
      }
      excludedNodesForCapacity.add(node.getNetworkFullPath());
    }
  }

  private Node getRackOfDatanodeDetails(DatanodeDetails datanodeDetails) {
    String location = datanodeDetails.getNetworkLocation();
    return networkTopology.getAncestor(networkTopology.getNode(location),
        RACK_LEVEL);
  }

}
