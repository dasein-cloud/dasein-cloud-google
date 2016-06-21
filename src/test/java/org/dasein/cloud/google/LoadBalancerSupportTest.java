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

import com.google.api.services.compute.model.ForwardingRule;
import com.google.api.services.compute.model.ForwardingRuleList;
import com.google.api.services.compute.model.HttpHealthCheck;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.Region;
import com.google.api.services.compute.model.TargetPool;
import com.google.api.services.compute.model.TargetPoolList;
import com.google.api.services.compute.model.TargetPoolsAddHealthCheckRequest;
import com.google.api.services.compute.model.TargetPoolsAddInstanceRequest;
import com.google.api.services.compute.model.TargetPoolsRemoveInstanceRequest;
import mockit.Expectations;
import mockit.Mocked;
import mockit.NonStrictExpectations;
import org.apache.commons.collections.IteratorUtils;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ResourceNotFoundException;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.google.network.IPAddressSupport;
import org.dasein.cloud.google.network.LoadBalancerSupport;
import org.dasein.cloud.network.HealthCheckFilterOptions;
import org.dasein.cloud.network.HealthCheckOptions;
import org.dasein.cloud.network.IPVersion;
import org.dasein.cloud.network.LbAlgorithm;
import org.dasein.cloud.network.LbListener;
import org.dasein.cloud.network.LbPersistence;
import org.dasein.cloud.network.LbProtocol;
import org.dasein.cloud.network.LbType;
import org.dasein.cloud.network.LoadBalancer;
import org.dasein.cloud.network.LoadBalancerAddressType;
import org.dasein.cloud.network.LoadBalancerCreateOptions;
import org.dasein.cloud.network.LoadBalancerEndpoint;
import org.dasein.cloud.network.LoadBalancerHealthCheck;
import org.dasein.cloud.network.LoadBalancerState;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.*;

/**
 * User: daniellemayne
 * Date: 15/06/2016
 * Time: 09:12
 */
public class LoadBalancerSupportTest extends GoogleTestBase {
    private LoadBalancerSupport support = null;
    static final String TEST_LOAD_BALANCER_ID = "TEST_LOAD_BALANCER";
    static final String TEST_IP_ADDRESS_ID = "TEST_IP_ADDRESS_ID";
    static final String TEST_LB_HEALTH_CHECK_NAME = "TEST_HEALTH_CHECK_NAME";

    @Mocked
    IPAddressSupport ipAddressSupport;

    @Before
    public void setUp() throws CloudException, InternalException {
        super.setUp();
        support = new LoadBalancerSupport(googleProviderMock);

        new NonStrictExpectations() {
            {googleProviderMock.getNetworkServices().getIpAddressSupport();
                result = ipAddressSupport;
            }
        };
    }

    private ForwardingRuleList getTestForwardingRuleList() {
        ForwardingRule fr1 = new ForwardingRule();
        fr1.setName("TEST_FORWARDING_RULE_1");
        fr1.setTarget("TEST_TARGET/"+TEST_LOAD_BALANCER_ID);
        fr1.setIPProtocol("TCP");
        fr1.setPortRange("80-80");
        fr1.setIPAddress("192.168.101.2");

        ForwardingRule fr2 = new ForwardingRule();
        fr2.setName("TEST_FORWARDING_RULE_2");
        fr2.setTarget("TEST_TARGET/"+TEST_LOAD_BALANCER_ID);
        fr2.setIPProtocol("TCP");
        fr2.setPortRange("81-81");
        fr2.setIPAddress("192.168.101.3");

        ForwardingRule fr3 = new ForwardingRule();
        fr3.setName("TEST_FORWARDING_RULE_3");
        fr3.setTarget("TEST_TARGET/"+TEST_LOAD_BALANCER_ID+"1");
        fr3.setIPProtocol("TCP");
        fr3.setPortRange("82-82");
        fr3.setIPAddress("192.168.101.4");

        List<ForwardingRule> list = new ArrayList<>();
        list.add(fr1);
        list.add(fr2);
        list.add(fr3);

        ForwardingRuleList frList = new ForwardingRuleList();
        frList.setItems(list);

        return frList;
    }

    private HttpHealthCheck getTestHttpHealthCheck() {
        HttpHealthCheck check = new HttpHealthCheck();
        check.setName(TEST_LB_HEALTH_CHECK_NAME);
        check.setDescription("HC_DESCRIPTION");
        check.setHost("localhost");
        check.setPort(80);
        check.setRequestPath("/index.htm");
        check.setCheckIntervalSec(5);
        check.setTimeoutSec(5);
        check.setHealthyThreshold(3);
        check.setUnhealthyThreshold(10);
        return check;
    }

    private TargetPoolList getTestTargetPoolList() {
        TargetPool pool = new TargetPool();
        pool.setName(TEST_LOAD_BALANCER_ID);
        pool.setDescription("LB_DESCRIPTION");
        List<String> hcs = new ArrayList<>();
        hcs.add("HC1");
        pool.setHealthChecks(hcs);
        pool.setCreationTimestamp("2016-06-20T09:57:34.123"); //yyyy-MM-dd'T'HH:mm:ss.SSS
        pool.setRegion(TEST_REGION);
        pool.setInstances(Collections.singletonList(TEST_VM_ID));

        TargetPool pool2 = new TargetPool();
        pool2.setName(TEST_LOAD_BALANCER_ID+"2");
        List<String> hcs2 = new ArrayList<>();
        hcs2.add("HC2");
        hcs2.add("HC3");
        pool2.setHealthChecks(hcs2);

        List<TargetPool> tpList = new ArrayList<>();
        tpList.add(pool);
        tpList.add(pool2);

        TargetPoolList list = new TargetPoolList();
        list.setItems(tpList);
        return list;
    }

    @Test
    public void removeLoadBalancer() throws CloudException, InternalException, IOException {
        final LoadBalancer lb = LoadBalancer.getInstance(TEST_ACCOUNT_NO, TEST_REGION, TEST_LOAD_BALANCER_ID, LoadBalancerState.ACTIVE, TEST_LOAD_BALANCER_ID,
                "DESCRIPTION", LoadBalancerAddressType.DNS, "192.168.101.1", 80);

        new NonStrictExpectations(LoadBalancerSupport.class) {
            {support.getLoadBalancer(TEST_LOAD_BALANCER_ID);
                result = lb;
            }
            {googleComputeMock.forwardingRules().list(TEST_ACCOUNT_NO, TEST_REGION).execute();
                result = getTestForwardingRuleList();
            }
            {ipAddressSupport.releaseFromPool(anyString);
            }
            {ipAddressSupport.getIpAddressIdFromIP(lb.getAddress(), TEST_REGION);
                result = TEST_IP_ADDRESS_ID;
            }
            {support.getLoadBalancerHealthCheckName(TEST_LOAD_BALANCER_ID);
                result = TEST_LB_HEALTH_CHECK_NAME;
            }
            {googleComputeMock.targetPools().delete(TEST_ACCOUNT_NO, TEST_REGION, TEST_LOAD_BALANCER_ID).execute();
                result = new Operation();
            }
            {support.removeLoadBalancerHealthCheck(TEST_LB_HEALTH_CHECK_NAME);
            }
        };

        new Expectations() {
            {googleComputeMock.forwardingRules().delete(TEST_ACCOUNT_NO, TEST_REGION, anyString).execute();
                result = new Operation();
                times = 2;
            }
            {googleMethodMock.getOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.REGION_OPERATION, TEST_REGION, "");
                result = true;
                times = 3;
            }
        };

        support.removeLoadBalancer(TEST_LOAD_BALANCER_ID);
    }

    @Test
    public void getLoadBalancerHealthCheckName_shouldReturnCorrectHealthCheckName() throws CloudException, InternalException, IOException {
        final TargetPool tp = new TargetPool();
        tp.setHealthChecks(Collections.singletonList(TEST_LB_HEALTH_CHECK_NAME));

        new NonStrictExpectations() {
            {googleComputeMock.targetPools().get(TEST_ACCOUNT_NO, TEST_REGION, TEST_LOAD_BALANCER_ID).execute();
                result = tp;
            }
        };

        String healthCheckName = support.getLoadBalancerHealthCheckName(TEST_LOAD_BALANCER_ID);
        assertTrue(healthCheckName.equals(TEST_LB_HEALTH_CHECK_NAME));
    }

    @Test
    public void removeListeners() throws CloudException, InternalException, IOException {
        final LbListener[] listeners = new LbListener[]{LbListener.getInstance(80, 80)};
        new NonStrictExpectations() {
            {googleComputeMock.forwardingRules().list(TEST_ACCOUNT_NO, TEST_REGION).execute();
                result = getTestForwardingRuleList();
            }
            {googleComputeMock.forwardingRules().get(TEST_ACCOUNT_NO, TEST_REGION, "TEST_FORWARDING_RULE_1").execute();
                result = getTestForwardingRuleList().getItems().get(0);
            }
            {googleComputeMock.forwardingRules().get(TEST_ACCOUNT_NO, TEST_REGION, "TEST_FORWARDING_RULE_2").execute();
                result = getTestForwardingRuleList().getItems().get(1);
            }
            {googleComputeMock.forwardingRules().get(TEST_ACCOUNT_NO, TEST_REGION, "TEST_FORWARDING_RULE_3").execute();
                result = getTestForwardingRuleList().getItems().get(2);
            }
            {ipAddressSupport.releaseFromPool(anyString);
            }
        };

        new Expectations() {
            {googleComputeMock.forwardingRules().delete(TEST_ACCOUNT_NO, TEST_REGION, anyString).execute();
                result = new Operation();
                times = 1;
            }
            {googleMethodMock.getOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.REGION_OPERATION, TEST_REGION, "");
                result = true;
                times = 1;
            }
        };

        support.removeListeners(TEST_LOAD_BALANCER_ID, listeners);
    }

    @Test
    public void createLoadBalancer_existingHealthCheckShouldNotCreateNewHealthCheck() throws CloudException, InternalException, IOException {
        final LoadBalancerCreateOptions options = LoadBalancerCreateOptions.getInstance(TEST_LOAD_BALANCER_ID, TEST_LOAD_BALANCER_ID, TEST_IP_ADDRESS_ID);
        final HealthCheckOptions hcOptions = HealthCheckOptions.getInstance(
                TEST_LB_HEALTH_CHECK_NAME, null, null, "localhost",
                LoadBalancerHealthCheck.HCProtocol.HTTP, 8080, "/index.htm",
                support.getCapabilities().getMaxHealthCheckInterval(),
                support.getCapabilities().getMaxHealthCheckTimeout(),
                3,
                10);

        options.withHealthCheckOptions(hcOptions);
        options.havingListeners(LbListener.getInstance(80, 80));

        final TargetPool tp = new TargetPool();
        tp.setSelfLink("SELF_LINK");

        final String validName = support.getCapabilities().getLoadBalancerNamingConstraints().convertToValidName(TEST_LB_HEALTH_CHECK_NAME, Locale.US);

        new NonStrictExpectations(LoadBalancerSupport.class) {
            {googleComputeMock.targetPools().insert(TEST_ACCOUNT_NO, TEST_REGION, (TargetPool) any).execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.REGION_OPERATION, TEST_REGION, "");
                result = true;
            }
            {googleComputeMock.httpHealthChecks().get(TEST_ACCOUNT_NO, validName).execute();
                result = getTestHttpHealthCheck();
            }
            {googleComputeMock.httpHealthChecks().insert(TEST_ACCOUNT_NO, (HttpHealthCheck) any).execute();
                times = 0;
            }
            {support.attachHealthCheckToLoadBalancer(TEST_LOAD_BALANCER_ID, TEST_LB_HEALTH_CHECK_NAME);
            }
            {googleComputeMock.targetPools().get(TEST_ACCOUNT_NO, TEST_REGION, TEST_LOAD_BALANCER_ID).execute();
                result = tp;
            }
            {googleComputeMock.forwardingRules().insert(TEST_ACCOUNT_NO, TEST_REGION, (ForwardingRule) any).execute();
                result = new Operation();
            }
        };

        String lbName = support.createLoadBalancer(options);
        assertTrue(lbName.equals(TEST_LOAD_BALANCER_ID));
    }

    @Test
    public void createLoadBalancer_newHealthCheckShouldCreateHealthCheck() throws CloudException, InternalException, IOException {
        final LoadBalancerCreateOptions options = LoadBalancerCreateOptions.getInstance(TEST_LOAD_BALANCER_ID, TEST_LOAD_BALANCER_ID, TEST_IP_ADDRESS_ID);
        final HealthCheckOptions hcOptions = HealthCheckOptions.getInstance(
                TEST_LB_HEALTH_CHECK_NAME, null, null, "localhost",
                LoadBalancerHealthCheck.HCProtocol.HTTP, 8080, "/index.htm",
                support.getCapabilities().getMaxHealthCheckInterval(),
                support.getCapabilities().getMaxHealthCheckTimeout(),
                3,
                10);

        options.withHealthCheckOptions(hcOptions);
        options.havingListeners(LbListener.getInstance(80, 80));

        final TargetPool tp = new TargetPool();
        tp.setSelfLink("SELF_LINK");

        final String validName = support.getCapabilities().getLoadBalancerNamingConstraints().convertToValidName(TEST_LB_HEALTH_CHECK_NAME, Locale.US);

        new NonStrictExpectations(LoadBalancerSupport.class) {
            {googleComputeMock.targetPools().insert(TEST_ACCOUNT_NO, TEST_REGION, (TargetPool) any).execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.REGION_OPERATION, TEST_REGION, "");
                result = true;
            }
            {googleComputeMock.httpHealthChecks().get(TEST_ACCOUNT_NO, validName).execute();
                result = null;
                result = getTestHttpHealthCheck();
            }
            {googleComputeMock.httpHealthChecks().insert(TEST_ACCOUNT_NO, (HttpHealthCheck) any).execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.GLOBAL_OPERATION, TEST_REGION, "");
                result = true;
            }
            {support.attachHealthCheckToLoadBalancer(TEST_LOAD_BALANCER_ID, TEST_LB_HEALTH_CHECK_NAME);
            }
            {googleComputeMock.targetPools().get(TEST_ACCOUNT_NO, TEST_REGION, TEST_LOAD_BALANCER_ID).execute();
                result = tp;
            }
            {googleComputeMock.forwardingRules().insert(TEST_ACCOUNT_NO, TEST_REGION, (ForwardingRule) any).execute();
                result = new Operation();
            }
        };

        String lbName = support.createLoadBalancer(options);
        assertTrue(lbName.equals(TEST_LOAD_BALANCER_ID));
    }

    @Test(expected = ResourceNotFoundException.class)
    public void createLoadBalancer_shouldThrowExceptionIfNewTargetPoolNotFoundDuringForwardingRuleCreation() throws CloudException, InternalException, IOException {
        final LoadBalancerCreateOptions options = LoadBalancerCreateOptions.getInstance(TEST_LOAD_BALANCER_ID, TEST_LOAD_BALANCER_ID, TEST_IP_ADDRESS_ID);
        final HealthCheckOptions hcOptions = HealthCheckOptions.getInstance(
                TEST_LB_HEALTH_CHECK_NAME, null, null, "localhost",
                LoadBalancerHealthCheck.HCProtocol.HTTP, 8080, "/index.htm",
                support.getCapabilities().getMaxHealthCheckInterval(),
                support.getCapabilities().getMaxHealthCheckTimeout(),
                3,
                10);

        options.withHealthCheckOptions(hcOptions);
        options.havingListeners(LbListener.getInstance(80, 80));

        final String validName = support.getCapabilities().getLoadBalancerNamingConstraints().convertToValidName(TEST_LB_HEALTH_CHECK_NAME, Locale.US);

        new NonStrictExpectations(LoadBalancerSupport.class) {
            {googleComputeMock.targetPools().insert(TEST_ACCOUNT_NO, TEST_REGION, (TargetPool) any).execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.REGION_OPERATION, TEST_REGION, "");
                result = true;
            }
            {googleComputeMock.httpHealthChecks().get(TEST_ACCOUNT_NO, validName).execute();
                result = null;
                result = getTestHttpHealthCheck();
            }
            {googleComputeMock.httpHealthChecks().insert(TEST_ACCOUNT_NO, (HttpHealthCheck) any).execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.GLOBAL_OPERATION, TEST_REGION, "");
                result = true;
            }
            {support.attachHealthCheckToLoadBalancer(TEST_LOAD_BALANCER_ID, TEST_LB_HEALTH_CHECK_NAME);
            }
            {googleComputeMock.targetPools().get(TEST_ACCOUNT_NO, TEST_REGION, TEST_LOAD_BALANCER_ID).execute();
                result = null;
            }
            {googleComputeMock.forwardingRules().insert(TEST_ACCOUNT_NO, TEST_REGION, (ForwardingRule) any).execute();
                result = new Operation();
            }
        };

        support.createLoadBalancer(options);
    }

    @Test
    public void addListeners() throws CloudException, InternalException, IOException {
        final TargetPool tp = new TargetPool();
        tp.setSelfLink("SELF_LINK");

        final LbListener[] listeners = new LbListener[]{LbListener.getInstance(80, 80)};

        new NonStrictExpectations() {
            {googleComputeMock.targetPools().get(TEST_ACCOUNT_NO, TEST_REGION, TEST_LOAD_BALANCER_ID).execute();
                result = tp;
            }
            {googleComputeMock.forwardingRules().list(TEST_ACCOUNT_NO, TEST_REGION).execute();
                result = getTestForwardingRuleList();
            }
            {googleComputeMock.forwardingRules().get(TEST_ACCOUNT_NO, TEST_REGION, "TEST_FORWARDING_RULE_1").execute();
                result = getTestForwardingRuleList().getItems().get(0);
            }
            {googleComputeMock.forwardingRules().get(TEST_ACCOUNT_NO, TEST_REGION, "TEST_FORWARDING_RULE_2").execute();
                result = getTestForwardingRuleList().getItems().get(1);
            }
            {googleComputeMock.forwardingRules().get(TEST_ACCOUNT_NO, TEST_REGION, "TEST_FORWARDING_RULE_3").execute();
                result = getTestForwardingRuleList().getItems().get(2);
            }
            {googleComputeMock.forwardingRules().insert(TEST_ACCOUNT_NO, TEST_REGION, (ForwardingRule) any).execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.REGION_OPERATION, TEST_REGION, "");
                result = true;
            }
        };

        support.addListeners(TEST_LOAD_BALANCER_ID, listeners);
    }

    @Test(expected = ResourceNotFoundException.class)
    public void addListeners_shouldThrowExceptionIfTargetPoolNotFound() throws CloudException, InternalException, IOException {
        final LbListener[] listeners = new LbListener[]{LbListener.getInstance(80, 80)};

        new NonStrictExpectations() {
            {googleComputeMock.targetPools().get(TEST_ACCOUNT_NO, TEST_REGION, TEST_LOAD_BALANCER_ID).execute();
                result = null;
            }
        };

        support.addListeners(TEST_LOAD_BALANCER_ID, listeners);
    }

    @Test
    public void createLoadBalancerHealthCheck_shouldReturnNewHealthCheck() throws CloudException, InternalException, IOException {
        final String validName = support.getCapabilities().getLoadBalancerNamingConstraints().convertToValidName(TEST_LB_HEALTH_CHECK_NAME, Locale.US);

        new NonStrictExpectations() {
            {googleComputeMock.httpHealthChecks().insert(TEST_ACCOUNT_NO, (HttpHealthCheck) any).execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.GLOBAL_OPERATION, TEST_REGION, "");
                result = true;
            }
            {googleComputeMock.httpHealthChecks().get(TEST_ACCOUNT_NO, validName).execute();
                result = getTestHttpHealthCheck();
            }
        };

        LoadBalancerHealthCheck hc = support.createLoadBalancerHealthCheck(HealthCheckOptions.getInstance(
                TEST_LB_HEALTH_CHECK_NAME, null, null, "localhost",
                LoadBalancerHealthCheck.HCProtocol.HTTP, 8080, "/index.htm",
                support.getCapabilities().getMaxHealthCheckInterval(),
                support.getCapabilities().getMaxHealthCheckTimeout(),
                3,
                10));
        assertNotNull(hc);
        assertTrue(hc.getName().equals(TEST_LB_HEALTH_CHECK_NAME));
    }

    @Test
    public void attachHealthCheckToLoadBalancer() throws CloudException, InternalException, IOException {
        final String validName = support.getCapabilities().getLoadBalancerNamingConstraints().convertToValidName(TEST_LB_HEALTH_CHECK_NAME, Locale.US);

        new NonStrictExpectations(){
            {googleComputeMock.httpHealthChecks().get(TEST_ACCOUNT_NO, validName).execute();
                result = getTestHttpHealthCheck();
            }
            {googleComputeMock.targetPools().addHealthCheck(TEST_ACCOUNT_NO, TEST_REGION, TEST_LOAD_BALANCER_ID, (TargetPoolsAddHealthCheckRequest) any).execute();
            }
        };

        support.attachHealthCheckToLoadBalancer(TEST_LOAD_BALANCER_ID, TEST_LB_HEALTH_CHECK_NAME);
    }

    @Test
    public void toLoadBalancerHealthCheck_shouldReturnCorrectAttributes() throws CloudException, InternalException {
        LoadBalancerHealthCheck check = support.toLoadBalancerHealthCheck(TEST_LOAD_BALANCER_ID, getTestHttpHealthCheck());
        assertTrue(check.getName().equals(TEST_LB_HEALTH_CHECK_NAME));
        assertTrue(check.getDescription().equals("HC_DESCRIPTION"));
        assertTrue(check.getHost().equals("localhost"));
        assertTrue(check.getProviderLBHealthCheckId().equals(TEST_LOAD_BALANCER_ID));
        assertTrue(check.getProviderLoadBalancerIds().contains(TEST_LOAD_BALANCER_ID));
        assertTrue(check.getProtocol().equals(LoadBalancerHealthCheck.HCProtocol.HTTP));
        assertTrue(check.getPort() == 80);
        assertTrue(check.getPath().equals("/index.htm"));
        assertTrue(check.getInterval() == 5);
        assertTrue(check.getTimeout() == 5);
        assertTrue(check.getHealthyCount() == 3);
        assertTrue(check.getUnhealthyCount() == 10);
    }

    @Test(expected = InternalException.class)
    public void toLoadBalancerHealthCheck_shouldThrowExceptionIfLoadBalancerIdIsNull() throws CloudException, InternalException {
        support.toLoadBalancerHealthCheck(null, getTestHttpHealthCheck());
    }

    @Test(expected = InternalException.class)
    public void toLoadBalancerHealthCheck_shouldThrowExceptionIfHealthCheckIsNull() throws CloudException, InternalException {
        support.toLoadBalancerHealthCheck(TEST_LOAD_BALANCER_ID, null);
    }

    @Test
    public void listLoadBalancerHealthChecks_noFilterShouldReturnAllAvailableLbHealthChecks() throws CloudException, InternalException, IOException {
        final String validName = support.getCapabilities().getLoadBalancerNamingConstraints().convertToValidName(TEST_LB_HEALTH_CHECK_NAME, Locale.US);

        new NonStrictExpectations(LoadBalancerSupport.class) {
            {googleComputeMock.targetPools().list(TEST_ACCOUNT_NO, TEST_REGION).execute();
                result = getTestTargetPoolList();
            }
            {googleComputeMock.httpHealthChecks().get(TEST_ACCOUNT_NO, validName).execute();
                result = getTestHttpHealthCheck();
            }
            {support.toLoadBalancerHealthCheck(TEST_LOAD_BALANCER_ID, getTestHttpHealthCheck());
                result = LoadBalancerHealthCheck.getInstance(LoadBalancerHealthCheck.HCProtocol.HTTP, 80, "/index.htm", 5, 5, 3, 10);
            }
        };
        Iterable<LoadBalancerHealthCheck> list = support.listLBHealthChecks(HealthCheckFilterOptions.getInstance());
        assertNotNull(list);
        List<TargetPool> resultAsList = IteratorUtils.toList(list.iterator());
        assertTrue(resultAsList.size() == 2);
    }

    @Test
    public void removeLoadBalancerHealthCheck() throws CloudException, InternalException, IOException {
        new NonStrictExpectations(){
            {googleComputeMock.httpHealthChecks().delete(TEST_ACCOUNT_NO, TEST_LB_HEALTH_CHECK_NAME).execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.GLOBAL_OPERATION, TEST_REGION, "");
                result = true;
            }
        };
        support.removeLoadBalancerHealthCheck(TEST_LB_HEALTH_CHECK_NAME);
    }

    @Test
    public void modifyHealthCheck_shouldReturnHealthCheckWithNewProperty() throws CloudException, InternalException, IOException {
        final HealthCheckOptions hcOptions = HealthCheckOptions.getInstance(
                TEST_LB_HEALTH_CHECK_NAME, null, null, "localhost",
                LoadBalancerHealthCheck.HCProtocol.HTTP, 8080, "/index.htm",
                10,//changed from 5 to 10
                support.getCapabilities().getMaxHealthCheckTimeout(),
                3,
                10);

        final HttpHealthCheck newCheck = new HttpHealthCheck();
        newCheck.setName(TEST_LB_HEALTH_CHECK_NAME);
        newCheck.setDescription("HC_DESCRIPTION");
        newCheck.setHost("localhost");
        newCheck.setPort(80);
        newCheck.setRequestPath("/index.htm");
        newCheck.setCheckIntervalSec(10);
        newCheck.setTimeoutSec(5);
        newCheck.setHealthyThreshold(3);
        newCheck.setUnhealthyThreshold(10);

        new NonStrictExpectations(){
            {googleComputeMock.httpHealthChecks().get(TEST_ACCOUNT_NO, TEST_LB_HEALTH_CHECK_NAME).execute();
                result = getTestHttpHealthCheck();
                result = newCheck;
            }
            {googleComputeMock.httpHealthChecks().update(TEST_ACCOUNT_NO, TEST_LB_HEALTH_CHECK_NAME, (HttpHealthCheck) any).execute();
                result = new Operation();
            }
        };

        LoadBalancerHealthCheck check = support.modifyHealthCheck(TEST_LB_HEALTH_CHECK_NAME, hcOptions);
        assertTrue(check.getInterval() == 10);
    }

    @Test
    public void getLoadBalancerHealthCheck_shouldReturnValidObject() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.httpHealthChecks().get(TEST_ACCOUNT_NO, TEST_LB_HEALTH_CHECK_NAME).execute();
                result = getTestHttpHealthCheck();
            }
        };

        LoadBalancerHealthCheck check = support.getLoadBalancerHealthCheck(TEST_LB_HEALTH_CHECK_NAME, TEST_LOAD_BALANCER_ID);
        assertNotNull(check);
        assertTrue(check.getName().equals(TEST_LB_HEALTH_CHECK_NAME));
    }

    @Test
    public void getLoadBalancerHealthCheck_shouldReturnNullForInvalidId() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.httpHealthChecks().get(TEST_ACCOUNT_NO, TEST_LB_HEALTH_CHECK_NAME).execute();
                result = null;
            }
        };

        LoadBalancerHealthCheck check = support.getLoadBalancerHealthCheck(TEST_LB_HEALTH_CHECK_NAME, TEST_LOAD_BALANCER_ID);
        assertNull(check);
    }

    @Test
    public void getLoadBalancer_shouldReturnCorrectAttributes() throws CloudException, InternalException, IOException {
        final Region region = new Region();
        region.setZones(Collections.singletonList(TEST_DATACENTER));

        long dt = 0;

        try {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
            dt = fmt.parse("2016-06-20T09:57:34.123").getTime();
        }
        catch ( ParseException ignore ) {}
        final long timestamp = dt;

        new NonStrictExpectations() {
            {googleComputeMock.targetPools().get(TEST_ACCOUNT_NO, TEST_REGION, TEST_LOAD_BALANCER_ID).execute();
                result = getTestTargetPoolList().getItems().get(0);
            }
            {googleComputeMock.forwardingRules().list(TEST_ACCOUNT_NO, TEST_REGION).execute();
                result = getTestForwardingRuleList();
            }
            {googleComputeMock.forwardingRules().get(TEST_ACCOUNT_NO, TEST_REGION, "TEST_FORWARDING_RULE_1").execute();
                result = getTestForwardingRuleList().getItems().get(0);
            }
            {googleComputeMock.forwardingRules().get(TEST_ACCOUNT_NO, TEST_REGION, "TEST_FORWARDING_RULE_2").execute();
                result = null;
            }
            {googleComputeMock.forwardingRules().get(TEST_ACCOUNT_NO, TEST_REGION, "TEST_FORWARDING_RULE_3").execute();
                result = null;
            }
            {googleComputeMock.regions().get(TEST_ACCOUNT_NO, TEST_REGION).execute();
                result = region;
            }
            {googleProviderMock.parseTime(anyString);
                result = timestamp;}
        };

        LoadBalancer lb = support.getLoadBalancer(TEST_LOAD_BALANCER_ID);
        assertTrue(lb.getProviderOwnerId().equals(TEST_ACCOUNT_NO));
        assertTrue(lb.getProviderRegionId().equals(TEST_REGION));
        assertTrue(lb.getProviderLoadBalancerId().equals(TEST_LOAD_BALANCER_ID));
        assertTrue(lb.getCurrentState().equals(LoadBalancerState.ACTIVE));
        assertTrue(lb.getName().equals(TEST_LOAD_BALANCER_ID));
        assertTrue(lb.getDescription().equals("LB_DESCRIPTION"));
        assertTrue(lb.getType().equals(LbType.EXTERNAL));
        assertTrue(lb.getAddressType().equals(LoadBalancerAddressType.DNS));
        assertTrue(lb.getAddress().equals("192.168.101.2"));
        assertTrue(lb.getProviderLBHealthCheckId().equals("HC1"));

        try {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
            long tmp = fmt.parse("2016-06-20T09:57:34.123").getTime();
            assertTrue(lb.getCreationTimestamp() == tmp);
        }
        catch ( ParseException ignore ) {}

        assertTrue(lb.getProviderDataCenterIds().length == 1);
        assertTrue(lb.getProviderDataCenterIds()[0].equals(TEST_DATACENTER));
        assertTrue(lb.getPublicPorts().length == 1);
        assertTrue(lb.getPublicPorts()[0] == 80);
        assertTrue(lb.getSupportedTraffic()[0].equals(IPVersion.IPV4));
        assertTrue(lb.getListeners().length == 1);
        LbListener listener = lb.getListeners()[0];
        assertTrue(listener.getAlgorithm().equals(LbAlgorithm.SOURCE));
        assertTrue(listener.getPersistence().equals(LbPersistence.SUBNET));
        assertTrue(listener.getNetworkProtocol().equals(LbProtocol.RAW_TCP));
        assertTrue(listener.getPublicPort() == 80);
        assertTrue(listener.getPrivatePort() == 80);
    }

    @Test
    public void addServers() throws CloudException, InternalException, IOException {
        final VirtualMachine vm = new VirtualMachine();
        vm.setProviderRegionId(TEST_REGION);
        vm.setTag("contentLink", "contentLink");

        new NonStrictExpectations() {
            {googleProviderMock.getComputeServices().getVirtualMachineSupport().getVirtualMachine(TEST_VM_ID);
                result = vm;
            }
            {googleComputeMock.targetPools().addInstance(TEST_ACCOUNT_NO, TEST_REGION, TEST_LOAD_BALANCER_ID, (TargetPoolsAddInstanceRequest) any).execute();
                result = new Operation();
            }
        };

        support.addServers(TEST_LOAD_BALANCER_ID, TEST_VM_ID);
    }

    @Test
    public void removeServers() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.targetPools().get(TEST_ACCOUNT_NO, TEST_REGION, TEST_LOAD_BALANCER_ID).execute();
                result = getTestTargetPoolList().getItems().get(0);
            }
            {googleProviderMock.getComputeServices().getVirtualMachineSupport().getVmNameFromId(TEST_VM_ID);
                result = TEST_VM_ID;
            }
            {googleComputeMock.targetPools().removeInstance(TEST_ACCOUNT_NO, TEST_REGION, TEST_LOAD_BALANCER_ID, (TargetPoolsRemoveInstanceRequest) any).execute();
                result = new Operation();
            }
        };

        support.removeServers(TEST_LOAD_BALANCER_ID, TEST_VM_ID);
    }

    @Test(expected = ResourceNotFoundException.class)
    public void removeServers_shouldThrowExceptionIfTargetPoolNotFound() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.targetPools().get(TEST_ACCOUNT_NO, TEST_REGION, TEST_LOAD_BALANCER_ID).execute();
                result = null;
            }
        };
        support.removeServers(TEST_LOAD_BALANCER_ID, TEST_VM_ID);
    }

    @Test
    public void listEndpoints_shouldReturnAllAvailableEndpoints() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.targetPools().get(TEST_ACCOUNT_NO, TEST_REGION, TEST_LOAD_BALANCER_ID).execute();
                result = getTestTargetPoolList().getItems().get(0);
            }
            {googleProviderMock.getComputeServices().getVirtualMachineSupport().getVmIdFromName(TEST_VM_ID);
                result = TEST_VM_ID;
            }
            {googleComputeMock.targetPools().removeInstance(TEST_ACCOUNT_NO, TEST_REGION, TEST_LOAD_BALANCER_ID, (TargetPoolsRemoveInstanceRequest) any).execute();
                result = new Operation();
            }
        };

        Iterable<LoadBalancerEndpoint> list = support.listEndpoints(TEST_LOAD_BALANCER_ID);
        assertNotNull(list);
        List<LoadBalancerEndpoint> resultAsList = IteratorUtils.toList(list.iterator());
        assertTrue(resultAsList.size() == 1);
        LoadBalancerEndpoint endpoint = resultAsList.get(0);
        assertTrue(endpoint.getEndpointValue().equals(TEST_VM_ID));
    }

    @Test(expected = ResourceNotFoundException.class)
    public void listEndpoints_shouldThrowExceptionIfTargetPoolNotFound() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.targetPools().get(TEST_ACCOUNT_NO, TEST_REGION, TEST_LOAD_BALANCER_ID).execute();
                result = null;
            }
        };

        support.listEndpoints(TEST_LOAD_BALANCER_ID);
    }

    @Test
    public void listLoadBalancerStatus() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.targetPools().list(TEST_ACCOUNT_NO, TEST_REGION).execute();
                result = getTestTargetPoolList();
            }
            {googleComputeMock.httpHealthChecks().get(TEST_ACCOUNT_NO, TEST_LB_HEALTH_CHECK_NAME).execute();
                result = getTestHttpHealthCheck();
            }
        };

        Iterable<ResourceStatus> list = support.listLoadBalancerStatus();
        List<ResourceStatus> resultAsList = IteratorUtils.toList(list.iterator());
        assertTrue(resultAsList.size() == 3);
    }

    @Test
    public void listLoadBalancers() throws CloudException, InternalException, IOException {
        final Region region = new Region();
        region.setZones(Collections.singletonList(TEST_DATACENTER));

        long dt = 0;

        try {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
            dt = fmt.parse("2016-06-20T09:57:34.123").getTime();
        }
        catch ( ParseException ignore ) {}
        final long timestamp = dt;

        new NonStrictExpectations() {
            {googleComputeMock.targetPools().list(TEST_ACCOUNT_NO, TEST_REGION).execute();
                result = getTestTargetPoolList();
            }
            {googleComputeMock.forwardingRules().get(TEST_ACCOUNT_NO, TEST_REGION, "TEST_FORWARDING_RULE_1").execute();
                result = getTestForwardingRuleList().getItems().get(0);
            }
            {googleComputeMock.forwardingRules().get(TEST_ACCOUNT_NO, TEST_REGION, "TEST_FORWARDING_RULE_2").execute();
                result = null;
            }
            {googleComputeMock.forwardingRules().get(TEST_ACCOUNT_NO, TEST_REGION, "TEST_FORWARDING_RULE_3").execute();
                result = null;
            }
            {googleComputeMock.regions().get(TEST_ACCOUNT_NO, TEST_REGION).execute();
                result = region;
            }
            {googleProviderMock.parseTime(anyString);
                result = timestamp;
            }
        };

        Iterable<ResourceStatus> list = support.listLoadBalancerStatus();
        List<ResourceStatus> resultAsList = IteratorUtils.toList(list.iterator());
        assertTrue(resultAsList.size() == 3);
    }
}
