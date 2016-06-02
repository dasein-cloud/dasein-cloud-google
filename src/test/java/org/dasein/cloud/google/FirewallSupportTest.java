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

import com.google.api.services.compute.model.Firewall;
import com.google.api.services.compute.model.FirewallList;
import com.google.api.services.compute.model.Network;
import com.google.api.services.compute.model.NetworkList;
import com.google.api.services.compute.model.Operation;
import mockit.NonStrictExpectations;
import org.apache.commons.collections.IteratorUtils;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.VisibleScope;
import org.dasein.cloud.google.network.FirewallSupport;
import org.dasein.cloud.network.Direction;
import org.dasein.cloud.network.FirewallCreateOptions;
import org.dasein.cloud.network.FirewallRule;
import org.dasein.cloud.network.Permission;
import org.dasein.cloud.network.Protocol;
import org.dasein.cloud.network.RuleTarget;
import org.dasein.cloud.network.RuleTargetType;
import org.dasein.cloud.network.VLAN;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * User: daniellemayne
 * Date: 31/05/2016
 * Time: 11:19
 */
@RunWith(JUnit4.class)
public class FirewallSupportTest extends GoogleTestBase {
    private FirewallSupport support = null;
    static String TEST_FIREWALL_ID = "fw-TEST_FIREWALL_ID";

    @Before
    public void setUp() throws CloudException, InternalException {
        super.setUp();
        support = new FirewallSupport(googleProviderMock);
    }

    private FirewallList getTestFirewallList() {
        Firewall.Allowed allowed = new Firewall.Allowed();
        allowed.setIPProtocol("tcp");
        allowed.setPorts(Collections.singletonList("22"));

        Firewall firewall = new Firewall();
        firewall.setAllowed(Collections.singletonList(allowed));
        firewall.setCreationTimestamp("2016-06-01T08:57:34.123+00:00"); //yyyy-MM-dd'T'HH:mm:ss.SSSZZ
        firewall.setDescription("TEST_DESCRIPTION");
        firewall.setName("TEST_FIREWALL_ID");
        firewall.setNetwork("https://www.googleapis.com/compute/v1/projects/myproject/global/networks/default");
        firewall.setSourceRanges(Collections.singletonList("0.0.0.0/0"));

        FirewallList list = new FirewallList();
        list.setItems(Collections.singletonList(firewall));
        return list;
    }

    private NetworkList getTestNetworkList() {
        Network net = new Network();
        net.setName("TEST_NETWORK");
        net.setSelfLink("https://www.googleapis.com/compute/v1/projects/myproject/global/networks/TEST_NETWORK");

        Network net2 = new Network();
        net2.setName("default");
        net2.setSelfLink("https://www.googleapis.com/compute/v1/projects/myproject/global/networks/default");

        List<Network> list = new ArrayList<>();
        list.add(net);
        list.add(net2);

        NetworkList nl = new NetworkList();
        nl.setItems(list);
        return nl;
    }

    @Test
    public void authorize_shouldReturnNewFirewallRule() throws CloudException, InternalException, IOException {
        final VLAN vlan = new VLAN();
        vlan.setTag("contentLink", "TESTNETWORKLINK");

        new NonStrictExpectations() {
            {googleProviderMock.getNetworkServices().getVlanSupport().getVlan(anyString);
                result = vlan;
            }
            {googleProviderMock.getComputeServices().getVirtualMachineSupport().getVmNameFromId(anyString);
                result = TEST_VM_ID;
            }
            {googleComputeMock.firewalls().insert(TEST_ACCOUNT_NO, (Firewall) any).execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationTarget(providerContextMock, (Operation) any, GoogleOperationType.GLOBAL_OPERATION, "", "", false);
                result = "TEST_FIREWALL_RULE";
            }
        };

        String ruleId = support.authorize(TEST_FIREWALL_ID, Direction.INGRESS, Permission.ALLOW, RuleTarget.getCIDR("192.168.1.0"), Protocol.TCP,
                RuleTarget.getVirtualMachine(TEST_VM_ID), 80, 80, 0);
        assertTrue(ruleId.equals("TEST_FIREWALL_RULE"));
    }

    @Test(expected = OperationNotSupportedException.class)
    public void authorize_shouldThrowExceptionIfPermissionIsDeny() throws CloudException, InternalException {
        support.authorize(TEST_FIREWALL_ID, Direction.INGRESS, Permission.DENY, RuleTarget.getCIDR("0.0.0.0/0"), Protocol.TCP, RuleTarget.getVlan("VLAN"),
                22, 22, 0);
    }

    @Test(expected = OperationNotSupportedException.class)
    public void authorize_shouldThrowExceptionIfDirectionIsEgress() throws CloudException, InternalException {
        support.authorize(TEST_FIREWALL_ID, Direction.EGRESS, Permission.ALLOW, RuleTarget.getCIDR("0.0.0.0/0"), Protocol.TCP, RuleTarget.getVlan("VLAN"),
                22, 22, 0);
    }

    @Test(expected = OperationNotSupportedException.class)
    public void authorize_shouldThrowExceptionIfSourceEndpointIsGlobal() throws CloudException, InternalException {
        support.authorize(TEST_FIREWALL_ID, Direction.INGRESS, Permission.ALLOW, RuleTarget.getGlobal(TEST_FIREWALL_ID), Protocol.TCP, RuleTarget.getVlan("VLAN"),
                22, 22, 0);
    }

    @Test(expected = OperationNotSupportedException.class)
    public void create_shouldThrowOperationNotSupportException() throws CloudException, InternalException {
        support.create(FirewallCreateOptions.getInstance("NAME", "DESCRIPTION"));
    }

    @Test(expected = OperationNotSupportedException.class)
    public void delete_shouldThrowOperationNotSupportedException() throws CloudException, InternalException {
        support.delete(TEST_FIREWALL_ID);
    }

    @Test
    public void getFirewall_shouldReturnFirewallWithCorrectAttributes() throws CloudException, InternalException, IOException {
        final Network vlan = new Network();
        vlan.setName("TEST_FIREWALL_ID");
        vlan.setDescription("TEST_DESCRIPTION");

        new NonStrictExpectations() {
            {googleComputeMock.networks().get(TEST_ACCOUNT_NO, "TEST_FIREWALL_ID").execute();
                result = vlan;
            }
            {googleComputeMock.firewalls().list(TEST_ACCOUNT_NO).setFilter(anyString).execute();
                result = getTestFirewallList();
            }
         };

        org.dasein.cloud.network.Firewall fw = support.getFirewall(TEST_FIREWALL_ID);
        assertNotNull(fw);
        assertTrue(fw.getName().equals("TEST_FIREWALL_ID Firewall"));
        assertTrue(fw.getProviderFirewallId().equals(TEST_FIREWALL_ID));
        assertTrue(fw.getVisibleScope().equals(VisibleScope.ACCOUNT_GLOBAL));
        assertTrue(fw.isActive());
        assertTrue(fw.isAvailable());
        assertTrue(fw.getDescription().equals("TEST_DESCRIPTION"));
        assertTrue(fw.getProviderVlanId().equals("TEST_FIREWALL_ID"));
        assertNotNull(fw.getRules());
        Iterable<FirewallRule> rules = fw.getRules();
        List<FirewallRule> list = IteratorUtils.toList(rules.iterator());
        assertTrue(list.size() == 1);
        FirewallRule rule = list.get(0);
        assertTrue(rule.getProviderRuleId().equals("TEST_FIREWALL_ID"));
        assertTrue(rule.getFirewallId().equals("fw-default"));
        assertTrue(rule.getSourceEndpoint().getRuleTargetType().equals(RuleTargetType.CIDR));
        assertTrue(rule.getSourceEndpoint().getCidr().equals("0.0.0.0/0"));
        assertTrue(rule.getDirection().equals(Direction.INGRESS));
        assertTrue(rule.getProtocol().equals(Protocol.TCP));
        assertTrue(rule.getPermission().equals(Permission.ALLOW));
        assertTrue(rule.getDestinationEndpoint().getRuleTargetType().equals(RuleTargetType.VLAN));
        assertTrue(rule.getDestinationEndpoint().getProviderVlanId().equals("default"));
        assertTrue(rule.getStartPort() == 22);
        assertTrue(rule.getEndPort() == 22);
    }

    @Test
    public void getFirewall_shouldReturnNullIfFirewallIdDoesNotStartWith_fw() throws CloudException, InternalException, IOException {
        org.dasein.cloud.network.Firewall fw = support.getFirewall("test");
        assertNull(fw);
    }

    @Test
    public void getRules_shouldReturnOnlyRulesForSpecifiedFirewallId() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.firewalls().list(TEST_ACCOUNT_NO).setFilter(anyString).execute();
                result = getTestFirewallList();
            }
        };

        Iterable<FirewallRule> rules = support.getRules(TEST_FIREWALL_ID);
        assertNotNull(rules);
        List<FirewallRule> list = IteratorUtils.toList(rules.iterator());
        assertTrue(list.size() == 1);
    }

    @Test
    public void list_shouldReturnFirewallForEachNetwork() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.networks().list(TEST_ACCOUNT_NO).execute();
                result = getTestNetworkList();
            }
            {googleComputeMock.firewalls().list(TEST_ACCOUNT_NO).execute();
                result = getTestFirewallList();
            }
        };

        Iterable<org.dasein.cloud.network.Firewall> list = support.list();
        assertNotNull(list);
        List<org.dasein.cloud.network.Firewall> resultAsList = IteratorUtils.toList(list.iterator());
        assertTrue(resultAsList.size() == 2);
        Iterable<FirewallRule> ruleList = resultAsList.get(0).getRules();
        Iterable<FirewallRule> ruleList2 = resultAsList.get(1).getRules();
        List<FirewallRule> firewallRuleList = IteratorUtils.toList(ruleList.iterator());
        firewallRuleList.addAll(IteratorUtils.toList(ruleList2.iterator()));
        assertTrue(firewallRuleList.size() == 1);
    }

    @Test
    public void listFirewallStatus_shoudreturnCorrectNumberOfStatusObjects() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.networks().list(TEST_ACCOUNT_NO).execute();
                result = getTestNetworkList();
            }
            {googleComputeMock.firewalls().list(TEST_ACCOUNT_NO).execute();
                result = getTestFirewallList();
            }
        };

        Iterable<ResourceStatus> list = support.listFirewallStatus();
        List<ResourceStatus> resultAsList = IteratorUtils.toList(list.iterator());
        assertNotNull(resultAsList);
        assertTrue(resultAsList.size() == 2);
        assertTrue(resultAsList.get(0).getResourceStatus().equals(true));
        assertTrue(resultAsList.get(1).getResourceStatus().equals(true));
    }

    @Test
    public void revoke_shouldResultInSuccessfulOperation() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.firewalls().delete(TEST_ACCOUNT_NO, TEST_FIREWALL_ID).execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.GLOBAL_OPERATION, "", "");
                result = true;
            }
        };
        support.revoke(TEST_FIREWALL_ID);
    }

    @Test(expected = OperationNotSupportedException.class)
    public void revoke_shouldThrowOperationNotSupportedExceptionIfDirectionIsEgress() throws CloudException, InternalException {
        support.revoke(TEST_FIREWALL_ID, Direction.EGRESS, "0.0.0.0/0", Protocol.TCP, 22, 22);
    }

    @Test(expected = OperationNotSupportedException.class)
    public void revoke_shouldThrowOperationNotSupportedExceptionIfPermissionIsDeny() throws CloudException, InternalException {
        support.revoke(TEST_FIREWALL_ID, Direction.INGRESS, Permission.DENY, "0.0.0.0/0", Protocol.TCP, 22, 22);
    }

    @Test(expected = OperationNotSupportedException.class)
    public void revoke_shouldThrowOperationNotSupportedExceptionIfCIDRIsNotIPv4() throws CloudException, InternalException {
        support.revoke(TEST_FIREWALL_ID, Direction.INGRESS, Permission.ALLOW, "0.0.0/0", Protocol.TCP, RuleTarget.getVlan("VLAN"), 22, 22);
    }

    @Test(expected = OperationNotSupportedException.class)
    public void revoke_shouldThrowOperationNotSupportedExceptionIfSourceIsNotIPv4() throws CloudException, InternalException {
        support.revoke(TEST_FIREWALL_ID, Direction.INGRESS, Permission.ALLOW, "0.0.0", Protocol.TCP, RuleTarget.getVlan("VLAN"), 22, 22);
    }

    @Test(expected = OperationNotSupportedException.class)
    public void revoke_shouldThrowOperationNotSupportedExceptionIfTargetTypeIsCIDR() throws CloudException, InternalException {
        support.revoke(TEST_FIREWALL_ID, Direction.INGRESS, Permission.ALLOW, "0.0.0.0/0", Protocol.TCP, RuleTarget.getCIDR("0.0.0.0/0"), 22, 22);
    }
}
