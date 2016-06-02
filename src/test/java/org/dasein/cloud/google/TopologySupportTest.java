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

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.InstanceTemplate;
import com.google.api.services.compute.model.InstanceTemplateList;
import mockit.Mocked;
import mockit.NonStrictExpectations;
import org.apache.commons.collections.IteratorUtils;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ci.GoogleTopologySupport;
import org.dasein.cloud.ci.Topology;
import org.dasein.cloud.ci.TopologyFilterOptions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * User: daniellemayne
 * Date: 26/05/2016
 * Time: 15:53
 */
@RunWith(JUnit4.class)
public class TopologySupportTest extends GoogleTestBase {
    private GoogleTopologySupport support = null;
    final String TEST_INSTANCE_TEMPLATE = "TEST_INSTANCE_TEMPLATE";

    @Mocked
    Compute.InstanceTemplates instanceTemplates;

    @Before
    public void setUp() throws CloudException, InternalException {
        super.setUp();
        support = new GoogleTopologySupport(googleProviderMock);
        new NonStrictExpectations(){
            {googleComputeMock.instanceTemplates();
                result = instanceTemplates;
            }
        };
    }

    private InstanceTemplateList getTestInstanceTemplateList() {
        InstanceTemplate it = new InstanceTemplate();
        it.setName(TEST_INSTANCE_TEMPLATE);
        it.setCreationTimestamp(TEST_INSTANCE_TEMPLATE);
        it.setDescription("DESCRIPTION");

        List<InstanceTemplate> list = new ArrayList<>();
        list.add(it);
        InstanceTemplateList itl = new InstanceTemplateList();
        itl.setItems(list);
        return itl;
    }

    @Test
    public void listTopologies_shouldReturnAllAvailableTopologies() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {instanceTemplates.list(TEST_ACCOUNT_NO).execute();
                result = getTestInstanceTemplateList();
            }
        };

        Iterable<Topology> list = support.listTopologies(TopologyFilterOptions.getInstance());
        assertNotNull(list);
        List<Topology> resultAsList = IteratorUtils.toList(list.iterator());
        assertTrue(resultAsList.size() == 1);
        Topology topology = resultAsList.get(0);
        assertTrue(topology.getName().equals(TEST_INSTANCE_TEMPLATE));
        assertNull(topology.getProviderRegionId());
        assertTrue(topology.getDescription().equals("DESCRIPTION"));
        assertNull(topology.getProviderDataCenterId());
        assertTrue(topology.getProviderOwnerId().equals(TEST_ACCOUNT_NO));
    }
}
