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

import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.Snapshot;
import com.google.api.services.compute.model.SnapshotList;
import mockit.NonStrictExpectations;
import org.apache.commons.collections.IteratorUtils;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.Tag;
import org.dasein.cloud.compute.SnapshotCreateOptions;
import org.dasein.cloud.compute.SnapshotFilterOptions;
import org.dasein.cloud.compute.SnapshotState;
import org.dasein.cloud.compute.Volume;
import org.dasein.cloud.google.compute.server.SnapshotSupport;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * User: daniellemayne
 * Date: 25/05/2016
 * Time: 14:23
 */
public class SnapshotSupportTest extends GoogleTestBase {
    private SnapshotSupport support = null;
    final String TEST_SNAPSHOT_ID = "TESTSNAPSHOT_112233";
    final String TEST_VOLUME_ID = "TESTVOLUME_112233";

    @Before
    public void setUp() throws CloudException, InternalException {
        super.setUp();
        support = new SnapshotSupport(googleProviderMock);
    }

    private SnapshotList getTestSnapshotList() {
        Snapshot snapshot = new Snapshot();
        snapshot.setName(TEST_SNAPSHOT_ID);
        snapshot.setDescription("SNAPSHOT_DESCRIPTION");
        snapshot.setStatus("READY");
        snapshot.setDiskSizeGb(new Long(3));
        snapshot.setCreationTimestamp("2016-05-25T14:57:34.123+00:00"); //yyyy-MM-dd'T'HH:mm:ss.SSSZZ
        snapshot.setSourceDisk("/disks/"+TEST_VOLUME_ID);

        Snapshot snapshot2 = new Snapshot();
        snapshot2.setName("SNAPSHOT2");
        snapshot2.setDescription("SNAPSHOT2_DESCRIPTION");
        snapshot2.setStatus("DELETING");
        snapshot2.setDiskSizeGb(new Long(2));
        snapshot2.setCreationTimestamp("2016-05-25T15:07:34.123+00:00"); //yyyy-MM-dd'T'HH:mm:ss.SSSZZ
        snapshot2.setSourceDisk("/disks/"+TEST_VOLUME_ID+"2");

        List<Snapshot> list = new ArrayList<>();
        list.add(snapshot);
        list.add(snapshot2);

        SnapshotList sl = new SnapshotList();
        sl.setItems(list);
        return sl;
    }

    @Test(expected = OperationNotSupportedException.class)
    public void addSnapshotShare_shouldThrowOperationNotSupportedException() throws CloudException, InternalException {
        support.addSnapshotShare(TEST_SNAPSHOT_ID, TEST_ACCOUNT_NO);
    }

    @Test(expected = OperationNotSupportedException.class)
    public void addPublicShare_shouldThrowOperationNotSupportedException() throws CloudException, InternalException {
        support.addPublicShare(TEST_SNAPSHOT_ID);
    }

    @Test
    public void createSnapshot_shouldReturnNewSnapshotName() throws CloudException, InternalException, IOException {
        final Volume volume = new Volume();
        volume.setProviderDataCenterId(TEST_DATACENTER);

        new NonStrictExpectations() {
            {googleProviderMock.getComputeServices().getVolumeSupport().getVolume(anyString);
                result = volume;
            }
            {googleComputeMock.disks().createSnapshot(TEST_ACCOUNT_NO, TEST_DATACENTER, TEST_VOLUME_ID, (Snapshot) any).execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.ZONE_OPERATION, "", TEST_DATACENTER);
                result = true;
            }
            {googleComputeMock.snapshots().list(TEST_ACCOUNT_NO).setFilter("name eq "+TEST_SNAPSHOT_ID).execute();
                result = getTestSnapshotList();
            }
        };

        SnapshotCreateOptions options = SnapshotCreateOptions.getInstanceForCreate(TEST_VOLUME_ID, TEST_SNAPSHOT_ID, "TESTDESCRIPTION");

        String newSnapshotID = support.createSnapshot(options);
        assertTrue(newSnapshotID.equals(TEST_SNAPSHOT_ID));
    }

    @Test
    public void getSnapshot_shouldReturnCorrectAttributes() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.snapshots().get(TEST_ACCOUNT_NO, "SNAPSHOT2").execute();
                result = getTestSnapshotList().getItems().get(1);
            }
        };

        org.dasein.cloud.compute.Snapshot snapshot = support.getSnapshot("SNAPSHOT2");
        assertTrue(snapshot.getName().equals("SNAPSHOT2"));
        assertTrue(snapshot.getProviderSnapshotId().equals("SNAPSHOT2"));
        assertTrue(snapshot.getDescription().equals("SNAPSHOT2_DESCRIPTION"));
        assertTrue(snapshot.getOwner().equals(TEST_ACCOUNT_NO));
        assertTrue(snapshot.getCurrentState().equals(SnapshotState.DELETED));
        assertTrue(snapshot.getSizeInGb() == 2);

        DateTimeFormatter fmt = ISODateTimeFormat.dateTime();
        DateTime dt = DateTime.parse("2016-05-25T15:07:34.123+00:00", fmt);
        assertTrue(snapshot.getSnapshotTimestamp() == dt.toDate().getTime());
        assertTrue(snapshot.getVolumeId().equals(TEST_VOLUME_ID+"2"));
    }

    @Test
    public void listShares_shouldReturnEmptyList() throws CloudException, InternalException {
        Iterable<String> shares = support.listShares(TEST_SNAPSHOT_ID);
        List<String> resultAsList = IteratorUtils.toList(shares.iterator());
        assertTrue(resultAsList.isEmpty());
    }

    @Test
    public void listSnapshotStatus_shouldReturnCorrectStatusForEachSnapshot() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.snapshots().list(TEST_ACCOUNT_NO).execute();
                result = getTestSnapshotList();
            }
        };
        Iterable<ResourceStatus> list = support.listSnapshotStatus();
        assertNotNull(list);
        List<ResourceStatus> resultAsList = IteratorUtils.toList(list.iterator());
        assertTrue(resultAsList.size() == 2);
        ResourceStatus status = resultAsList.get(0);
        assertTrue(status.getResourceStatus().equals(SnapshotState.AVAILABLE));
        status = resultAsList.get(1);
        assertTrue(status.getResourceStatus().equals(SnapshotState.DELETED));
    }

    @Test
    public void listSnapshots_shouldReturnAllAvailableSnapshots() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.snapshots().list(TEST_ACCOUNT_NO).execute();
                result = getTestSnapshotList();
            }
        };

        Iterable<org.dasein.cloud.compute.Snapshot> list = support.listSnapshots();
        assertNotNull(list);
        List<org.dasein.cloud.compute.Snapshot> resultAsList = IteratorUtils.toList(list.iterator());
        assertTrue(resultAsList.size() == 2);
    }

    @Test
    public void listSnapshotsWithNameFilter_shouldOnlyReturnFilteredSnapshot() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.snapshots().list(TEST_ACCOUNT_NO).execute();
                result = getTestSnapshotList();
            }
        };

        Iterable<org.dasein.cloud.compute.Snapshot> list = support.listSnapshots(SnapshotFilterOptions.getInstance("SNAPSHOT2"));
        assertNotNull(list);
        List<org.dasein.cloud.compute.Snapshot> resultAsList = IteratorUtils.toList(list.iterator());
        assertTrue(resultAsList.size() == 1);
        assertTrue(resultAsList.get(0).getName().equals("SNAPSHOT2"));
    }

    @Test
    public void remove_shouldResultInSuccessfulOperation() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.snapshots().delete(TEST_ACCOUNT_NO, TEST_SNAPSHOT_ID).execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.GLOBAL_OPERATION, "", "");
                result = true;
            }
        };

        support.remove(TEST_SNAPSHOT_ID);
    }

    @Test(expected = OperationNotSupportedException.class)
    public void removeSnapshotShare_shouldThrowOperationNotSupportedException() throws CloudException, InternalException, IOException {
        support.removeSnapshotShare(TEST_SNAPSHOT_ID, TEST_ACCOUNT_NO);
    }

    @Test(expected = OperationNotSupportedException.class)
    public void removePublicShare_shouldThrowOperationNotSupportedException() throws CloudException, InternalException, IOException {
        support.removePublicShare(TEST_SNAPSHOT_ID);
    }

    @Test(expected = OperationNotSupportedException.class)
    public void removeTagsFromSingleSnapshot_shouldThrowOperationNotSupportedException() throws CloudException, InternalException, IOException {
        support.removeTags(TEST_SNAPSHOT_ID, new Tag());
    }

    @Test(expected = OperationNotSupportedException.class)
    public void removeTagsFromMultipleSnapshots_shouldThrowOperationNotSupportedException() throws CloudException, InternalException, IOException {
        support.removeTags(new String[]{TEST_SNAPSHOT_ID, "anothersnapshotid"}, new Tag());
    }

    @Test
    public void searchSnapshotsWithNameFilter_shouldOnlyReturnFilteredSnapshot() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.snapshots().list(TEST_ACCOUNT_NO).execute();
                result = getTestSnapshotList();
            }
        };

        Iterable<org.dasein.cloud.compute.Snapshot> list = support.searchSnapshots(SnapshotFilterOptions.getInstance("SNAPSHOT2"));
        assertNotNull(list);
        List<org.dasein.cloud.compute.Snapshot> resultAsList = IteratorUtils.toList(list.iterator());
        assertTrue(resultAsList.size() == 1);
        assertTrue(resultAsList.get(0).getName().equals("SNAPSHOT2"));
    }

    @Test(expected = OperationNotSupportedException.class)
    public void updateTagsFromSingleSnapshot_shouldThrowOperationNotSupportedException() throws CloudException, InternalException, IOException {
        support.updateTags(TEST_SNAPSHOT_ID, new Tag());
    }

    @Test(expected = OperationNotSupportedException.class)
    public void updateTagsFromMultipleSnapshots_shouldThrowOperationNotSupportedException() throws CloudException, InternalException, IOException {
        support.updateTags(new String[]{TEST_SNAPSHOT_ID, "anothersnapshotid"}, new Tag());
    }
}
