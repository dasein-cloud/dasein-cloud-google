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
import mockit.Mocked;
import mockit.NonStrictExpectations;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.junit.Before;

/**
 * User: daniellemayne
 * Date: 16/05/2016
 * Time: 12:02
 */
public class GoogleTestBase {
    @Mocked
    Google googleProviderMock;
    @Mocked
    ProviderContext providerContextMock;
    @Mocked
    Compute googleComputeMock;
    @Mocked
    GoogleMethod googleMethodMock;

    final String CLOUD_NAME = "GCE";
    final String TEST_ACCOUNT_NO = "12323232323";
    final String TEST_REGION = "us-central1";
    final String TEST_DATACENTER = "us-central1-a";
    final String TEST_VM_ID = "VMNAME_111122223333";

    @Before
    public void setUp() throws CloudException, InternalException {
        new NonStrictExpectations() {
            {   googleProviderMock.getContext(); result = providerContextMock; }
            {   providerContextMock.getAccountNumber(); result = TEST_ACCOUNT_NO; }
            {   providerContextMock.getRegionId(); result = TEST_REGION; }
            {   googleProviderMock.getGoogleCompute(); result = googleComputeMock; }
            {   googleProviderMock.getCloudName(); result = CLOUD_NAME; }
        };
    }
}
