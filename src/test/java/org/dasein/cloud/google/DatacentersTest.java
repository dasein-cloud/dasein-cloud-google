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

import com.google.api.services.compute.model.Region;
import com.google.api.services.compute.model.RegionList;
import com.google.api.services.compute.model.Zone;
import com.google.api.services.compute.model.ZoneList;
import mockit.NonStrictExpectations;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.dc.DataCenter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * User: daniellemayne
 * Date: 16/05/2016
 * Time: 09:20
 */
@RunWith(JUnit4.class)
public class DatacentersTest extends GoogleTestBase{
    private DataCenters dc = null;

    @Before
    public void setUp() throws CloudException, InternalException {
        super.setUp();
        dc = new DataCenters(googleProviderMock);
    }

    private ZoneList getTestDataCenterList() {
        List<Zone> zoneList = new ArrayList<Zone>();

        Zone dc1 = new Zone();
        dc1.setName(TEST_DATACENTER);
        dc1.setRegion(TEST_REGION);
        dc1.setStatus("UP");
        zoneList.add(dc1);

        Zone dc2 = new Zone();
        dc2.setName("TEST_DC_2");
        dc2.setRegion(TEST_REGION);
        dc2.setStatus("UP");
        zoneList.add(dc2);

        ZoneList dcList = new ZoneList();
        dcList.setItems(zoneList);
        return dcList;
    }

    private RegionList getTestRegionList() {
        List<Region> regionList = new ArrayList<Region>();

        Region region = new Region();
        region.setName(TEST_REGION);
        regionList.add(region);

        Region region1 = new Region();
        region1.setName("TEST_REGION_2");
        regionList.add(region1);

        RegionList regionList1 = new RegionList();
        regionList1.setItems(regionList);
        return regionList1;
    }

    @Test
    public void listDatacenters_shouldReturnAllAvailableDCs() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.zones().list(anyString).execute();
                result = getTestDataCenterList();}

        };
        Collection<DataCenter> list = dc.listDataCenters(TEST_REGION);
        assertNotNull("Datacenter list can be empty but not null", list);
        assertTrue(list.size() == 2);
    }

    @Test
    public void getDatacenter_shouldReturnCorrectDC() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.zones().get(TEST_ACCOUNT_NO, TEST_DATACENTER).execute();
                result = getTestDataCenterList().getItems().get(0);}

        };
        DataCenter list = dc.getDataCenter(TEST_DATACENTER);
        assertNotNull(list);
        assertTrue(list.getName().equals(TEST_DATACENTER));
        assertTrue(list.getProviderDataCenterId().equals(TEST_DATACENTER));
        assertTrue(list.getRegionId().equals(TEST_REGION));
    }

    @Test
    public void getRegion_shouldReturnCorrectRegion() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.regions().get(TEST_ACCOUNT_NO, TEST_REGION).execute();
                result = getTestRegionList().getItems().get(0);}

        };
        org.dasein.cloud.dc.Region list = dc.getRegion(TEST_REGION);
        assertNotNull(list);
        assertTrue(list.getName().equals(TEST_REGION));
        assertTrue(list.getProviderRegionId().equals(TEST_REGION));
        assertTrue(list.getJurisdiction().equals("US"));
    }

    @Test
    public void listRegions_shouldReturnAllAvailableRegions() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.regions().list(anyString).execute();
                result = getTestRegionList();
            }
        };
        Collection<org.dasein.cloud.dc.Region> list = dc.listRegions();
        assertNotNull("Region list can be empty but not null", list);
        assertTrue(list.size() == 2);
    }
}
