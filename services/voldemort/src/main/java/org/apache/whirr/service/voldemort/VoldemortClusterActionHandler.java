/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.whirr.service.voldemort;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.apache.commons.configuration.Configuration;
import org.apache.whirr.service.Cluster;
import org.apache.whirr.service.Cluster.Instance;
import org.apache.whirr.service.ClusterActionEvent;
import org.apache.whirr.service.ClusterActionHandlerSupport;
import org.apache.whirr.service.ClusterSpec;
import org.apache.whirr.service.ComputeServiceContextBuilder;
import org.apache.whirr.service.jclouds.FirewallSettings;
import org.jclouds.compute.ComputeServiceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VoldemortClusterActionHandler extends ClusterActionHandlerSupport {
  
  private static final Logger LOG =
    LoggerFactory.getLogger(VoldemortClusterActionHandler.class);
    
  public static final String VOLDEMORT_ROLE = "voldemort";
  public static final int CLIENT_PORT = 6666;
  public static final int ADMIN_PORT = 6667;
  public static final int HTTP_PORT = 8081;
  
  public static final String PARTITIONS_PER_NODE = "whirr.voldemort.partitions";
  
  @Override
  public String getRole() {
    return VOLDEMORT_ROLE;
  }
  
  @Override
  protected void beforeBootstrap(ClusterActionEvent event) throws IOException {
    addRunUrl(event, "sun/java/install");
    addRunUrl(event, "linkedin/voldemort/install");
  }

  @Override
  protected void beforeConfigure(ClusterActionEvent event)
      throws IOException, InterruptedException {
    ClusterSpec clusterSpec = event.getClusterSpec();
    Cluster cluster = event.getCluster();
    
    LOG.info("Authorizing firewall");
    ComputeServiceContext computeServiceContext =
      ComputeServiceContextBuilder.build(clusterSpec);
    FirewallSettings.authorizeIngress(computeServiceContext,
        cluster.getInstances(), clusterSpec, CLIENT_PORT);
    FirewallSettings.authorizeIngress(computeServiceContext,
        cluster.getInstances(), clusterSpec, ADMIN_PORT);
    FirewallSettings.authorizeIngress(computeServiceContext,
            cluster.getInstances(), clusterSpec, HTTP_PORT);
    
    String servers = Joiner.on(' ').join(getConcatenatedIps(cluster.getInstances()));
    
    Configuration config = event.getClusterSpec().getConfiguration();
    int partitionsPerNode = config.getInt(PARTITIONS_PER_NODE, 10);
    addRunUrl(event, "linkedin/voldemort/post-configure",
        "-c", clusterSpec.getProvider(), "-p", Integer.toString(partitionsPerNode), servers);
  }

  @Override
  protected void afterConfigure(ClusterActionEvent event) {
    ClusterSpec clusterSpec = event.getClusterSpec();
    Cluster cluster = event.getCluster();
    String hosts = Joiner.on(',').join(getPublicIps(cluster.getInstances()));
    LOG.info("Completed setup of Voldemort {} with hosts {}", 
             clusterSpec.getClusterName(), hosts);
  }
  
  /**
   * Given a set of instances returns a list of their public ips
   * 
   * @param instances Set of instances in the cluster
   * @return List of all public ips as strings
   */
  private List<String> getPublicIps(Set<Instance> instances) {
    return Lists.transform(Lists.newArrayList(instances),
        new Function<Instance, String>() {
      @Override
      public String apply(Instance instance) {
        return instance.getPublicAddress().getHostAddress();
      }
    });
  }
  
  /**
   * Returns the ips of all instances concatenated as <public ip>:<private ip>
   * 
   * @param instances Set of instances in the cluster
   * @return List of all public and private ips as strings
   */
  private List<String> getConcatenatedIps(Set<Instance> instances) {
      return Lists.transform(Lists.newArrayList(instances),
          new Function<Instance, String>() {
        @Override
        public String apply(Instance instance) {
          return instance.getPublicAddress().getHostAddress() + ":" +
                 instance.getPrivateAddress().getHostAddress();
        }
      });
    }
}
