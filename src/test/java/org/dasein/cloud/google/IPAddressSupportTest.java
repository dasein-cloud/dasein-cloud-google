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

import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.Address;
import com.google.api.services.compute.model.AddressAggregatedList;
import com.google.api.services.compute.model.AddressList;
import com.google.api.services.compute.model.AddressesScopedList;
import com.google.api.services.compute.model.Operation;
import mockit.Mocked;
import mockit.NonStrictExpectations;
import org.apache.commons.collections.IteratorUtils;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ResourceNotFoundException;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.google.compute.server.ServerSupport;
import org.dasein.cloud.google.network.IPAddressSupport;
import org.dasein.cloud.network.AddressType;
import org.dasein.cloud.network.IPVersion;
import org.dasein.cloud.network.IpAddress;
import org.dasein.cloud.network.Protocol;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * User: daniellemayne
 * Date: 02/06/2016
 * Time: 11:34
 */
public class IPAddressSupportTest extends GoogleTestBase {
    private IPAddressSupport support = null;
    static String TEST_IP_ADDRESS = "192.168.101.1";
    static String TEST_IP_ADDRESS_ID = "TEST_IP_ADDRESS_ID";

    @Mocked
    ServerSupport serverSupport;

    @Before
    public void setUp() throws CloudException, InternalException {
        super.setUp();
        support = new IPAddressSupport(googleProviderMock);
    }

    private AddressAggregatedList getTestAddressAggregatedList() {
        Address address = new Address();
        address.setName(TEST_IP_ADDRESS_ID);
        address.setAddress(TEST_IP_ADDRESS);
        address.setRegion("/"+TEST_REGION);
        List<String> users = new ArrayList<>();
        users.add("/instances/"+TEST_VM_ID);
        users.add("/instances/vm2");
        address.setUsers(users);
        address.setStatus("RESERVED");

        AddressesScopedList addressesScopedList = new AddressesScopedList();
        addressesScopedList.setAddresses(Collections.singletonList(address));

        AddressAggregatedList agl = new AddressAggregatedList();
        Map<String, AddressesScopedList> map = new HashMap<>();
        map.put(TEST_REGION, addressesScopedList);
        agl.setItems(map);
        return agl;

    }

    @Test
    public void assign_shouldResultInSuccessfulOperation() throws CloudException, InternalException, IOException {
        final IpAddress ipAddress = new IpAddress();
        ipAddress.setAddress(TEST_IP_ADDRESS);
        ipAddress.setVersion(IPVersion.IPV4);

        final VirtualMachine vm = new VirtualMachine();
        vm.setName("VMNAME");
        vm.setProviderDataCenterId(TEST_DATACENTER);

        new NonStrictExpectations(IPAddressSupport.class) {
            {support.getIpAddress(anyString);
                result = ipAddress;
            }
            {googleProviderMock.getComputeServices().getVirtualMachineSupport().getVirtualMachine(anyString);
                result = vm;
            }
            {googleComputeMock.instances().deleteAccessConfig(TEST_ACCOUNT_NO, TEST_DATACENTER, "VMNAME", "External NAT", "nic0").execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.ZONE_OPERATION, "", TEST_DATACENTER);
                result = true;
            }
            {googleComputeMock.instances().addAccessConfig(TEST_ACCOUNT_NO, TEST_DATACENTER, "VMNAME", "nic0", (AccessConfig) any).execute();
                result = new Operation();
            }
        };

        support.assign(TEST_IP_ADDRESS, TEST_VM_ID);
    }

    @Test(expected = OperationNotSupportedException.class)
    public void assignToNetworkInterface_shouldThrowOperationNotSupportedException() throws CloudException, InternalException {
        support.assignToNetworkInterface(TEST_IP_ADDRESS, TEST_VM_ID);
    }

    @Test(expected = OperationNotSupportedException.class)
    public void forward_shouldThrowOperationNotSupportedException() throws CloudException, InternalException {
        support.forward(TEST_IP_ADDRESS, 22, Protocol.TCP, 22, TEST_VM_ID);
    }

    @Test
    public void getIpAddress_shouldReturnObjectWithCorrectAttributes() throws CloudException, InternalException, IOException {
        final VirtualMachine vm = new VirtualMachine();
        vm.setProviderVirtualMachineId(TEST_VM_ID);

        new NonStrictExpectations() {
            {googleComputeMock.addresses().aggregatedList(TEST_ACCOUNT_NO).setFilter(anyString).execute();
                result = getTestAddressAggregatedList();
            }
            {googleProviderMock.getComputeServices().getVirtualMachineSupport();
                result = serverSupport;
            }
            {serverSupport.getVirtualMachine(TEST_VM_ID);
                result = vm;
            }
        };
        IpAddress address = support.getIpAddress(TEST_IP_ADDRESS_ID);
        assertNotNull(address);
        assertTrue(address.getProviderIpAddressId().equals(TEST_IP_ADDRESS_ID));
        assertTrue(address.getRawAddress().getIpAddress().equals(TEST_IP_ADDRESS));
        assertTrue(address.getRegionId().equals(TEST_REGION));
        assertTrue(address.getAddressType().equals(AddressType.PUBLIC));
        assertTrue(address.getVersion().equals(IPVersion.IPV4));
        assertTrue(!address.isForVlan());
        assertTrue(address.getServerId().equals(TEST_VM_ID));
    }

    @Test
    public void getIpAddressIpFromIP_shouldReturnCorrectName() throws CloudException, InternalException, IOException {
        final AddressList list = new AddressList();
        list.setItems(getTestAddressAggregatedList().getItems().get(TEST_REGION).getAddresses());

        new NonStrictExpectations() {
            {googleComputeMock.addresses().list(TEST_ACCOUNT_NO, TEST_REGION).execute();
                result = list;
            }
        };

        String name = support.getIpAddressIdFromIP(TEST_IP_ADDRESS, TEST_REGION);
        assertTrue(name.equals(TEST_IP_ADDRESS_ID));
    }

    @Test(expected = ResourceNotFoundException.class)
    public void getIpAddressFromIP_shouldThrowExceptionIfCloudReturnsNullList() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.addresses().list(TEST_ACCOUNT_NO, TEST_REGION).execute();
                result = null;
            }
        };
        support.getIpAddressIdFromIP(TEST_IP_ADDRESS, TEST_REGION);
    }

    @Test
    public void listIpPool_shouldReturnEmptyListIfVersionIsNotIPv4() throws CloudException, InternalException {
        Iterable<IpAddress> list = support.listIpPool(IPVersion.IPV6, false);
        assertNotNull(list);
        List<IpAddress> resultAsList = IteratorUtils.toList(list.iterator());
        assertTrue(resultAsList.size() == 0);
    }

    @Test
    public void listIpPool_shouldReturnAllAvailableIpAddresses() throws CloudException, InternalException, IOException {
        final AddressList list = new AddressList();
        list.setItems(getTestAddressAggregatedList().getItems().get(TEST_REGION).getAddresses());

        new NonStrictExpectations() {
            {googleComputeMock.addresses().list(TEST_ACCOUNT_NO, TEST_REGION).execute();
                result = list;
            }
        };

        Iterable<IpAddress> pool = support.listIpPool(IPVersion.IPV4, false);
        assertNotNull(list);
        List<IpAddress> resultAsList = IteratorUtils.toList(pool.iterator());
        assertTrue(resultAsList.size() == 1);
    }

    @Test
    public void listIpPoolUnassignedOnly_shoudlReturnEmptyList() throws CloudException, InternalException, IOException {
        final AddressList list = new AddressList();
        list.setItems(getTestAddressAggregatedList().getItems().get(TEST_REGION).getAddresses());

        final VirtualMachine vm = new VirtualMachine();
        vm.setProviderVirtualMachineId(TEST_VM_ID);

        new NonStrictExpectations() {
            {googleComputeMock.addresses().list(TEST_ACCOUNT_NO, TEST_REGION).execute();
                result = list;
            }
            {googleProviderMock.getComputeServices().getVirtualMachineSupport();
                result = serverSupport;
            }
            {serverSupport.getVirtualMachine(TEST_VM_ID);
                result = vm;
            }
        };

        Iterable<IpAddress> pool = support.listIpPool(IPVersion.IPV4, true);
        assertNotNull(pool);
        List<IpAddress> resultAsList = IteratorUtils.toList(pool.iterator());
        assertTrue(resultAsList.size() == 0);
    }

    @Test
    public void listIpPoolStatus_shouldReturnCorrectStatus() throws CloudException, InternalException, IOException {
        final VirtualMachine vm = new VirtualMachine();
        vm.setProviderVirtualMachineId(TEST_VM_ID);

        new NonStrictExpectations() {
            {googleComputeMock.addresses().aggregatedList(TEST_ACCOUNT_NO).execute();
                result = getTestAddressAggregatedList();
            }
            {googleProviderMock.getComputeServices().getVirtualMachineSupport();
                result = serverSupport;
            }
            {serverSupport.getVirtualMachine(TEST_VM_ID);
                result = vm;
            }
        };

        Iterable<ResourceStatus> list = support.listIpPoolStatus(IPVersion.IPV4);
        assertNotNull(list);
        List<ResourceStatus> resultAsList = IteratorUtils.toList(list.iterator());
        assertTrue(resultAsList.size() == 1);
        assertTrue(resultAsList.get(0).getResourceStatus().equals(false));
    }

    @Test
    public void listIpPoolStatus_shouldReturnEmptyListIfNotIPv4() throws CloudException, InternalException, IOException {

        Iterable<ResourceStatus> list = support.listIpPoolStatus(IPVersion.IPV6);
        assertNotNull(list);
        List<ResourceStatus> resultAsList = IteratorUtils.toList(list.iterator());
        assertTrue(resultAsList.size() == 0);
    }

    @Test
    public void releaseFromPool_shouldResultInSuccessfulOperation() throws CloudException, InternalException, IOException {
        final IpAddress address = new IpAddress();
        address.setRegionId(TEST_REGION);

        new NonStrictExpectations(IPAddressSupport.class) {
            {support.getIpAddress(TEST_IP_ADDRESS_ID);
                result = address;
            }
            {googleComputeMock.addresses().delete(TEST_ACCOUNT_NO, TEST_REGION, TEST_IP_ADDRESS_ID).execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.REGION_OPERATION, TEST_REGION, "");
                result = true;
            }
        };

        support.releaseFromPool(TEST_IP_ADDRESS_ID);
    }

    @Test
    public void releaseFromServer_shouldResultInSuccessfulOperation() throws CloudException, InternalException, IOException {
        final Address address = new Address();
        address.setName(TEST_IP_ADDRESS_ID);
        address.setAddress(TEST_IP_ADDRESS);
        address.setRegion("/"+TEST_REGION);
        List<String> users = new ArrayList<>();
        users.add("/zones/"+TEST_DATACENTER+"/instances/"+TEST_VM_ID);
        users.add("/zones/"+TEST_DATACENTER+"/instances/vm2");
        address.setUsers(users);
        address.setStatus("RESERVED");

        new NonStrictExpectations() {
            {googleComputeMock.addresses().get(TEST_ACCOUNT_NO, TEST_REGION, TEST_IP_ADDRESS_ID).execute();
                result = address;
            }
            {googleComputeMock.instances().deleteAccessConfig(TEST_ACCOUNT_NO, TEST_DATACENTER, TEST_VM_ID, "External NAT", "nic0").execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.ZONE_OPERATION, "", TEST_DATACENTER);
                result = true;
            }
        };
        support.releaseFromServer(TEST_IP_ADDRESS_ID);
    }

    @Test
    public void request_shouldReturnNewIpAddressId() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.addresses().insert(TEST_ACCOUNT_NO, TEST_REGION, (Address) any).execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationTarget(providerContextMock, (Operation) any, GoogleOperationType.REGION_OPERATION, TEST_REGION, "", false);
                result = "new_ip_address_id";
            }
        };

        String id = support.request(IPVersion.IPV4);
        assertTrue(id.equals("new_ip_address_id"));
    }

    @Test(expected = OperationNotSupportedException.class)
    public void request_shouldThrowExceptionIfNotIPv4() throws CloudException, InternalException {
        support.request(IPVersion.IPV6);
    }

    @Test(expected = OperationNotSupportedException.class)
    public void requestForVlan_shouldThrowOperationNotSupportedException() throws CloudException, InternalException {
        support.requestForVLAN(IPVersion.IPV6);
    }

    @Test(expected = OperationNotSupportedException.class)
    public void requestForVlanWithVlanId_shouldThrowOperationNotSupportedException() throws CloudException, InternalException {
        support.requestForVLAN(IPVersion.IPV4, "VLAN1");
    }

    @Test(expected = OperationNotSupportedException.class)
    public void stopForward_shouldThrowOperationNotSupportedException() throws CloudException, InternalException {
        support.stopForward("RULE");
    }
}
