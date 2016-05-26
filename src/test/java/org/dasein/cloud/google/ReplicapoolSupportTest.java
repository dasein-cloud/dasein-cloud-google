/**
 * Copyright (C) 2009-2016 Dell, Inc.
 * See annotations for authorship information
 * <p>
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */

package org.dasein.cloud.google;

import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceList;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.replicapool.Replicapool;
import com.google.api.services.replicapool.model.InstanceGroupManager;
import com.google.api.services.replicapool.model.InstanceGroupManagerList;
import com.google.api.services.replicapool.model.Operation;
import mockit.Mocked;
import mockit.NonStrictExpectations;
import org.apache.commons.collections.IteratorUtils;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ResourceType;
import org.dasein.cloud.ci.ConvergedInfrastructure;
import org.dasein.cloud.ci.ConvergedInfrastructureProvisionOptions;
import org.dasein.cloud.ci.ConvergedInfrastructureResource;
import org.dasein.cloud.ci.ConvergedInfrastructureState;
import org.dasein.cloud.ci.ReplicapoolSupport;
import org.dasein.cloud.dc.DataCenter;
import org.dasein.cloud.dc.Region;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * User: daniellemayne
 * Date: 26/05/2016
 * Time: 10:40
 */
public class ReplicapoolSupportTest extends GoogleTestBase {
    private ReplicapoolSupport support = null;

    final String TEST_REPLICAPOOL = "TEST_REPLICAPOOL";

    @Mocked
    Replicapool rp;

    @Before
    public void setUp() throws CloudException, InternalException {
        super.setUp();
        support = new ReplicapoolSupport(googleProviderMock);

        new NonStrictExpectations(){
            {googleProviderMock.getGoogleReplicapool();
                result = rp;
            }
        };
    }

    private InstanceGroupManagerList getTestInstanceGroupManagerList() {
        InstanceGroupManager igm = new InstanceGroupManager();
        igm.setName(TEST_REPLICAPOOL);
        igm.setId(new BigInteger("112233"));
        igm.setDescription("DESCRIPTION");
        igm.setSelfLink("SELFLINK");
        igm.setGroup("GROUP");
        igm.setBaseInstanceName("BASEINSTANCE");

        List<InstanceGroupManager> list = new ArrayList<>();
        list.add(igm);

        InstanceGroupManagerList igml = new InstanceGroupManagerList();
        igml.setItems(list);
        return igml;
    }

    private InstanceList getTestInstanceList() {
        Instance instance = new Instance();
        instance.setName("BASEINSTANCE-"+TEST_VM_ID);

        NetworkInterface net = new NetworkInterface();
        net.setNetwork("NETWORK");
        List<NetworkInterface> netList = new ArrayList<>();
        netList.add(net);
        instance.setNetworkInterfaces(netList);

        List<Instance> instanceList = new ArrayList<>();
        instanceList.add(instance);

        InstanceList il = new InstanceList();
        il.setItems(instanceList);
        return il;
    }

    @Test
    public void listConvergedInfrastructuresNoFilter_shouldReturnAllAvailableCIs() throws CloudException, InternalException, IOException {
        final List<Region> regions = new ArrayList<>();
        Region region = new Region();
        region.setProviderRegionId(TEST_REGION);
        regions.add(region);

        final List<DataCenter> dataCenters = new ArrayList<>();
        DataCenter dc = new DataCenter(TEST_DATACENTER, TEST_REGION, TEST_REGION, true, true);
        dataCenters.add(dc);

        new NonStrictExpectations() {
            {googleProviderMock.getDataCenterServices().listRegions();
                result = regions;
            }
            {googleProviderMock.getDataCenterServices().listDataCenters(TEST_REGION);
                result = dataCenters;
            }
            {rp.instanceGroupManagers().list(TEST_ACCOUNT_NO, TEST_DATACENTER).execute();
                result = getTestInstanceGroupManagerList();
            }
            {googleComputeMock.instances().list(TEST_ACCOUNT_NO, TEST_DATACENTER).execute();
                result = getTestInstanceList();
            }
        };

        Iterable<ConvergedInfrastructure> list = support.listConvergedInfrastructures(null);
        assertNotNull(list);
        List<ConvergedInfrastructure> resultAsList = IteratorUtils.toList(list.iterator());
        assertTrue(resultAsList.size() == 1);

        ConvergedInfrastructure ci = resultAsList.get(0);
        assertTrue(ci.getName().equals(TEST_REPLICAPOOL));
        assertTrue(ci.getProviderCIId().equals(TEST_REPLICAPOOL));
        assertTrue(ci.getDescription().equals("DESCRIPTION"));
        assertTrue(ci.getCiState().equals(ConvergedInfrastructureState.READY));
        assertTrue(ci.getProviderRegionId().equals(TEST_REGION));
        assertTrue(ci.getProviderDatacenterId().equals(TEST_DATACENTER));
        List<ConvergedInfrastructureResource> cirList = ci.getResources();
        assertNotNull(cirList);
        assertTrue(cirList.size() == 2);
        ConvergedInfrastructureResource r = cirList.get(0);
        assertTrue(r.getResourceId().equals("BASEINSTANCE-"+TEST_VM_ID));
        assertTrue(r.getResourceType().equals(ResourceType.VIRTUAL_MACHINE));
        r = cirList.get(1);
        assertTrue(r.getResourceId().equals("NETWORK"));
        assertTrue(r.getResourceType().equals(ResourceType.VLAN));

        assertTrue(ci.getTags().size() == 2);
        assertTrue(ci.getTag("selfLink").toString().equals("SELFLINK"));
        assertTrue(ci.getTag("instanceGroupLink").toString().equals("GROUP"));
    }

    @Test
    public void provision_shouldReturnNewConvergedInfrastructure() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {rp.instanceGroupManagers().insert(TEST_ACCOUNT_NO, TEST_DATACENTER, 2, (InstanceGroupManager) any).execute();
                result = new Operation();
            }
            {googleMethodMock.getCIOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.ZONE_OPERATION, TEST_REGION, TEST_DATACENTER);
                result = true;
            }
        };

        ConvergedInfrastructure ci = support.provision(ConvergedInfrastructureProvisionOptions.getInstance(TEST_REPLICAPOOL, "DESCRIPTION", null, null, "TESTTEMPLATE", null, false)
                            .inDatacenter(TEST_DATACENTER).withBaseName("BASENAME").withInstanceCount(2));
        assertNotNull(ci);
    }

    @Test
    public void terminate_shouldResultInSuccessfulOperation() throws CloudException, InternalException, IOException {
        final ConvergedInfrastructure ci = ConvergedInfrastructure.getInstance(TEST_REPLICAPOOL, TEST_REPLICAPOOL, "DESCRIPTION", ConvergedInfrastructureState.READY,
                 System.currentTimeMillis(), TEST_DATACENTER, TEST_REGION, null);
        final List<ConvergedInfrastructure> ciList = new ArrayList<>();
        ciList.add(ci);

        new NonStrictExpectations(ReplicapoolSupport.class) {
            {support.listConvergedInfrastructures(null);
                result = ciList;
            }
            {rp.instanceGroupManagers().delete(TEST_ACCOUNT_NO, TEST_DATACENTER, TEST_REPLICAPOOL).execute();
                result = new Operation();
            }
            {googleMethodMock.getCIOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.ZONE_OPERATION, TEST_REGION, TEST_DATACENTER);
                result = true;
            }
        };

        support.terminate(TEST_REPLICAPOOL, "terminateTest");
    }

    @Test(expected = OperationNotSupportedException.class)
    public void cancelDeployment_shouldThrowOperationNotSupportedException() throws CloudException, InternalException {
        support.cancelDeployment(TEST_REPLICAPOOL, "TEST");
    }
}
