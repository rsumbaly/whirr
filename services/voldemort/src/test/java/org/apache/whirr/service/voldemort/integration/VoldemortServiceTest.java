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

package org.apache.whirr.service.voldemort.integration;

import java.io.IOException;
import java.util.Set;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.whirr.service.Cluster;
import org.apache.whirr.service.Cluster.Instance;
import org.apache.whirr.service.voldemort.VoldemortClusterActionHandler;
import org.apache.whirr.service.ClusterSpec;
import org.apache.whirr.service.Service;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import voldemort.client.ClientConfig;
import voldemort.client.SocketStoreClientFactory;
import voldemort.client.StoreClient;
import voldemort.client.protocol.admin.AdminClient;
import voldemort.client.protocol.admin.AdminClientConfig;

public class VoldemortServiceTest {

  private final static int NUM_KEYS = 1000;
  
  private ClusterSpec clusterSpec;
  private Service service;
  private Cluster cluster;

  @Before
  public void setUp() throws Exception {
    CompositeConfiguration config = new CompositeConfiguration();
    if (System.getProperty("config") != null) {
      config.addConfiguration(new PropertiesConfiguration(System.getProperty("config")));
    }
    config.addConfiguration(new PropertiesConfiguration("whirr-voldemort-test.properties"));
    clusterSpec = ClusterSpec.withTemporaryKeys(config);
    
    service = new Service();
    cluster = service.launchCluster(clusterSpec);

    waitForBootstrap();
  }

  private void waitForBootstrap() {
    for ( Instance instance : cluster.getInstances()) {
	  while( true ) {
	    try {
		  AdminClient client = new AdminClient("tcp://" + 
		                                       instance.getPublicAddress() + 
		                                       ":" + VoldemortClusterActionHandler.ADMIN_PORT, 
		                                       new AdminClientConfig());
		  client.getAdminClientCluster();
		  break;
	    } catch (Exception e) {
	      System.out.print(".");
	      try {
	        Thread.sleep(1000);
	      } catch (InterruptedException e1) {
	        break;
	      }
	    }
	  }
    }
  }
    
  @Test
  public void testInstances() throws Exception {
      Set<Instance> instances = cluster.getInstances();
      Instance firstInstance = instances.iterator().next();
      ClientConfig config = new ClientConfig().setBootstrapUrls( 
                                "tcp://" + firstInstance.getPublicAddress() + 
                                ":" + VoldemortClusterActionHandler.CLIENT_PORT );
      SocketStoreClientFactory factory = new SocketStoreClientFactory( config );
      StoreClient<String, String> client = factory.getStoreClient( "test" );
      
      for ( int keyId = 0; keyId < NUM_KEYS ; keyId++ ) {
          client.put( "key" + keyId, "value" + keyId );
      }
      
      for ( int keyId = 0; keyId < NUM_KEYS ; keyId ++ ) {
          Assert.assertEquals( client.get( "key" + keyId ).getValue(), "value" + keyId );
      }
  }
  
  @After
  public void tearDown() throws IOException, InterruptedException {
    if (service != null) {
      service.destroyCluster(clusterSpec);      
    }
  }

}
