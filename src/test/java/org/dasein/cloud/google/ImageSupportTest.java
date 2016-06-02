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

import com.google.api.services.compute.model.Disk;
import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.ImageList;
import com.google.api.services.compute.model.Operation;
import mockit.Mocked;
import mockit.NonStrictExpectations;
import org.apache.commons.collections.IteratorUtils;
import org.dasein.cloud.AsynchronousTask;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.VisibleScope;
import org.dasein.cloud.compute.Architecture;
import org.dasein.cloud.compute.ImageClass;
import org.dasein.cloud.compute.ImageCreateOptions;
import org.dasein.cloud.compute.ImageFilterOptions;
import org.dasein.cloud.compute.MachineImage;
import org.dasein.cloud.compute.MachineImageFormat;
import org.dasein.cloud.compute.MachineImageState;
import org.dasein.cloud.compute.Platform;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.google.compute.server.ImageSupport;
import org.dasein.cloud.google.compute.server.ServerSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * User: daniellemayne
 * Date: 23/05/2016
 * Time: 14:24
 */
@RunWith(JUnit4.class)
public class ImageSupportTest extends GoogleTestBase {
    private ImageSupport support = null;

    @Mocked
    ServerSupport serverSupport;

    final String TEST_IMAGE_ID = "MYPROJ_TESTIMAGE";

    @Before
    public void setUp() throws CloudException, InternalException {
        super.setUp();
        support = new ImageSupport(googleProviderMock);
    }

    private ImageList getTestImageList() {
        Image image = new Image();
        image.setStatus("READY");
        image.setName("TESTIMAGE");
        image.setDescription("CENTOS");
        image.setSelfLink("/projects/12323232323/global/images/TESTIMAGE");
        image.set("diskSizeGb", new Long(2));

        Image image2 = new Image();
        image2.setStatus("PENDING");
        image2.setName("TESTIMAGE2");
        image2.setDescription("windows");
        image2.setSelfLink("/projects/MYPROJ/global/images/TESTIMAGE2");
        image2.set("diskSizeGb", new Long(4));

        List<Image> list = new ArrayList<>();
        list.add(image);
        list.add(image2);

        ImageList il = new ImageList();
        il.setItems(list);

        return il;
    }

    private ImageList getTestPublicImageList() {
        Image image = new Image();
        image.setStatus("READY");
        image.setName("TESTPUBLICIMAGE");
        image.setDescription("CENTOS");
        image.setSelfLink("/projects/centos-cloud/global/images/TESTCENTOSIMAGE");
        image.set("diskSizeGb", new Long(2));

        List<Image> list = new ArrayList<>();
        list.add(image);

        ImageList il = new ImageList();
        il.setItems(list);

        return il;
    }

    @Test(expected = OperationNotSupportedException.class)
    public void addImageShare_shouldThrowOperationNotSupportedException() throws CloudException, InternalException {
        support.addImageShare(TEST_IMAGE_ID, TEST_ACCOUNT_NO);
    }

    @Test(expected = OperationNotSupportedException.class)
    public void addPublicShare_shouldThrowOperationNotSupportedException() throws CloudException, InternalException {
        support.addPublicShare(TEST_IMAGE_ID);
    }

    @Test(expected = OperationNotSupportedException.class)
    public void bundleVirtualMachine_shouldThrowOperationNotSupportedException() throws CloudException, InternalException {
        support.bundleVirtualMachine(TEST_VM_ID, MachineImageFormat.OVF, "", "");
    }

    @Test(expected = OperationNotSupportedException.class)
    public void bundleVirtualMachineAsync_shouldThrowOperationNotSupportedException() throws CloudException, InternalException {
        support.bundleVirtualMachineAsync(TEST_VM_ID, MachineImageFormat.OVF, "", "", new AsynchronousTask<String>());
    }

    @Test
    public void getImage_shouldReturnCorrectAttributes() throws CloudException, InternalException, IOException {
        final Image image = new Image();
        image.setStatus("READY");
        image.setName("TESTIMAGE");
        image.setDescription("CENTOS");
        image.setSelfLink("/projects/MYPROJ/global/images/TESTIMAGE");
        image.set("diskSizeGb", new Long(2));

        new NonStrictExpectations() {
            {googleComputeMock.images().get("MYPROJ", "TESTIMAGE").execute();
                result = image;
            }
        };

        MachineImage img = support.getImage(TEST_IMAGE_ID);
        assertNotNull(img);
        assertTrue(img.getName().equals("TESTIMAGE"));
        assertTrue(img.getDescription().equals("CENTOS"));
        assertTrue(img.getProviderMachineImageId().equals(TEST_IMAGE_ID));
        assertTrue(img.getProviderOwnerId().equals("GCE"));
        assertTrue(img.getProviderRegionId().equals(""));
        assertTrue(img.getImageClass().equals(ImageClass.MACHINE));
        assertTrue(img.getCurrentState().equals(MachineImageState.ACTIVE));
        assertTrue(img.getArchitecture().equals(Architecture.I64));
        assertTrue(img.getPlatform().equals(Platform.CENT_OS));
        assertTrue(img.getStorageFormat().equals(MachineImageFormat.RAW));
        assertTrue(img.getVisibleScope().equals(VisibleScope.ACCOUNT_GLOBAL));
        assertTrue(img.isPublic());
        assertTrue(img.getTag("contentLink").toString().equals("/projects/MYPROJ/global/images/TESTIMAGE"));
        assertTrue(img.getTag("project").toString().equals("MYPROJ"));
        assertTrue(img.getMinimumDiskSizeGb() == 2);
    }

    @Test(expected = InternalException.class)
    public void getImage_shouldThrowExceptionIfImageIdDoesNotContain_() throws CloudException, InternalException {
        support.getImage("IMAGE");
    }

    @Test
    public void listImageStatus_shouldReturnCorrectStatusOfEachImage() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.images().list(TEST_ACCOUNT_NO).execute();
                result = getTestImageList();
            }
        };

        Iterable<ResourceStatus> list = support.listImageStatus(ImageClass.MACHINE);
        assertNotNull(list);
        List<ResourceStatus> resultAsList = IteratorUtils.toList(list.iterator());
        assertTrue(resultAsList.size() == 2);
        ResourceStatus status = resultAsList.get(0);
        assertTrue(status.getResourceStatus().equals(MachineImageState.ACTIVE));
        status = resultAsList.get(1);
        assertTrue(status.getResourceStatus().equals(MachineImageState.PENDING));
    }

    @Test
    public void listImagesNullFilter_shouldReturnAllAvailableImages() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.images().list(TEST_ACCOUNT_NO).execute();
                result = getTestImageList();
            }
        };

        Iterable<MachineImage> list = support.listImages((ImageFilterOptions) null);
        assertNotNull(list);
        List<MachineImage> resultAsList = IteratorUtils.toList(list.iterator());
        assertTrue(resultAsList.size() == 2);
    }

    @Test
    public void listImagesPlatformFilter_shouldReturnOnlyCentosImage() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.images().list(TEST_ACCOUNT_NO).execute();
                result = getTestImageList();
            }
        };

        Iterable<MachineImage> list = support.listImages(ImageFilterOptions.getInstance().onPlatform(Platform.CENT_OS));
        assertNotNull(list);
        List<MachineImage> resultAsList = IteratorUtils.toList(list.iterator());
        assertTrue(resultAsList.size() == 1);
    }

    @Test
    public void listMachineImages_shouldReturnAllAvailableImages() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.images().list(TEST_ACCOUNT_NO).execute();
                result = getTestImageList();
            }
        };

        Iterable<MachineImage> list = support.listMachineImages();
        assertNotNull(list);
        List<MachineImage> resultAsList = IteratorUtils.toList(list.iterator());
        assertTrue(resultAsList.size() == 2);
    }

    @Test
    public void listMachineImagesOwnedBy_shouldReturnOnlyPrivateImage() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.images().list(TEST_ACCOUNT_NO).execute();
                result = getTestImageList();
            }
        };

        Iterable<MachineImage> list = support.listMachineImagesOwnedBy(TEST_ACCOUNT_NO);
        assertNotNull(list);
        List<MachineImage> resultAsList = IteratorUtils.toList(list.iterator());
        assertTrue(resultAsList.size() == 1);
        MachineImage img = resultAsList.get(0);
        assertTrue(img.getTag("project").toString().equals(TEST_ACCOUNT_NO));
    }

    @Test
    public void listShares_shouldReturnEmptyList() throws CloudException, InternalException {
        Iterable<String> list = support.listShares(TEST_IMAGE_ID);
        List<String> resultAsList = IteratorUtils.toList(list.iterator());
        assertTrue(resultAsList.isEmpty());
    }

    @Test(expected = OperationNotSupportedException.class)
    public void registerImageBundle_shouldThrowOperationNotSupportedException() throws CloudException, InternalException {
        support.registerImageBundle(ImageCreateOptions.getInstance(new VirtualMachine(), "NAME", "DESCRIPTION", false));
    }

    @Test
    public void remove_shouldResultInSuccessfulOperation() throws CloudException, InternalException, IOException {
        final Image image = new Image();
        image.setStatus("READY");
        image.setName("TESTIMAGE");
        image.setDescription("CENTOS");
        image.setSelfLink("/projects/MYPROJ/global/images/TESTIMAGE");
        image.set("diskSizeGb", new Long(2));

        new NonStrictExpectations() {
            {googleComputeMock.images().get("MYPROJ", "TESTIMAGE").execute();
                result = image;
            }
            {googleComputeMock.images().delete(TEST_ACCOUNT_NO, "TESTIMAGE").execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.GLOBAL_OPERATION, "", "");
                result = true;
            }
        };

        support.remove(TEST_IMAGE_ID);
    }

    @Test
    public void remove_shouldJustReturnIfImageNotFound() throws CloudException, InternalException {
        new NonStrictExpectations(ImageSupport.class) {
            {support.getImage(anyString);
                result = null;
            }
        };

        support.remove(TEST_IMAGE_ID);
    }

    @Test
    public void searchImagesNoFiltering_shouldReturnPublicAndPrivateImages() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.images().list(TEST_ACCOUNT_NO).execute();
                result = getTestImageList();
            }
            {googleComputeMock.images().list("centos-cloud").execute();
                result = getTestPublicImageList();
            }
        };

        Iterable<MachineImage> list = support.searchImages(null, null, Platform.CENT_OS, null);
        List<MachineImage> resultAsList = IteratorUtils.toList(list.iterator());
        assertTrue(resultAsList.size() == 2);
    }

    @Test
    public void searchPublicImagesRegexFilter_shouldReturnEmptyList() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.images().list("centos-cloud").execute();
                result = getTestPublicImageList();
            }
        };

        Iterable<MachineImage> list = support.searchPublicImages(ImageFilterOptions.getInstance("myimage").onPlatform(Platform.CENT_OS));
        assertNotNull(list);
        List<MachineImage> resultAsList = IteratorUtils.toList(list.iterator());
        assertTrue(resultAsList.isEmpty());
    }

    @Test
    public void capture_shouldReturnNewImage() throws CloudException, InternalException, IOException {
        final VirtualMachine vm = new VirtualMachine();
        vm.setProviderVirtualMachineId(TEST_VM_ID);
        vm.setProviderDataCenterId(TEST_DATACENTER);
        vm.setProviderVolumeIds("TEST_VOLUME");

        final Disk disk = new Disk();
        disk.setName("TEST_VOLUME");
        disk.setDescription("DESCRIPTION");
        disk.setSelfLink("SELFLINK");
        disk.setZone(TEST_DATACENTER);
        disk.setCreationTimestamp("2016-05-23T09:57:34.123+00:00"); //yyyy-MM-dd'T'HH:mm:ss.SSSZZ
        disk.setStatus("READY");
        disk.setSizeGb(2l);
        disk.setSourceImage("SOURCEIMAGE_CENTOS");

        new NonStrictExpectations(ImageSupport.class) {
            {googleProviderMock.getComputeServices().getVirtualMachineSupport();
                result = serverSupport;
            }
            {serverSupport.getVirtualMachine(anyString);
                result = vm;
            }
            {serverSupport.terminateVm(anyString);
                result = null;
            }
            {googleComputeMock.disks().get(TEST_ACCOUNT_NO, TEST_DATACENTER, "TEST_VOLUME").execute();
                result = disk;
            }
            {googleComputeMock.images().insert(TEST_ACCOUNT_NO, (Image) any).execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.GLOBAL_OPERATION, "", "");
                result = true;
            }
            {googleComputeMock.disks().delete(TEST_ACCOUNT_NO, TEST_DATACENTER, "TEST_VOLUME").execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.ZONE_OPERATION, "", TEST_DATACENTER);
                result = true;
            }
            {support.getImage(anyString);
                result = null;
            }
        };

        support.capture(ImageCreateOptions.getInstance(vm, "NEWIMAGE", "NEWDESCRIPTION", false), null);
    }
}
