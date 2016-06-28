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

import com.google.api.services.compute.model.Network;
import com.google.api.services.compute.model.NetworkList;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.Route;
import com.google.api.services.compute.model.RouteList;
import mockit.Mocked;
import mockit.NonStrictExpectations;
import org.apache.commons.collections.IteratorUtils;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.VisibleScope;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.google.network.FirewallSupport;
import org.dasein.cloud.google.network.NetworkSupport;
import org.dasein.cloud.network.IPVersion;
import org.dasein.cloud.network.RawAddress;
import org.dasein.cloud.network.VLAN;
import org.dasein.cloud.network.VLANState;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * User: daniellemayne
 * Date: 21/06/2016
 * Time: 11:28
 */
public class NetworkSupportTest extends GoogleTestBase {
    private NetworkSupport support = null;

    static final String TEST_ROUTING_TABLE_ID = "TEST_ROUTING_TABLE_ID";
    static final String TEST_DESTINATION_CIDR = "TEST_DESTINATION_CIDR";
    static final String TEST_ADDRESS = "TEST_ADDRESS";
    static final String TEST_GATEWAY = "TEST_GATEWAY";
    static final String TEST_NIC_ID = "TEST_NIC_ID";
    static final String TEST_VLAN_ID = "TEST_VLAN_ID";

    @Mocked
    FirewallSupport firewallSupport;

    @Before
    public void setUp() throws CloudException, InternalException {
        super.setUp();
        support = new NetworkSupport(googleProviderMock);
    }

    private RouteList getTestRouteList() {
        Route route = new Route();
        route.setName("TEST_ROUTE");
        route.setNetwork(TEST_VLAN_ID);
        route.setDestRange(TEST_DESTINATION_CIDR);
        route.setNextHopInstance(TEST_VM_ID);

        RouteList list = new RouteList();
        list.setItems(Collections.singletonList(route));
        return list;
    }

    private NetworkList getTestNetworkList() {
        Network network = new Network();
        network.setName(TEST_VLAN_ID);
        network.setSelfLink("/networks/"+TEST_VLAN_ID);
        network.setIPv4Range(TEST_DESTINATION_CIDR);
        network.setDescription("TEST_DESCRIPTION");

        NetworkList list = new NetworkList();
        list.setItems(Collections.singletonList(network));
        return list;
    }

    @Test(expected = OperationNotSupportedException.class)
    public void addRouteToAddress_shouldThrowNotSupportedException() throws CloudException, InternalException {
        support.addRouteToAddress(TEST_ROUTING_TABLE_ID, IPVersion.IPV4, TEST_DESTINATION_CIDR, TEST_ADDRESS);
    }

    @Test(expected = OperationNotSupportedException.class)
    public void addRouteToGateway_shouldThrowNotSupportedException() throws CloudException, InternalException {
        support.addRouteToGateway(TEST_ROUTING_TABLE_ID, IPVersion.IPV4, TEST_DESTINATION_CIDR, TEST_GATEWAY);
    }

    @Test(expected = OperationNotSupportedException.class)
    public void addRouteToNetworkInterface_shouldThrowNotSupportedException() throws CloudException, InternalException {
        support.addRouteToNetworkInterface(TEST_ROUTING_TABLE_ID, IPVersion.IPV4, TEST_DESTINATION_CIDR, TEST_NIC_ID);
    }

    @Test
    public void addRouteToVirtualMachine() throws CloudException, InternalException, IOException {
        final VirtualMachine vm = new VirtualMachine();
        vm.setTag("contentLink", "contentLink");
        vm.setPrivateAddresses(new RawAddress(TEST_ADDRESS));

        new NonStrictExpectations() {
            {googleProviderMock.getComputeServices().getVirtualMachineSupport().getVirtualMachine(TEST_VM_ID);
                result = vm;
            }
            {googleComputeMock.routes().insert(TEST_ACCOUNT_NO, (Route) any).execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationTarget(providerContextMock, (Operation) any, GoogleOperationType.GLOBAL_OPERATION, "", "", false);
                result = "NEW_ROUTE";
            }
            {googleComputeMock.routes().get(TEST_ACCOUNT_NO, "NEW_ROUTE").execute();
                result = getTestRouteList().getItems().get(0);
            }
        };

        org.dasein.cloud.network.Route route = support.addRouteToVirtualMachine(TEST_ROUTING_TABLE_ID, IPVersion.IPV4, TEST_DESTINATION_CIDR, TEST_VM_ID);
        assertTrue(route.getGatewayVirtualMachineId().equals(TEST_VM_ID));
        assertTrue(route.getDestinationCidr().equals(TEST_DESTINATION_CIDR));
        assertTrue(route.getGatewayOwnerId().equals(TEST_ACCOUNT_NO));
    }

    @Test(expected = OperationNotSupportedException.class)
    public void createVlan_shouldThrowExceptionIfCreatingVlansIsNotSupported() throws CloudException, InternalException {
        new NonStrictExpectations(NetworkSupport.class) {
            {support.getCapabilities().allowsNewVlanCreation();
                result = false;
            }
        };
        support.createVlan(TEST_DESTINATION_CIDR, "TEST_VLAN_NAME", "TEST_DESCRIPTION", "TEST_DOMAIN", new String[]{"192.168.1.1"}, new String[]{"192.168.1.1"});
    }

    @Test(expected = InternalException.class)
    public void createVlan_shouldThrowExceptionIfRegionNotSet() throws CloudException, InternalException {
        new NonStrictExpectations() {
            {providerContextMock.getRegionId();
                result = null;
            }
        };
        support.createVlan(TEST_DESTINATION_CIDR, "TEST_VLAN_NAME", "TEST_DESCRIPTION", "TEST_DOMAIN", new String[]{"192.168.1.1"}, new String[]{"192.168.1.1"});
    }

    @Test
    public void createVlan() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.networks().insert(TEST_ACCOUNT_NO, (Network) any).execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationTarget(providerContextMock, (Operation) any, GoogleOperationType.GLOBAL_OPERATION, "", "", false);
                result = TEST_VLAN_ID;
            }
            {googleComputeMock.networks().get(TEST_ACCOUNT_NO, TEST_VLAN_ID).execute();
                result = getTestNetworkList().getItems().get(0);
            }
        };
        VLAN vlan = support.createVlan(TEST_DESTINATION_CIDR, TEST_VLAN_ID, "TEST_DESCRIPTION", "TEST_DOMAIN", new String[]{"192.168.1.1"}, new String[]{"192.168.1.1"});
        assertTrue(vlan.getProviderVlanId().equals(TEST_VLAN_ID));
    }

    @Test(expected = OperationNotSupportedException.class)
    public void getRoutingTableForVlan_shouldThrowNotSupportedException() throws CloudException, InternalException {
        support.getRoutingTableForVlan(TEST_VLAN_ID);
    }

    @Test
    public void getVlan_shouldReturnCorrectAttributes() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.networks().get(TEST_ACCOUNT_NO, TEST_VLAN_ID).execute();
                result = getTestNetworkList().getItems().get(0);
            }
        };

        VLAN vlan = support.getVlan(TEST_VLAN_ID);
        assertTrue(vlan.getName().equals(TEST_VLAN_ID));
        assertTrue(vlan.getProviderVlanId().equals(TEST_VLAN_ID));
        assertTrue(vlan.getProviderOwnerId().equals(TEST_ACCOUNT_NO));
        assertTrue(vlan.getTag("contentLink").toString().equals("/networks/"+TEST_VLAN_ID));
        assertTrue(vlan.getCidr().equals(TEST_DESTINATION_CIDR));
        assertTrue(vlan.getDescription().equals("TEST_DESCRIPTION"));
        assertTrue(vlan.getVisibleScope().equals(VisibleScope.ACCOUNT_GLOBAL));
        assertTrue(vlan.getCurrentState().equals(VLANState.AVAILABLE));
        assertTrue(vlan.getSupportedTraffic().length == 1);
        assertTrue(vlan.getSupportedTraffic()[0].equals(IPVersion.IPV4));
    }

    @Test(expected = OperationNotSupportedException.class)
    public void listFirewallIdsForNic_shouldThrowNotSupportedException() throws CloudException, InternalException {
        support.listFirewallIdsForNIC(TEST_NIC_ID);
    }

    @Test(expected = OperationNotSupportedException.class)
    public void getInternetGatewayById_shouldThrowNotSupportedException() throws CloudException, InternalException {
        support.getInternetGatewayById(TEST_GATEWAY);
    }

    @Test(expected = OperationNotSupportedException.class)
    public void getAttachedInternetGatewayId_shouldThrowNotSupportedException() throws CloudException, InternalException {
        support.getAttachedInternetGatewayId(TEST_VLAN_ID);
    }

    @Test(expected = OperationNotSupportedException.class)
    public void removeInternetGatewayById_shouldThrowNotSupportedException() throws CloudException, InternalException {
        support.removeInternetGatewayById(TEST_GATEWAY);
    }

    @Test
    public void listVlanStatus() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.networks().list(TEST_ACCOUNT_NO).execute();
                result = getTestNetworkList();
            }
        };

        Iterable<ResourceStatus> list = support.listVlanStatus();
        List<ResourceStatus> resultAsList = IteratorUtils.toList(list.iterator());
        assertTrue(resultAsList.size() == 1);
        assertTrue(resultAsList.get(0).getResourceStatus().equals(VLANState.AVAILABLE));
    }

    @Test
    public void listVlans() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.networks().list(TEST_ACCOUNT_NO).execute();
                result = getTestNetworkList();
            }
        };

        Iterable<VLAN> list = support.listVlans();
        List<VLAN> resultAsList = IteratorUtils.toList(list.iterator());
        assertTrue(resultAsList.size() == 1);
    }

    @Test
    public void removeVlan() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.networks().get(TEST_ACCOUNT_NO, TEST_VLAN_ID).execute();
                result = getTestNetworkList().getItems().get(0);
            }
            {googleProviderMock.getNetworkServices().getFirewallSupport();
                result = firewallSupport;
            }
            {firewallSupport.getRules(anyString);
                result = Collections.emptyList();
            }
            {googleComputeMock.routes().list(TEST_ACCOUNT_NO).execute();
                result = getTestRouteList();
            }
            {googleComputeMock.routes().delete(TEST_ACCOUNT_NO, "TEST_ROUTE").execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.GLOBAL_OPERATION, "", "");
                result = true;
            }
            {googleComputeMock.networks().delete(TEST_ACCOUNT_NO, TEST_VLAN_ID).execute();
                result = new Operation();
            }
        };

        support.removeVlan(TEST_VLAN_ID);
    }
}
