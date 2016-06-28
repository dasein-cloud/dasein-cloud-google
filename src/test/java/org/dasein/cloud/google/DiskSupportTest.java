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

import com.google.api.services.compute.model.AttachedDisk;
import com.google.api.services.compute.model.Disk;
import com.google.api.services.compute.model.DiskAggregatedList;
import com.google.api.services.compute.model.DisksScopedList;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceList;
import com.google.api.services.compute.model.Operation;
import mockit.NonStrictExpectations;
import org.apache.commons.collections.IteratorUtils;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.InvalidStateException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ResourceNotFoundException;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.compute.Volume;
import org.dasein.cloud.compute.VolumeCreateOptions;
import org.dasein.cloud.compute.VolumeFilterOptions;
import org.dasein.cloud.compute.VolumeFormat;
import org.dasein.cloud.compute.VolumeState;
import org.dasein.cloud.compute.VolumeType;
import org.dasein.cloud.google.compute.server.DiskSupport;
import org.dasein.util.uom.storage.Storage;
import org.dasein.util.uom.storage.StorageUnit;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * User: daniellemayne
 * Date: 19/05/2016
 * Time: 12:41
 */
@RunWith(JUnit4.class)
public class DiskSupportTest extends GoogleTestBase {
    private DiskSupport support  = null;
    final String TEST_VOLUME_ID = "TEST_VOLUME";

    private DiskAggregatedList getTestDiskAggregatedList() {
        Disk disk = new Disk();
        disk.setName(TEST_VOLUME_ID);
        disk.setDescription("DESCRIPTION");
        disk.setSelfLink("SELFLINK");
        disk.setZone(TEST_DATACENTER);
        disk.setCreationTimestamp("2016-05-23T09:57:34.123+00:00"); //yyyy-MM-dd'T'HH:mm:ss.SSSZZ
        disk.setStatus("READY");
        disk.setSizeGb(2l);

        Disk disk2 = new Disk();
        disk2.setName("VOLUMENAME2");
        disk2.setDescription("DESCRIPTION2");
        disk2.setSelfLink("SELFLINK2");
        disk2.setZone(TEST_DATACENTER);
        disk2.setCreationTimestamp("2016-05-23T12:57:34.123+00:00"); //yyyy-MM-dd'T'HH:mm:ss.SSSZZ
        disk2.setStatus("FAILED");
        disk2.setSizeGb(3l);

        List<Disk> list = new ArrayList<>();
        list.add(disk);
        list.add(disk2);

        DisksScopedList dsl = new DisksScopedList();
        dsl.setDisks(list);

        DiskAggregatedList dal = new DiskAggregatedList();
        Map<String, DisksScopedList> map = new HashMap<>();
        map.put(TEST_DATACENTER, dsl);
        dal.setItems(map);
        return dal;
    }

    private InstanceList getTestInstanceList() {
        Instance instance = new Instance();
        instance.setName("VMNAME");
        instance.setId(new BigInteger("111122223333"));

        AttachedDisk disk = new AttachedDisk();
        disk.setSource("SELFLINK");
        disk.setDeviceName("1");
        List<AttachedDisk> list = new ArrayList<>();
        list.add(disk);
        instance.setDisks(list);

        List<Instance> instanceList = new ArrayList<>();
        instanceList.add(instance);
        InstanceList il = new InstanceList();
        il.setItems(instanceList);
        return il;
    }

    @Before
    public void setUp() throws CloudException, InternalException {
        super.setUp();
        support = new DiskSupport(googleProviderMock);
    }

    @Test
    public void attach_shouldHaveSuccessfulOperationComplete() throws CloudException, InternalException, IOException {
        final VirtualMachine vm = new VirtualMachine();
        vm.setProviderVirtualMachineId(TEST_VM_ID);
        vm.setName("name");
        vm.setProviderDataCenterId(TEST_DATACENTER);

        final Volume volume = new Volume();
        volume.setTag("contentLink", "CONTENT_LINK");

        new NonStrictExpectations(DiskSupport.class) {
            {googleProviderMock.getComputeServices().getVirtualMachineSupport().getVmNameFromId(TEST_VM_ID);
                result = "VMNAME";
            }
            {googleProviderMock.getComputeServices().getVirtualMachineSupport().getVirtualMachine("VMNAME");
                result = vm;
            }
            {support.getVolume(TEST_VOLUME_ID);
                result = volume;
            }
            {googleComputeMock.instances().attachDisk(TEST_ACCOUNT_NO, TEST_DATACENTER, TEST_VM_ID, (AttachedDisk) any).execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.ZONE_OPERATION, "", TEST_DATACENTER);
                result = true;
            }
        };

        support.attach(TEST_VOLUME_ID, TEST_VM_ID, "0");
    }

    @Test(expected = ResourceNotFoundException.class)
    public void attach_shouldThrowExceptionIfVmNotFound() throws CloudException, InternalException {
        new NonStrictExpectations() {
            {googleProviderMock.getComputeServices().getVirtualMachineSupport().getVmNameFromId(TEST_VM_ID);
                result = "VMNAME";
            }
            {googleProviderMock.getComputeServices().getVirtualMachineSupport().getVirtualMachine(anyString);
                result = null;
            }
        };

        support.attach(TEST_VOLUME_ID, TEST_VM_ID, "0");
    }

    @Test
    public void createVolume_shouldReturnNewVolumeId() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.disks().insert(TEST_ACCOUNT_NO, TEST_DATACENTER, (Disk) any).execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationTarget(providerContextMock, (Operation) any, GoogleOperationType.ZONE_OPERATION, "", TEST_DATACENTER, false);
                result = TEST_VOLUME_ID;
            }
        };

        VolumeCreateOptions options = VolumeCreateOptions.getInstance(new Storage<StorageUnit>(2, Storage.GIGABYTE), "myNewDisk", "myNewDiskDescription");
        options.inDataCenter(TEST_DATACENTER);

        String volumeId = support.createVolume(options);
        assertNotNull(volumeId);
        assertTrue(volumeId.equals(TEST_VOLUME_ID));
    }

    @Test(expected = OperationNotSupportedException.class)
    public void createVolume_shouldThrowOperationNotSupportedExceptionIfVolumeFormatIsNFS() throws CloudException, InternalException {
        VolumeCreateOptions options = VolumeCreateOptions.getInstance(new Storage<StorageUnit>(2, Storage.GIGABYTE), "myNewDisk", "myNewDiskDescription");
        options.inDataCenter(TEST_DATACENTER);
        options.setFormat(VolumeFormat.NFS);

        support.createVolume(options);
    }

    @Test
    public void detach_shouldResultInSuccessfulOperation() throws CloudException, InternalException, IOException {
        final Volume volume = new Volume();
        volume.setTag("contentLink", "CONTENT_LINK");
        volume.setProviderDataCenterId(TEST_DATACENTER);
        volume.setProviderVirtualMachineId(TEST_VM_ID);
        volume.setDeviceId("1");

        new NonStrictExpectations(DiskSupport.class) {
            {googleProviderMock.getComputeServices().getVirtualMachineSupport().getVmNameFromId(TEST_VM_ID);
                result = "VMNAME";
            }
            {support.getVolume(TEST_VOLUME_ID);
                result = volume;
            }
            {googleComputeMock.instances().detachDisk(TEST_ACCOUNT_NO, TEST_DATACENTER, TEST_VM_ID, volume.getDeviceId()).execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.ZONE_OPERATION, "", TEST_DATACENTER);
                result = true;
            }
        };

        support.detach(TEST_VOLUME_ID, false);
    }

    @Test(expected = InvalidStateException.class)
    public void detach_shouldThrowExceptionIfVolumeIsNotAttached() throws CloudException, InternalException {
        final Volume volume = new Volume();
        volume.setTag("contentLink", "CONTENT_LINK");
        volume.setProviderDataCenterId(TEST_DATACENTER);
        volume.setDeviceId("1");

        new NonStrictExpectations(DiskSupport.class) {
            {support.getVolume(TEST_VOLUME_ID);
                result = volume;
            }
        };

        support.detach(TEST_VOLUME_ID, true);
    }

    @Test
    public void getVolume_shouldReturnTheCorrectAttributes() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.disks().aggregatedList(TEST_ACCOUNT_NO).setFilter(anyString).execute();
                result = getTestDiskAggregatedList();
            }
            {googleProviderMock.getDataCenterServices().getRegionFromZone(TEST_DATACENTER);
                result = TEST_REGION;
            }
            {googleComputeMock.instances().list(TEST_ACCOUNT_NO, TEST_DATACENTER).execute();
                result = getTestInstanceList();
            }
        };

        Volume volume = support.getVolume(TEST_VOLUME_ID);
        assertNotNull(volume);
        assertTrue(volume.getProviderVolumeId().equals(TEST_VOLUME_ID));
        assertTrue(volume.getName().equals(TEST_VOLUME_ID));
        assertTrue(volume.getMediaLink().equals("SELFLINK"));
        assertTrue(volume.getDescription().equals("DESCRIPTION"));
        assertTrue(volume.getProviderRegionId().equals(TEST_REGION));

        DateTimeFormatter fmt = ISODateTimeFormat.dateTime();
        DateTime dt = DateTime.parse("2016-05-23T09:57:34.123+00:00", fmt);
        assertTrue(volume.getCreationTimestamp() == dt.toDate().getTime());
        assertTrue(volume.getProviderDataCenterId().equals(TEST_DATACENTER));
        assertTrue(volume.getCurrentState().equals(VolumeState.AVAILABLE));
        assertTrue(volume.getType().equals(VolumeType.HDD));
        assertTrue(volume.getFormat().equals(VolumeFormat.BLOCK));
        assertTrue(volume.getSizeInGigabytes() == 2);
        assertTrue(volume.getTag("contentLink").equals("SELFLINK"));
        assertTrue(volume.getDeviceId().equals("1"));
        assertTrue(volume.getProviderVirtualMachineId().equals(TEST_VM_ID));
    }

    @Test
    public void listVolumeStatus_shouldReturnCorrectStatus() throws CloudException, InternalException, IOException {
        new NonStrictExpectations(){
            {googleComputeMock.disks().aggregatedList(TEST_ACCOUNT_NO).execute();
                result = getTestDiskAggregatedList();
            }
        };

        Iterable<ResourceStatus> list = support.listVolumeStatus();
        assertNotNull(list);
        List<ResourceStatus> resultAsList = IteratorUtils.toList(list.iterator());
        assertTrue(resultAsList.size() == 2);
        ResourceStatus rs = resultAsList.get(0);
        assertTrue(rs.getResourceStatus().equals(VolumeState.AVAILABLE));
        rs = resultAsList.get(1);
        assertTrue(rs.getResourceStatus().equals(VolumeState.ERROR));
    }

    @Test
    public void listVolumes_shouldReturnAllAvailableVolumes() throws CloudException, InternalException, IOException {
        new NonStrictExpectations(){
            {googleComputeMock.disks().aggregatedList(TEST_ACCOUNT_NO).execute();
                result = getTestDiskAggregatedList();
            }
            {googleProviderMock.getDataCenterServices().getRegionFromZone(TEST_DATACENTER);
                result = TEST_REGION;
            }
            {googleComputeMock.instances().list(TEST_ACCOUNT_NO, TEST_DATACENTER).execute();
                result = getTestInstanceList();
            }
        };

        Iterable<Volume> list = support.listVolumes();
        assertNotNull(list);
        List<Volume> resultAsList = IteratorUtils.toList(list.iterator());
        assertTrue(resultAsList.size() == 2);
    }

    @Test
    public void listVolumesWithVMFilter_shouldReturnNoVolumes() throws CloudException, InternalException, IOException {
        new NonStrictExpectations(){
            {googleComputeMock.disks().aggregatedList(TEST_ACCOUNT_NO).execute();
                result = getTestDiskAggregatedList();
            }
            {googleProviderMock.getDataCenterServices().getRegionFromZone(TEST_DATACENTER);
                result = TEST_REGION;
            }
            {googleComputeMock.instances().list(TEST_ACCOUNT_NO, TEST_DATACENTER).execute();
                result = getTestInstanceList();
            }
        };

        VolumeFilterOptions options = VolumeFilterOptions.getInstance().attachedTo("myFakeVMId");

        Iterable<Volume> list = support.listVolumes(options);
        assertNotNull(list);
        List<Volume> resultAsList = IteratorUtils.toList(list.iterator());
        assertTrue(resultAsList.size() == 0);
    }

    @Test
    public void remove_shouldResultInSuccessfulOperation() throws CloudException, InternalException, IOException {
        final Volume volume = new Volume();
        volume.setProviderVolumeId(TEST_VOLUME_ID);
        volume.setProviderDataCenterId(TEST_DATACENTER);

        new NonStrictExpectations(DiskSupport.class) {
            {support.getVolume(TEST_VOLUME_ID);
                result = volume;
            }
            {googleComputeMock.disks().delete(TEST_ACCOUNT_NO, TEST_DATACENTER, TEST_VOLUME_ID).execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.ZONE_OPERATION, "", TEST_DATACENTER);
                result = true;
            }
        };

        support.remove(TEST_VOLUME_ID);
    }
}