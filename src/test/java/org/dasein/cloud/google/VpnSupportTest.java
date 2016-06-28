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
import com.google.api.services.compute.model.Network;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.Route;
import com.google.api.services.compute.model.RouteList;
import com.google.api.services.compute.model.TargetVpnGateway;
import com.google.api.services.compute.model.TargetVpnGatewayList;
import com.google.api.services.compute.model.VpnTunnel;
import com.google.api.services.compute.model.VpnTunnelList;
import mockit.NonStrictExpectations;
import org.apache.commons.collections.IteratorUtils;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.dc.Region;
import org.dasein.cloud.google.network.VpnSupport;
import org.dasein.cloud.network.IPVersion;
import org.dasein.cloud.network.Vpn;
import org.dasein.cloud.network.VpnConnection;
import org.dasein.cloud.network.VpnConnectionState;
import org.dasein.cloud.network.VpnCreateOptions;
import org.dasein.cloud.network.VpnGateway;
import org.dasein.cloud.network.VpnGatewayCreateOptions;
import org.dasein.cloud.network.VpnGatewayState;
import org.dasein.cloud.network.VpnProtocol;
import org.dasein.cloud.network.VpnState;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * User: daniellemayne
 * Date: 22/06/2016
 * Time: 12:19
 */
public class VpnSupportTest extends GoogleTestBase {
    private VpnSupport support = null;

    static final String TEST_VPN_ID = "TEST_VPN_ID";
    static final String TEST_VLAN_ID = "TEST_VLAN_ID";
    static final String TEST_IP_ADDRESS = "192.168.101.1";
    static final String TEST_VPN_GATEWAY = "TEST_VPN_GATEWAY";

    @Before
    public void setUp() throws CloudException, InternalException {
        super.setUp();
        support = new VpnSupport(googleProviderMock);
    }

    private RouteList getTestRouteList() {
        Route route = new Route();
        route.setName(TEST_VPN_ID);
        route.setNetwork(TEST_VLAN_ID);
        route.setDestRange("192.168.101.0");
        route.setNextHopInstance(TEST_VM_ID);
        route.setNextHopVpnTunnel("VPN_TUNNEL");

        RouteList list = new RouteList();
        list.setItems(Collections.singletonList(route));
        return list;
    }

    private VpnTunnelList getTestVpnTunnelList() {
        VpnTunnel vt = new VpnTunnel();
        vt.setName(TEST_VPN_GATEWAY);
        vt.setId(new BigInteger("112233"));
        vt.setDescription(TEST_VPN_GATEWAY+" DESCRIPTION");
        vt.setRegion(TEST_REGION);
        vt.setPeerIp(TEST_IP_ADDRESS);
        vt.setIkeVersion(1);
        vt.setStatus("DONE");
        vt.setTargetVpnGateway(TEST_VPN_ID);

        VpnTunnelList list = new VpnTunnelList();
        list.setItems(Collections.singletonList(vt));
        return list;
    }

    private TargetVpnGatewayList getTestTargetVpnGatewayList() {
        TargetVpnGateway tvg = new TargetVpnGateway();
        tvg.setName(TEST_VPN_ID);
        tvg.setDescription("TEST_DESCRIPTION");
        tvg.setId(new BigInteger("11223344"));
        tvg.setNetwork(TEST_VLAN_ID);
        tvg.setSelfLink("TEST_TARGET/TEST_LOAD_BALANCER_ID");
        TargetVpnGatewayList list = new TargetVpnGatewayList();
        list.setItems(Collections.singletonList(tvg));
        return list;
    }

    private ForwardingRuleList getTestForwardingRuleList() {
        ForwardingRule fr1 = new ForwardingRule();
        fr1.setName("TEST_FORWARDING_RULE_1");
        fr1.setTarget("TEST_TARGET/TEST_LOAD_BALANCER_ID");
        fr1.setIPProtocol("TCP");
        fr1.setPortRange("80-80");
        fr1.setIPAddress("192.168.101.2");

        List<ForwardingRule> list = new ArrayList<>();
        list.add(fr1);

        ForwardingRuleList frList = new ForwardingRuleList();
        frList.setItems(list);

        return frList;
    }

    @Test(expected = OperationNotSupportedException.class)
    public void attachToVlan_shouldThrowNotSupportedException() throws CloudException, InternalException {
        support.attachToVlan(TEST_VPN_ID, TEST_VLAN_ID);
    }

    @Test
    public void createVpn_shouldReturnNewVpnWithCorrectName() throws CloudException, InternalException, IOException {
        final VpnCreateOptions options = VpnCreateOptions.getInstance(TEST_VPN_ID, "TEST_DESCRIPTION", VpnProtocol.OPEN_VPN).withProviderVlanId(TEST_VLAN_ID);

        final Network network = new Network();
        network.setName(TEST_VLAN_ID);
        network.setSelfLink("/networks/"+TEST_VLAN_ID);
        network.setIPv4Range("TEST_DESTINATION_CIDR");
        network.setDescription("TEST_DESCRIPTION");

        new NonStrictExpectations() {
            {googleComputeMock.networks().get(TEST_ACCOUNT_NO, TEST_VLAN_ID).execute();
                result = network;
            }
            {googleComputeMock.targetVpnGateways().insert(TEST_ACCOUNT_NO, TEST_REGION, (TargetVpnGateway) any).execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.GLOBAL_OPERATION, "", "");
                result = true;
            }
            {googleProviderMock.getNetworkServices().getIpAddressSupport().request(IPVersion.IPV4);
                result = TEST_IP_ADDRESS;
            }
            {googleProviderMock.getNetworkServices().getIpAddressSupport().getIpAddress(TEST_IP_ADDRESS).getRawAddress().getIpAddress();
                result = TEST_IP_ADDRESS;
            }
            {googleComputeMock.getBaseUrl();
                result = "/";
            }
            {googleComputeMock.forwardingRules().insert(TEST_ACCOUNT_NO, TEST_REGION, (ForwardingRule) any).execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.REGION_OPERATION, TEST_REGION, null);
                result = true;
            }
        };
        Vpn vpn = support.createVpn(options);
        assertTrue(vpn.getName().equals(TEST_VPN_ID));
        assertTrue(vpn.getProtocol().equals(VpnProtocol.OPEN_VPN));
    }

    @Test
    public void deleteVpn() throws CloudException, InternalException, IOException {
        final TargetVpnGateway tvg = new TargetVpnGateway();
        tvg.setForwardingRules(Collections.singletonList("TEST_FORWARDING_RULE"));

        final ForwardingRule fr = new ForwardingRule();
        fr.setIPAddress("192.168.101.0");

        new NonStrictExpectations() {
            {googleComputeMock.targetVpnGateways().get(TEST_ACCOUNT_NO, TEST_REGION, TEST_VPN_ID).execute();
                result = tvg;
            }
            {googleComputeMock.routes().list(TEST_ACCOUNT_NO).execute();
                result = getTestRouteList();
            }
            {googleComputeMock.routes().delete(TEST_ACCOUNT_NO, TEST_VPN_ID).execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.GLOBAL_OPERATION, null, null);
                result = true;
            }
            {googleComputeMock.forwardingRules().get(TEST_ACCOUNT_NO, TEST_REGION, "TEST_FORWARDING_RULE").execute();
                result = fr;
            }
            {googleComputeMock.forwardingRules().delete(TEST_ACCOUNT_NO, TEST_REGION, "TEST_FORWARDING_RULE").execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.REGION_OPERATION, TEST_REGION, null);
                result = true;
            }
            {googleProviderMock.getNetworkServices().getIpAddressSupport().getIpAddressIdFromIP(TEST_IP_ADDRESS, TEST_REGION);
                result = "TEST_IP_ADDRESS_ID";
            }
            {googleProviderMock.getNetworkServices().getIpAddressSupport().releaseFromPool("TEST_IP_ADDRESS_ID");
            }
            {googleComputeMock.targetVpnGateways().delete(TEST_ACCOUNT_NO, TEST_REGION, TEST_VPN_ID).execute();
                result = new Operation();
            }
        };

        support.deleteVpn(TEST_VPN_ID);
    }

    @Test
    public void createVpnGateway() throws CloudException, InternalException, IOException {
        final VpnGatewayCreateOptions options = VpnGatewayCreateOptions.getInstance(TEST_VPN_GATEWAY, TEST_VPN_GATEWAY+" DESCRIPTION", VpnProtocol.IKE_V1, TEST_IP_ADDRESS);
        options.withSharedSecret("SECRET");
        options.withVlanName(TEST_VLAN_ID);
        options.withCidr("192.168.101.0");

        new NonStrictExpectations() {
            {googleComputeMock.vpnTunnels().insert(TEST_ACCOUNT_NO, TEST_REGION, (VpnTunnel) any).execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.REGION_OPERATION, TEST_REGION, null);
                result = true;
            }
            {googleComputeMock.routes().insert(TEST_ACCOUNT_NO, (Route) any).execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.GLOBAL_OPERATION, null, null);
                result = true;
            }
            {googleComputeMock.vpnTunnels().get(TEST_ACCOUNT_NO, TEST_REGION, TEST_VPN_GATEWAY).execute();
                result = getTestVpnTunnelList().getItems().get(0);
            }
        };

        VpnGateway vg = support.createVpnGateway(options);
        assertNotNull(vg);
        assertTrue(vg.getName().equals(TEST_VPN_GATEWAY));
        assertTrue(vg.getProtocol().equals(VpnProtocol.IKE_V1));
    }

    @Test(expected = OperationNotSupportedException.class)
    public void connectToGateway_shouldThrowOperationNotSupportedException() throws CloudException, InternalException {
        support.connectToGateway(TEST_VPN_ID, TEST_VPN_GATEWAY);
    }

    @Test(expected = OperationNotSupportedException.class)
    public void disconnectFromGateway_shouldThrowOperationNotSupportedException() throws CloudException, InternalException {
        support.disconnectFromGateway(TEST_VPN_ID, TEST_VPN_GATEWAY);
    }

    @Test
    public void deleteVpnGateway() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.vpnTunnels().delete(TEST_ACCOUNT_NO, TEST_REGION, TEST_VPN_GATEWAY).execute();
                result = new Operation();
            }
            {googleMethodMock.getOperationComplete(providerContextMock, (Operation) any, GoogleOperationType.REGION_OPERATION, TEST_REGION, null);
                result = true;
            }
        };

        support.deleteVpnGateway(TEST_VPN_GATEWAY);
    }

    @Test(expected = OperationNotSupportedException.class)
    public void detachFromVlan_shouldThrowOperationNotSupportedException() throws CloudException, InternalException {
        support.detachFromVlan(TEST_VPN_ID, TEST_VLAN_ID);
    }

    @Test
    public void getGateway_shouldReturnCorrectAttributes() throws CloudException, InternalException, IOException {
        new NonStrictExpectations() {
            {googleComputeMock.vpnTunnels().get(TEST_ACCOUNT_NO, TEST_REGION, TEST_VPN_GATEWAY).execute();
                result = getTestVpnTunnelList().getItems().get(0);
            }
        };
        VpnGateway gateway = support.getGateway(TEST_VPN_GATEWAY);
        assertNotNull(gateway);
        assertTrue(gateway.getName().equals(TEST_VPN_GATEWAY));
        assertTrue(gateway.getDescription().equals(TEST_VPN_GATEWAY+" DESCRIPTION"));
        assertTrue(gateway.getProviderRegionId().equals(TEST_REGION));
        assertTrue(gateway.getEndpoint().equals(TEST_IP_ADDRESS));
        assertTrue(gateway.getProtocol().equals(VpnProtocol.IKE_V1));
        assertTrue(gateway.getCurrentState().equals(VpnGatewayState.AVAILABLE));
    }

    @Test
    public void getVpn_shouldReturnCorrectAttributes() throws CloudException, InternalException, IOException {
        final Region r = new Region();
        r.setName(TEST_REGION);

        new NonStrictExpectations() {
            {googleProviderMock.getDataCenterServices().listRegions();
                result = Collections.singletonList(r);
            }
            {googleComputeMock.targetVpnGateways().list(TEST_ACCOUNT_NO, TEST_REGION).execute();
                result = getTestTargetVpnGatewayList();}
        };

        Vpn vpn = support.getVpn(TEST_VPN_ID);
        assertNotNull(vpn);
        assertTrue(vpn.getName().equals(TEST_VPN_ID));
        assertTrue(vpn.getDescription().equals("TEST_DESCRIPTION"));
        assertTrue(vpn.getProviderVpnId().equals("11223344"));
    }

    @Test(expected = OperationNotSupportedException.class)
    public void listGatewayConnections_shouldThrowOperationNotSupportedException() throws CloudException, InternalException{
        support.listGatewayConnections(TEST_VPN_GATEWAY);
    }

    @Test(expected = OperationNotSupportedException.class)
    public void listGatewaStatus_shouldThrowOperationNotSupportedException() throws CloudException, InternalException{
        support.listGatewayStatus();
    }

    @Test(expected = OperationNotSupportedException.class)
    public void listGateways_shouldThrowOperationNotSupportedException() throws CloudException, InternalException{
        support.listGateways();
    }

    @Test(expected = OperationNotSupportedException.class)
    public void listGatewaysWithBgpAsn_shouldThrowOperationNotSupportedException() throws CloudException, InternalException{
        support.listGatewaysWithBgpAsn("BGP_ASN");
    }

    @Test
    public void listVpnConnections_shouldReturnCorrectAttributes() throws CloudException, InternalException, IOException {
        final Vpn vpn = new Vpn();
        vpn.setName(TEST_VPN_ID);

        final Region r = new Region();
        r.setName(TEST_REGION);

        new NonStrictExpectations(VpnSupport.class) {
            {support.getVpn(TEST_VPN_ID);
                result = vpn;
            }
            {googleProviderMock.getDataCenterServices().listRegions();
                result = Collections.singletonList(r);
            }
            {googleComputeMock.vpnTunnels().list(TEST_ACCOUNT_NO, TEST_REGION).execute();
                result = getTestVpnTunnelList();
            }
        };

        Iterable<VpnConnection> list = support.listVpnConnections(TEST_VPN_ID);
        assertNotNull(list);
        List<VpnConnection> resultAsList = IteratorUtils.toList(list.iterator());
        assertTrue(resultAsList.size() == 1);
        VpnConnection vc = resultAsList.get(0);
        assertTrue(vc.getProtocol().equals(VpnProtocol.IKE_V1));
        assertTrue(vc.getCurrentState().equals(VpnConnectionState.PENDING));
        assertTrue(vc.getProviderGatewayId().equals(TEST_IP_ADDRESS));
        assertTrue(vc.getProviderVpnConnectionId().equals(TEST_VPN_GATEWAY));
        assertTrue(vc.getProviderVpnId().equals(TEST_VPN_ID));
    }

    @Test
    public void listVpnStatus() throws CloudException, InternalException, IOException {
        final Region r = new Region();
        r.setName(TEST_REGION);


        new NonStrictExpectations(VpnSupport.class) {
            {googleProviderMock.getDataCenterServices().listRegions();
                result = Collections.singletonList(r);
            }
            {googleComputeMock.vpnTunnels().list(TEST_ACCOUNT_NO, TEST_REGION).execute();
                result = getTestVpnTunnelList();
            }
        };

        Iterable<ResourceStatus> list = support.listVpnStatus();
        assertNotNull(list);
        List<ResourceStatus> resultAsList = IteratorUtils.toList(list.iterator());
        assertTrue(resultAsList.size() == 1);
        assertTrue(resultAsList.get(0).getResourceStatus().equals(VpnState.PENDING));
    }

    @Test
    public void listVpns() throws CloudException, InternalException, IOException {
        final Region r = new Region();
        r.setName(TEST_REGION);

        new NonStrictExpectations(VpnSupport.class) {
            {googleProviderMock.getDataCenterServices().listRegions();
                result = Collections.singletonList(r);
            }
            {googleComputeMock.vpnTunnels().list(TEST_ACCOUNT_NO, TEST_REGION).execute();
                result = getTestVpnTunnelList();
            }
            {googleComputeMock.targetVpnGateways().get(TEST_ACCOUNT_NO, TEST_REGION, TEST_VPN_ID).execute();
                result = getTestTargetVpnGatewayList().getItems().get(0);
            }
            {googleComputeMock.forwardingRules().list(TEST_ACCOUNT_NO, TEST_REGION).execute();
                result = getTestForwardingRuleList();
            }
        };

        Iterable<Vpn> list = support.listVpns();
        assertNotNull(list);
        List<Vpn> resultAsList = IteratorUtils.toList(list.iterator());
        assertTrue(resultAsList.size() == 1);
        Vpn vpn = resultAsList.get(0);
        assertTrue(vpn.getName().equals(TEST_VPN_GATEWAY));
        assertTrue(vpn.getDescription().equals(TEST_VPN_GATEWAY+" DESCRIPTION"));
        assertTrue(vpn.getProviderVpnId().equals("112233"));
        assertTrue(vpn.getProtocol().equals(VpnProtocol.IKE_V1));
        assertTrue(vpn.getCurrentState().equals(VpnState.PENDING));
        assertTrue(vpn.getProviderVlanIds().length == 1);
        assertTrue(vpn.getProviderVlanIds()[0].equals(TEST_VLAN_ID));
        assertTrue(vpn.getProviderVpnIp().equals("192.168.101.2"));
    }
}
