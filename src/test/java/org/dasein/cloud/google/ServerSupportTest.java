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

import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceAggregatedList;
import com.google.api.services.compute.model.InstancesScopedList;
import com.google.api.services.compute.model.MachineType;
import com.google.api.services.compute.model.MachineTypeAggregatedList;
import com.google.api.services.compute.model.MachineTypeList;
import com.google.api.services.compute.model.MachineTypesScopedList;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.SerialPortOutput;
import mockit.NonStrictExpectations;
import org.apache.commons.collections.IteratorUtils;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.VisibleScope;
import org.dasein.cloud.compute.Architecture;
import org.dasein.cloud.compute.ImageClass;
import org.dasein.cloud.compute.MachineImage;
import org.dasein.cloud.compute.MachineImageState;
import org.dasein.cloud.compute.Platform;
import org.dasein.cloud.compute.VMFilterOptions;
import org.dasein.cloud.compute.VMLaunchOptions;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.compute.VirtualMachineProduct;
import org.dasein.cloud.compute.VmState;
import org.dasein.cloud.compute.Volume;
import org.dasein.cloud.google.compute.server.ServerSupport;
import org.dasein.cloud.network.VLAN;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * User: daniellemayne
 * Date: 16/05/2016
 * Time: 11:53
 */
@RunWith(JUnit4.class)
public class ServerSupportTest extends GoogleTestBase {
    private ServerSupport support = null;

    final String TEST_PRODUCT_ID = "g1-small+"+TEST_DATACENTER;
    final String TEST_MACHINE_IMAGE_ID = "TEST_MACHINE_IMAGE_ID";
    final String TEST_HOST_NAME = "TEST_HOST_NAME";
    final String TEST_FRIENDLY_NAME = "TEST_FRIENDLY_NAME";
    final String TEST_DESCRIPTION = "TEST_DESCRIPTION";
    final String TEST_VLAN_ID = "TEST_VLAN_ID";

    @Before
    public void setUp() throws CloudException, InternalException {
        super.setUp();
        support = new ServerSupport(googleProviderMock);
    }

    private Iterable<VirtualMachine> getTestVirtualMachineList() {
        List<VirtualMachine> list = new ArrayList<VirtualMachine>();
        VirtualMachine vm = new VirtualMachine();
        vm.setProviderVirtualMachineId(TEST_VM_ID);
        vm.setName("name");
        vm.setProviderDataCenterId(TEST_DATACENTER);
        list.add(vm);

        VirtualMachine vm2 = new VirtualMachine();
        vm2.setProviderVirtualMachineId("name2_id2");
        vm2.setName("name2");
        vm.setProviderDataCenterId(TEST_DATACENTER);
        list.add(vm);

        return list;
    }

    private MachineTypeList getTestMachineTypeList() {
        MachineType type = new MachineType();
        type.setName("g1-small");
        type.setZone(TEST_DATACENTER);
        type.setSelfLink("https://www.googleapis.com/compute/v1/projects/qa-project-2/zones/us-central1-a/machineTypes/g1-small");
        type.setGuestCpus(1);
        type.setMemoryMb(1740);

        MachineType type2 = new MachineType();
        type2.setName("g1-medium");
        type2.setZone(TEST_DATACENTER);
        type2.setSelfLink("https://www.googleapis.com/compute/v1/projects/qa-project-2/zones/us-central1-a/machineTypes/g1-medium");
        type2.setGuestCpus(2);
        type2.setMemoryMb(3440);

        List<MachineType> types = new ArrayList<>();
        types.add(type);
        types.add(type2);

        MachineTypeList list = new MachineTypeList();
        list.setItems(types);
        return list;
    }

    private InstanceAggregatedList getTestInstanceAggregatedList() {
        Instance instance = new Instance();
        instance.setName("VMNAME");
        instance.setId(new BigInteger("111122223333"));
        instance.setDescription("VM_DESCRIPTION");
        instance.setStatus("RUNNING");
        instance.setStatusMessage("SUCCESS");
        instance.setZone(TEST_DATACENTER);
        instance.setCreationTimestamp("2016-05-16T12:57:34.123+00:00"); //yyyy-MM-dd'T'HH:mm:ss.SSSZZ
        instance.setMachineType("g1-small");
        instance.setSelfLink("https://www.googleapis.com/compute/v1/projects/qa-project-2/zones/us-central1-a/instances/VM_NAME");
        NetworkInterface net = new NetworkInterface();
        net.setNetwork("TEST_NET");
        List<NetworkInterface> netList = new ArrayList<>();
        netList.add(net);
        instance.setNetworkInterfaces(netList);
        List<Instance> list = new ArrayList<>();
        list.add(instance);


        Instance instance2 = new Instance();
        instance2.setName("VMNAME2");
        instance2.setId(new BigInteger("111122224444"));
        instance2.setDescription("VM_DESCRIPTION2");
        instance2.setStatus("TERMINATED");
        instance2.setStatusMessage("SUCCESS");
        instance2.setZone(TEST_DATACENTER);
        instance2.setCreationTimestamp("2016-05-17T15:57:34.123+00:00"); //yyyy-MM-dd'T'HH:mm:ss.SSSZZ
        instance2.setMachineType("g1-small");
        instance2.setSelfLink("https://www.googleapis.com/compute/v1/projects/qa-project-2/zones/us-central1-a/instances/VM_NAME2");
        NetworkInterface net2 = new NetworkInterface();
        net2.setNetwork("TEST_NET2");
        List<NetworkInterface> netList2 = new ArrayList<>();
        netList2.add(net2);
        instance2.setNetworkInterfaces(netList2);
        list.add(instance2);
        InstancesScopedList isl = new InstancesScopedList();
        isl.setInstances(list);
        Map<String,InstancesScopedList> map = new HashMap<>();
        map.put(TEST_DATACENTER, isl);
        InstanceAggregatedList ial = new InstanceAggregatedList();
        ial.setItems(map);
        return ial;
    }

    private MachineTypeAggregatedList getTestMachineTypeAggregatedList() {
        List<MachineType> list = getTestMachineTypeList().getItems();
        List<MachineType> list2 = new ArrayList<>();

        MachineType type = new MachineType();
        type.setName("g1-small");
        type.setZone("us-central-1b");
        type.setSelfLink("https://www.googleapis.com/compute/v1/projects/qa-project-2/zones/us-central1-b/machineTypes/g1-small");
        type.setGuestCpus(1);
        type.setMemoryMb(1740);
        list2.add(type);

        MachineTypesScopedList mtsl = new MachineTypesScopedList();
        mtsl.setMachineTypes(list);
        MachineTypesScopedList mtsl2 = new MachineTypesScopedList();
        mtsl2.setMachineTypes(list2);

        Map<String, MachineTypesScopedList> map = new HashMap<>();
        map.put(TEST_DATACENTER, mtsl);
        map.put("us-central-1b", mtsl2);

        MachineTypeAggregatedList mtal = new MachineTypeAggregatedList();
        mtal.setItems(map);

        return mtal;
    }

    @Test(expected = OperationNotSupportedException.class)
    public void clone_shouldThrowOperationNotSupportedException() throws CloudException, InternalException {
        support.clone("", "", "", "", true);

    }

    @Test(expected = OperationNotSupportedException.class)
    public void getPassword_shouldThrowOperationNotSupportedException() throws CloudException, InternalException {
        support.getPassword("");
    }

    @Test
    public void getVmNameFromId() throws CloudException, InternalException {
        String name = support.getVmNameFromId("name_id");
        assertTrue(name.equals("name"));
    }

    @Test
    public void getVmIdFromName() throws CloudException, InternalException {
        new NonStrictExpectations(ServerSupport.class) {
            {support.getVirtualMachine(anyString);
                result = getTestVirtualMachineList().iterator().next();
            }
        };
        String id = support.getVmIdFromName("name");
        assertTrue(id.equals(TEST_VM_ID));
    }

    @Test
    public void getConsoleOutput_shouldReturnOutputForCorrectVm() throws CloudException, InternalException, IOException {
        final SerialPortOutput output = new SerialPortOutput();
        output.setContents("content");

        new NonStrictExpectations(ServerSupport.class) {
            {support.listVirtualMachines();
                result = getTestVirtualMachineList();
            }
            {googleComputeMock.instances().getSerialPortOutput(TEST_ACCOUNT_NO, TEST_DATACENTER, "VMNAME").execute();
                result = output;
            }
        };

        String consoleOutput = support.getConsoleOutput(TEST_VM_ID);
        assertTrue(consoleOutput.equals("content"));
    }


    @Test
    public void getProduct_shouldReturnCorrectProduct() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.machineTypes().list(TEST_ACCOUNT_NO, TEST_DATACENTER).setFilter(anyString).execute();
                result = getTestMachineTypeList();
            }
        };
        VirtualMachineProduct product = support.getProduct(TEST_PRODUCT_ID);
        assertNotNull(product);
        assertTrue(product.getProviderProductId().equals(TEST_PRODUCT_ID));
        assertTrue(product.getName().equals("g1-small"));
        assertTrue(product.getDescription().equals("https://www.googleapis.com/compute/v1/projects/qa-project-2/zones/us-central1-a/machineTypes/g1-small"));
        assertTrue(product.getCpuCount() == 1);
        assertTrue(product.getRamSize().intValue() == 1740);
        assertTrue(product.getRootVolumeSize().intValue() == 0);
        assertTrue(product.getVisibleScope().equals(VisibleScope.ACCOUNT_DATACENTER));
    }

    @Test
    public void getVirtualMachine_shouldReturnCorrectVM() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.instances().aggregatedList(TEST_ACCOUNT_NO).setFilter(anyString).execute();
                result = getTestInstanceAggregatedList();
            }
            {googleProviderMock.getDataCenterServices().getRegionFromZone(anyString);
                result = TEST_REGION;
            }
        };

        VirtualMachine vm = support.getVirtualMachine(TEST_VM_ID);
        assertNotNull(vm);
        assertTrue(vm.getProviderVirtualMachineId().equals(TEST_VM_ID));
        assertTrue(vm.getName().equals("VMNAME"));
        assertTrue(vm.getDescription().equals("VM_DESCRIPTION"));
        assertTrue(vm.getProviderOwnerId().equals(TEST_ACCOUNT_NO));
        assertTrue(vm.getCurrentState().equals(VmState.RUNNING));
        assertTrue(vm.getProviderRegionId().equals(TEST_REGION));
        assertTrue(vm.getProviderDataCenterId().equals(TEST_DATACENTER));

        DateTimeFormatter fmt = ISODateTimeFormat.dateTime();
        DateTime dt = DateTime.parse("2016-05-16T12:57:34.123+00:00", fmt);
        assertTrue(vm.getCreationTimestamp() == dt.toDate().getTime());
        assertTrue(vm.getProductId().equals(TEST_PRODUCT_ID));
        assertTrue(vm.getProviderVlanId().equals("TEST_NET"));
    }

    @Test(expected = InternalException.class)
    public void validateLaunchOptions_shouldThrowExceptionWhenDatacenterNotSpecified() throws CloudException, InternalException {
        VMLaunchOptions options = VMLaunchOptions.getInstance(TEST_PRODUCT_ID, TEST_MACHINE_IMAGE_ID, TEST_HOST_NAME, TEST_FRIENDLY_NAME, TEST_DESCRIPTION);
        options = options.inVlan(null, TEST_DATACENTER, TEST_VLAN_ID)
                .inDataCenter(null);

        final VLAN network = new VLAN();
        network.setTag("contentLink", "vlanContentLink");

        new NonStrictExpectations() {
            {googleProviderMock.getNetworkServices().getVlanSupport().getVlan(TEST_VLAN_ID);
                result = network;
            }
            {googleProviderMock.getComputeServices().getVolumeSupport().getVolume(anyString);
                result = null;
            }
        };

        support.validateLaunchOptions(options);
    }

    @Test(expected = InternalException.class)
    public void validateLaunchOptions_shouldThrowExceptionWhenMachineImageNotSpecified() throws CloudException, InternalException {
        VMLaunchOptions options = VMLaunchOptions.getInstance(TEST_PRODUCT_ID, null, TEST_HOST_NAME, TEST_FRIENDLY_NAME, TEST_DESCRIPTION);
        options = options.inVlan(null, TEST_DATACENTER, TEST_VLAN_ID);

        final VLAN network = new VLAN();
        network.setTag("contentLink", "vlanContentLink");

        new NonStrictExpectations() {
            {googleProviderMock.getNetworkServices().getVlanSupport().getVlan(TEST_VLAN_ID);
                result = network;
            }
            {googleProviderMock.getComputeServices().getVolumeSupport().getVolume(anyString);
                result = null;
            }
        };

        support.validateLaunchOptions(options);
    }

    @Test(expected = InternalException.class)
    public void validateLaunchOptions_shouldThrowExceptionWhenHostnameNotSpecified() throws CloudException, InternalException {
        VMLaunchOptions options = VMLaunchOptions.getInstance(TEST_PRODUCT_ID, TEST_MACHINE_IMAGE_ID, null, TEST_FRIENDLY_NAME, TEST_DESCRIPTION);
        options = options.inVlan(null, TEST_DATACENTER, TEST_VLAN_ID);

        final VLAN network = new VLAN();
        network.setTag("contentLink", "vlanContentLink");

        new NonStrictExpectations() {
            {googleProviderMock.getNetworkServices().getVlanSupport().getVlan(TEST_VLAN_ID);
                result = network;
            }
            {googleProviderMock.getComputeServices().getVolumeSupport().getVolume(anyString);
                result = null;
            }
        };

        support.validateLaunchOptions(options);
    }

    @Test(expected = InternalException.class)
    public void validateLaunchOptions_shouldThrowExceptionWhenVlanNotSpecified() throws CloudException, InternalException {
        VMLaunchOptions options = VMLaunchOptions.getInstance(TEST_PRODUCT_ID, TEST_MACHINE_IMAGE_ID, TEST_HOST_NAME, TEST_FRIENDLY_NAME, TEST_DESCRIPTION);
        final VLAN network = new VLAN();
        network.setTag("contentLink", "vlanContentLink");

        new NonStrictExpectations() {
            {googleProviderMock.getNetworkServices().getVlanSupport().getVlan(TEST_VLAN_ID);
                result = network;
            }
            {googleProviderMock.getComputeServices().getVolumeSupport().getVolume(anyString);
                result = null;
            }
        };

        support.validateLaunchOptions(options);
    }

    @Test(expected = InternalException.class)
    public void validateLaunchOptions_shouldThrowExceptionWhenDuplicateHostnameSpecified() throws CloudException, InternalException {
        VMLaunchOptions options = VMLaunchOptions.getInstance(TEST_PRODUCT_ID, TEST_MACHINE_IMAGE_ID, TEST_HOST_NAME, TEST_FRIENDLY_NAME, TEST_DESCRIPTION);
        options = options.inVlan(null, TEST_DATACENTER, TEST_VLAN_ID);

        final VLAN network = new VLAN();
        network.setTag("contentLink", "vlanContentLink");

        new NonStrictExpectations() {
            {googleProviderMock.getNetworkServices().getVlanSupport().getVlan(TEST_VLAN_ID);
                result = network;
            }
            {googleProviderMock.getComputeServices().getVolumeSupport().getVolume(anyString);
                result = new Volume();
            }
        };

        support.validateLaunchOptions(options);
    }

    @Test
    public void launch_shouldReturnVm() throws CloudException, InternalException, IOException {
        VMLaunchOptions options = VMLaunchOptions.getInstance(TEST_PRODUCT_ID, TEST_MACHINE_IMAGE_ID, TEST_HOST_NAME, TEST_FRIENDLY_NAME, TEST_DESCRIPTION);
        options = options.inVlan(null, TEST_DATACENTER, TEST_VLAN_ID);

        final VLAN network = new VLAN();
        network.setTag("contentLink", "vlanContentLink");

        final MachineImage image = MachineImage.getInstance("OWNER", TEST_REGION, TEST_MACHINE_IMAGE_ID, ImageClass.MACHINE, MachineImageState.ACTIVE,
                "IMAGE_NAME", "IMAGE_DESCRIPTION", Architecture.I64, Platform.CENT_OS);
        image.setTag("contentLink", "imageContentLink");

        final Image gceImage = new Image();
        gceImage.setLicenses(Collections.singletonList("LICENSE"));
        gceImage.setDescription("DESCRIPTION");
        gceImage.setName("GCE_IMAGE_NAME");
        gceImage.setDiskSizeGb(10l);

        new NonStrictExpectations(ServerSupport.class) {
            {googleProviderMock.getNetworkServices().getVlanSupport().getVlan(TEST_VLAN_ID);
                result = network;
            }
            {googleProviderMock.getComputeServices().getVolumeSupport().getVolume(anyString);
                result = null;
            }
            {support.getProduct(anyString).getDescription();
                result = TEST_PRODUCT_ID;
            }
            {googleProviderMock.getComputeServices().getImageSupport().getImage(TEST_MACHINE_IMAGE_ID);
                result = image;
            }
            {googleComputeMock.images().get(anyString, anyString).execute();
                result = gceImage;
            }
            {googleComputeMock.instances().insert(TEST_ACCOUNT_NO, TEST_DATACENTER, (Instance)any).execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationTarget(providerContextMock, (Operation) any, GoogleOperationType.ZONE_OPERATION, "", TEST_DATACENTER, false);
                result = TEST_VM_ID;
            }
            {support.getVirtualMachine(TEST_VM_ID);
                result = new VirtualMachine();
            }
        };

        VirtualMachine vm = support.launch(options);
        assertNotNull(vm);
    }

    @Test
    public void listProducts_shouldReturnProductsForSpecifiedDatacenter() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.machineTypes().aggregatedList(TEST_ACCOUNT_NO).execute();
                result = getTestMachineTypeAggregatedList();
            }
        };

        Iterable<VirtualMachineProduct> products = support.listProducts(Architecture.I64, TEST_DATACENTER);
        assertNotNull(products);
        List<VirtualMachineProduct> actualResultAsList = IteratorUtils.toList(products.iterator());
        assertTrue(actualResultAsList.size() == 2);
    }

    @Test
    public void listVirtualMachines_noFilterShouldReturnAllVMs() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.instances().aggregatedList(TEST_ACCOUNT_NO).execute();
                result = getTestInstanceAggregatedList();
            }
            {googleProviderMock.getDataCenterServices().getRegionFromZone(anyString);
                result = TEST_REGION;
            }
        };

        Iterable<VirtualMachine> vms = support.listVirtualMachines(null);
        assertNotNull(vms);
        List<VirtualMachine> resultAsList = IteratorUtils.toList(vms.iterator());
        assertTrue(resultAsList.size() == 2);
    }

    @Test
    public void listVirtualMachines_DCFilterShouldReturn1VM() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.instances().aggregatedList(TEST_ACCOUNT_NO).execute();
                result = getTestInstanceAggregatedList();
            }
            {googleProviderMock.getDataCenterServices().getRegionFromZone(anyString);
                result = TEST_REGION;
            }
        };

        Iterable<VirtualMachine> vms = support.listVirtualMachines(VMFilterOptions.getInstance().matchingRegex("VMNAME2"));
        assertNotNull(vms);
        List<VirtualMachine> resultAsList = IteratorUtils.toList(vms.iterator());
        assertTrue(resultAsList.size() == 1);
    }

    @Test
    public void listVirtualMachines_shouldReturnAllVMs() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.instances().aggregatedList(TEST_ACCOUNT_NO).execute();
                result = getTestInstanceAggregatedList();
            }
            {googleProviderMock.getDataCenterServices().getRegionFromZone(anyString);
                result = TEST_REGION;
            }
        };

        Iterable<VirtualMachine> vms = support.listVirtualMachines();
        assertNotNull(vms);
        List<VirtualMachine> resultAsList = IteratorUtils.toList(vms.iterator());
        assertTrue(resultAsList.size() == 2);
    }

    @Test
    public void listVirtualMachinesStatus_shouldReturnCorrectStatusForEachVM() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.instances().aggregatedList(TEST_ACCOUNT_NO).execute();
                result = getTestInstanceAggregatedList();
            }
            {googleProviderMock.getDataCenterServices().getRegionFromZone(anyString);
                result = TEST_REGION;
            }
        };

        Iterable<ResourceStatus> vms = support.listVirtualMachineStatus();
        assertNotNull(vms);
        List<ResourceStatus> resultAsList = IteratorUtils.toList(vms.iterator());
        assertTrue(resultAsList.size() == 2);
        ResourceStatus rs = resultAsList.get(0);
        assertTrue(rs.getResourceStatus().equals(VmState.RUNNING));
        rs = resultAsList.get(1);
        assertTrue(rs.getResourceStatus().equals(VmState.STOPPED));
    }

    @Test(expected = OperationNotSupportedException.class)
    public void pause_shouldThrowOperationNotSupportedException() throws CloudException, InternalException{
        support.pause(TEST_VM_ID);
    }

    @Test
    public void reboot_shouldCreateASuccessfulOperation() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.instances().reset(TEST_ACCOUNT_NO, TEST_DATACENTER, "VMNAME").execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.ZONE_OPERATION, null, TEST_DATACENTER);
                result = true;
            }
            {googleComputeMock.instances().aggregatedList(TEST_ACCOUNT_NO).execute();
                result = getTestInstanceAggregatedList();
            }
            {googleProviderMock.getDataCenterServices().getRegionFromZone(anyString);
                result = TEST_REGION;
            }
        };

        support.reboot(TEST_VM_ID);
    }

    @Test(expected = OperationNotSupportedException.class)
    public void resume_shouldThrowOperationNotSupportedException() throws CloudException, InternalException{
        support.resume(TEST_VM_ID);
    }

    @Test(expected = OperationNotSupportedException.class)
    public void suspend_shouldThrowOperationNotSupportedException() throws CloudException, InternalException{
        support.suspend(TEST_VM_ID);
    }

    @Test
    public void start() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.instances().start(TEST_ACCOUNT_NO, TEST_DATACENTER, "VMNAME").execute();
                result = new Operation();
            }
            {googleComputeMock.instances().aggregatedList(TEST_ACCOUNT_NO).setFilter(anyString).execute();
                result = getTestInstanceAggregatedList();
            }
            {googleProviderMock.getDataCenterServices().getRegionFromZone(anyString);
                result = TEST_REGION;
            }
        };
        support.start(TEST_VM_ID);
    }

    @Test
    public void stop() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.instances().stop(TEST_ACCOUNT_NO, TEST_DATACENTER, "VMNAME").execute();
                result = new Operation();
            }
            {googleComputeMock.instances().aggregatedList(TEST_ACCOUNT_NO).setFilter(anyString).execute();
                result = getTestInstanceAggregatedList();
            }
            {googleProviderMock.getDataCenterServices().getRegionFromZone(anyString);
                result = TEST_REGION;
            }
        };
        support.stop(TEST_VM_ID, true);
    }

    @Test
    public void terminate() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.instances().aggregatedList(TEST_ACCOUNT_NO).setFilter(anyString).execute();
                result = getTestInstanceAggregatedList();
            }
            {googleProviderMock.getDataCenterServices().getRegionFromZone(anyString);
                result = TEST_REGION;
            }
            {googleComputeMock.instances().delete(TEST_ACCOUNT_NO, TEST_DATACENTER, "VMNAME").execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.ZONE_OPERATION, null, TEST_DATACENTER);
                result = true;
            }
            {googleComputeMock.disks().delete(TEST_ACCOUNT_NO, TEST_DATACENTER, anyString).execute();
                result = new Operation();
            }
        };
        support.terminate(TEST_VM_ID, "terminate test");
    }

    @Test(expected = OperationNotSupportedException.class)
    public void unpause_shouldThrowOperationNotSupportedException() throws CloudException, InternalException {
        support.unpause(TEST_VM_ID);
    }
}

