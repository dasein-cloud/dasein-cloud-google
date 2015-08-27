package org.dasein.cloud.google.network;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.dc.Region;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.GoogleMethod;
import org.dasein.cloud.google.GoogleOperationType;
import org.dasein.cloud.google.capabilities.GCEVPNCapabilities;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.network.AbstractVPNSupport;
import org.dasein.cloud.network.IPVersion;
import org.dasein.cloud.network.VPN;
import org.dasein.cloud.network.VPNCapabilities;
import org.dasein.cloud.network.VPNConnection;
import org.dasein.cloud.network.VPNConnectionState;
import org.dasein.cloud.network.VPNGateway;
import org.dasein.cloud.network.VPNGatewayCreateOptions;
import org.dasein.cloud.network.VPNGatewayState;
import org.dasein.cloud.network.VPNProtocol;
import org.dasein.cloud.network.VPNState;
import org.dasein.cloud.network.VPNCreateOptions;
import org.dasein.cloud.util.APITrace;

import com.google.api.services.compute.Compute;
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

public class VPNSupport extends AbstractVPNSupport<Google> {

    private Google provider;
    private VPNCapabilities capabilities;

    protected VPNSupport(Google provider) {
        super(provider);
        this.provider = provider;
    }

    @Override
    public String[] mapServiceAction(ServiceAction action) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void attachToVLAN(String providerVpnId, String providerVlanId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("vlans are integral in GCE VPNS and are attached by createVPN");
    }

    @Override
    public VPN createVPN(VPNCreateOptions vpnLaunchOptions) throws CloudException, InternalException {
        APITrace.begin(provider, "createVPN");
        VPN vpn = new VPN();
        try {
            vpn.setName(vpnLaunchOptions.getName());
            vpn.setDescription(vpnLaunchOptions.getDescription());
            Compute gce = getProvider().getGoogleCompute();
            try {
                GoogleMethod method = new GoogleMethod(getProvider());

                TargetVpnGateway vpnGatewayContent = new TargetVpnGateway();
                vpnGatewayContent.setName(vpnLaunchOptions.getName());
                vpnGatewayContent.setDescription(vpnLaunchOptions.getDescription());
                Network net = gce.networks().get(getContext().getAccountNumber(), vpnLaunchOptions.getProviderVlanId()).execute();
                vpnGatewayContent.setNetwork(net.getSelfLink());

                Operation op = gce.targetVpnGateways().insert(getContext().getAccountNumber(), getContext().getRegionId(), vpnGatewayContent).execute();
                method.getOperationComplete(getContext(), op, GoogleOperationType.REGION_OPERATION, getContext().getRegionId(), null);

                vpn.setName(vpnLaunchOptions.getName());
                vpn.setDescription(vpnLaunchOptions.getDescription());
                vpn.setProtocol(vpnLaunchOptions.getProtocol());
                vpn.setProviderVpnId(vpnLaunchOptions.getProviderVlanId());
                String ipAddress = getProvider().getNetworkServices().getIpAddressSupport().request(IPVersion.IPV4);
                vpn.setProviderVpnIP(getProvider().getNetworkServices().getIpAddressSupport().getIpAddress(ipAddress).getRawAddress().getIpAddress());

                createForwardingRule(vpnLaunchOptions.getName(), "-rule-esp", vpn.getProviderVpnIp(), "ESP", null);
                createForwardingRule(vpnLaunchOptions.getName(), "-rule-udp500", vpn.getProviderVpnIp(), "UDP", "500");
                createForwardingRule(vpnLaunchOptions.getName(), "-rule-udp4500", vpn.getProviderVpnIp(), "UDP", "4500");

            } catch ( Exception e ) {
                throw new CloudException(e);
            }
        } finally {
            APITrace.end();
        }
        return vpn;
    }

    @Override
    public void deleteVPN(String providerVpnId) throws CloudException, InternalException {
        APITrace.begin(provider, "deleteVPN");
        try {
            Compute gce = getProvider().getGoogleCompute();

            try {
                GoogleMethod method = new GoogleMethod(getProvider());
                Operation op = null;
                TargetVpnGateway v = gce.targetVpnGateways().get(getContext().getAccountNumber(), getContext().getRegionId(), providerVpnId).execute();

                RouteList routes = gce.routes().list(getContext().getAccountNumber()).execute();
                if ((null != routes) && (null != routes.getItems())) {
                    for (Route route : routes.getItems()) {
                        if ((null != route.getNextHopVpnTunnel()) && 
                            (route.getName().replaceAll(".*/", "").equals(providerVpnId))) {
                            op = gce.routes().delete(getContext().getAccountNumber(), route.getName()).execute();
                            method.getOperationComplete(getContext(), op, GoogleOperationType.GLOBAL_OPERATION, null, null);
                        }
                    }
                }
                for (String forwardingRule : v.getForwardingRules()) {
                    ForwardingRule fr = gce.forwardingRules().get(getContext().getAccountNumber(), getContext().getRegionId(), forwardingRule.replaceAll(".*/", "")).execute();
                    String ipAddress = fr.getIPAddress();
                    op = gce.forwardingRules().delete(getContext().getAccountNumber(), getContext().getRegionId(), forwardingRule.replaceAll(".*/", "")).execute();
                    method.getOperationComplete(getContext(), op, GoogleOperationType.REGION_OPERATION, getContext().getRegionId(), null);
                    try {
                        String ipAddressName = getProvider().getNetworkServices().getIpAddressSupport().getIpAddressIdFromIP(ipAddress, getContext().getRegionId());
                        getProvider().getNetworkServices().getIpAddressSupport().releaseFromPool(ipAddressName);
                    } catch (InternalException e) {  } // NOP if it already got freed.
                }

                op = gce.targetVpnGateways().delete(getContext().getAccountNumber(), getContext().getRegionId(), providerVpnId).execute();
                method.getOperationComplete(getContext(), op, GoogleOperationType.REGION_OPERATION, getContext().getRegionId(), null);
            } catch (IOException e ) {
                throw new CloudException(e);
            }
        } finally {
            APITrace.end();
        }
    }

    private void createForwardingRule(@Nonnull String targetVpnGatewayId, @Nonnull String ruleName, @Nonnull String ipAddress, @Nonnull String protocol, @Nullable String portRange) throws CloudException, InternalException {
        GoogleMethod method = new GoogleMethod(getProvider());
        Compute gce = getProvider().getGoogleCompute();

        ForwardingRule frContent = new ForwardingRule();
        frContent.setName(targetVpnGatewayId + ruleName);
        frContent.setDescription(targetVpnGatewayId + ruleName);
        frContent.setIPAddress(ipAddress);
        frContent.setIPProtocol(protocol);
        if (protocol.equalsIgnoreCase("UDP")) {
            frContent.setPortRange(portRange);
        }
        frContent.setTarget(gce.getBaseUrl() + getContext().getAccountNumber() + "/regions/" + getContext().getRegionId() +"/targetVpnGateways/" + targetVpnGatewayId);
        Operation op;
        try {
            op = gce.forwardingRules().insert(getContext().getAccountNumber(), getContext().getRegionId(), frContent ).execute();
        } catch (Exception e ) {
            throw new CloudException(e);
        }
        method.getOperationComplete(getContext(), op, GoogleOperationType.REGION_OPERATION, getContext().getRegionId(), null);

    }

    @Override
    public @Nonnull VPNGateway createVPNGateway(@Nonnull VPNGatewayCreateOptions vpnGatewayCreateOptions) throws CloudException, InternalException {
        APITrace.begin(provider, "createVPNGateway");
        try {
            GoogleMethod method = new GoogleMethod(getProvider());
            Compute gce = getProvider().getGoogleCompute();
            Operation op = null;

            VpnTunnel content = new VpnTunnel();
            content.setName(vpnGatewayCreateOptions.getName());
            content.setDescription(vpnGatewayCreateOptions.getDescription());
            if (VPNProtocol.IKE_V1 == vpnGatewayCreateOptions.getProtocol()) {
                content.setIkeVersion(1);
            } else if (VPNProtocol.IKE_V2 == vpnGatewayCreateOptions.getProtocol()) {
                content.setIkeVersion(2);
            }
            content.setPeerIp(vpnGatewayCreateOptions.getEndpoint());
            content.setSharedSecret(vpnGatewayCreateOptions.getSharedSecret());

            content.setTargetVpnGateway(gce.getBaseUrl() + getContext().getAccountNumber() + "/regions/" + getContext().getRegionId() +"/targetVpnGateways/" + vpnGatewayCreateOptions.getVpnName());
            op = gce.vpnTunnels().insert(getContext().getAccountNumber(), getContext().getRegionId(), content).execute();
            method.getOperationComplete(getContext(), op, GoogleOperationType.REGION_OPERATION, getContext().getRegionId(), null);

            createRoute(vpnGatewayCreateOptions.getName(), vpnGatewayCreateOptions.getVlanName(), vpnGatewayCreateOptions.getDescription(), vpnGatewayCreateOptions.getCidr(), getContext().getRegionId());

            VpnTunnel vpnAfter = gce.vpnTunnels().get(getContext().getAccountNumber(), getContext().getRegionId(), vpnGatewayCreateOptions.getName()).execute();

            return toVPNGateway(vpnAfter);
        } catch ( Exception e ) {
            throw new CloudException(e);
        } finally {
            APITrace.end();
        }
    }

    @Override
    public void connectToGateway(String providerVpnId, String toGatewayId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("connectToGateway in GCE VPNS is performed by createVPNGateway");
    }

    private void createRoute(String vpnName, String name, String description, String cidr, String providerRegionId) throws CloudException, InternalException {
        GoogleMethod method = new GoogleMethod(getProvider());
        Compute gce = getProvider().getGoogleCompute();
        Operation op = null;

        Route routeContent = new Route();
        routeContent.setName(name);
        routeContent.setDescription(description);
        routeContent.setNetwork(gce.getBaseUrl() + getContext().getAccountNumber() + "/global/networks/" + name);
        routeContent.setPriority(1000L);
        routeContent.setDestRange(cidr);
        routeContent.setNextHopVpnTunnel(gce.getBaseUrl() + getContext().getAccountNumber() + "/regions/" + providerRegionId +"/vpnTunnels/" + vpnName);
        try {
            op = gce.routes().insert(getContext().getAccountNumber(), routeContent ).execute();
        } catch (IOException e) {
            throw new CloudException(e);
        }
        method.getOperationComplete(getContext(), op, GoogleOperationType.GLOBAL_OPERATION, null, null);
    }

    @Override
    public void disconnectFromGateway(String providerVpnId, String fromGatewayId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Gateway are not supported by GCE VPN's");
    }

    @Override
    public void deleteVPNGateway(String providerVPNGatewayId) throws CloudException, InternalException {
        APITrace.begin(provider, "deleteVPNGateway");
        try {
            Compute gce = getProvider().getGoogleCompute();
            Operation op = null;
            GoogleMethod method = new GoogleMethod(getProvider());

            op = gce.vpnTunnels().delete(getContext().getAccountNumber(), getContext().getRegionId(), providerVPNGatewayId).execute();
            method.getOperationComplete(getContext(), op, GoogleOperationType.REGION_OPERATION, getContext().getRegionId(), null);
        } catch ( IOException e ) {
            throw new CloudException(e);

        } finally {
            APITrace.end();
        }
    }

    private VPNGateway toVPNGateway(VpnTunnel vpnTunnel) {
        VPNGateway vpnGateway = new VPNGateway();
        vpnGateway.setName(vpnTunnel.getName());
        vpnGateway.setDescription(vpnTunnel.getDescription());
        vpnGateway.setProviderRegionId(vpnTunnel.getRegion().replaceAll(".*/", ""));
        vpnGateway.setEndpoint(vpnTunnel.getPeerIp());

        if (1 == vpnTunnel.getIkeVersion()) {
            vpnGateway.setProtocol(VPNProtocol.IKE_V1); 
        } else if (2 == vpnTunnel.getIkeVersion()) {
            vpnGateway.setProtocol(VPNProtocol.IKE_V2); 
        }

        String status = vpnTunnel.getStatus();
        if (status.equals("WAITING_FOR_FULL_CONFIG")) {
            vpnGateway.setCurrentState(VPNGatewayState.PENDING);
        } else {
            vpnGateway.setCurrentState(VPNGatewayState.AVAILABLE);
        }

        return vpnGateway;
    }

    @Override
    public void detachFromVLAN(String providerVpnId, String providerVlanId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("vlans are integral in GCE VPNS and are detatched by deleteVPN");
    }

    @Override
    public VPNCapabilities getCapabilities() throws CloudException, InternalException {
        if (capabilities == null) {
            capabilities = new GCEVPNCapabilities(provider);
        }
        return capabilities;
    }

    @Override
    public VPNGateway getGateway(String gatewayId) throws CloudException, InternalException {
        Compute gce = getProvider().getGoogleCompute();

        VpnTunnel vpnAfter;
        try {
            vpnAfter = gce.vpnTunnels().get(getContext().getAccountNumber(), getContext().getRegionId(), gatewayId).execute();
        } catch ( IOException e ) {
            throw new CloudException(e);
        }

        return toVPNGateway(vpnAfter); 
    }

    @Override
    public VPN getVPN(String providerTargetVPNGatewayId) throws CloudException, InternalException {
        APITrace.begin(provider, "getVPN");
        try {
            Compute gce = getProvider().getGoogleCompute();

            Collection<Region> regions = getProvider().getDataCenterServices().listRegions();
            for (Region region : regions) {
                TargetVpnGatewayList tunnels = gce.targetVpnGateways().list(getContext().getAccountNumber(), region.getName()).execute();

                if ((null != tunnels) && (null != tunnels.getItems())) {
                    List<TargetVpnGateway> targetVpnGatewayItems = tunnels.getItems();
                    for (TargetVpnGateway targetVpnGateway : targetVpnGatewayItems) {
                        if (providerTargetVPNGatewayId.equals(targetVpnGateway.getName())) {
                            return toVPN(targetVpnGateway);
                        }
                    }
                }
            }
            return null;
        } catch (IOException e) {
            throw new CloudException(e);
        } finally {
            APITrace.end();
        }
    }

    public VPN toVPN(TargetVpnGateway targetVpnGateway) {
        VPN vpn = new VPN();
        vpn.setName(targetVpnGateway.getName());
        vpn.setDescription(targetVpnGateway.getDescription());
        vpn.setProviderVpnId(targetVpnGateway.getId().toString());

        return vpn;
    }

    @Override
    public Iterable<VPNConnection> listGatewayConnections(String toGatewayId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Gateway are not supported by GCE VPN's");
    }

    @Override
    public Iterable<ResourceStatus> listGatewayStatus() throws CloudException, InternalException {
        throw new OperationNotSupportedException("Gateway are not supported by GCE VPN's");
    }

    @Override
    public Iterable<VPNGateway> listGateways() throws CloudException, InternalException {
        throw new OperationNotSupportedException("Gateway are not supported by GCE VPN's");
    }

    @Override
    public Iterable<VPNGateway> listGatewaysWithBgpAsn(String bgpAsn) throws CloudException, InternalException {
        throw new OperationNotSupportedException("GCE VPNS do not support bgpAsn");
    }

    @Override
    public Iterable<VPNConnection> listVPNConnections(String toVpnId) throws CloudException, InternalException {
        APITrace.begin(provider, "listVPNConnections");
        VPN vpn = getVPN(toVpnId);
        List<VPNConnection> vpnConnections = new ArrayList<VPNConnection>();
        try {
            Compute gce = getProvider().getGoogleCompute();

            Collection<Region> regions = getProvider().getDataCenterServices().listRegions();
            for (Region region : regions) {
                VpnTunnelList tunnels;
                try {
                    tunnels = gce.vpnTunnels().list(getContext().getAccountNumber(), region.getName()).execute();
                } catch ( IOException e ) {
                    throw new CloudException(e);
                }

                if ((null != tunnels) && (null != tunnels.getItems())) {
                    for (VpnTunnel vpnTunnel : tunnels.getItems()) {
                        if (toVpnId.equals(vpnTunnel.getTargetVpnGateway().replaceAll(".*/", ""))) {
                            VPNConnection vpnConnection = new VPNConnection();

                            if (vpnTunnel.getIkeVersion() == 1) {
                                vpnConnection.setProtocol(VPNProtocol.IKE_V1);
                            } else if (vpnTunnel.getIkeVersion() == 2) {
                                vpnConnection.setProtocol(VPNProtocol.IKE_V2);
                            }

                            if (vpnTunnel.getStatus().equals("ESTABLISHED")) {
                                vpnConnection.setCurrentState(VPNConnectionState.AVAILABLE);
                            } else {
                                vpnConnection.setCurrentState(VPNConnectionState.PENDING);
                            }

                            vpnConnection.setProviderGatewayId(vpnTunnel.getPeerIp());
                            vpnConnection.setProviderVpnConnectionId(vpnTunnel.getName());
                            vpnConnection.setProviderVpnId(vpn.getName());
                            vpnConnections.add(vpnConnection);
                        }
                    }
                }
            }
        } finally {
            APITrace.end();
        }
        return vpnConnections;
    }


    @Override
    public Iterable<ResourceStatus> listVPNStatus() throws CloudException, InternalException {
        APITrace.begin(provider, "listVPNStatus");
        List<ResourceStatus> statusList = new ArrayList<ResourceStatus>();
        try {
            Compute gce = getProvider().getGoogleCompute();

            Collection<Region> regions = getProvider().getDataCenterServices().listRegions();
            for (Region region : regions) {
                VpnTunnelList tunnels = null;
                try {
                    tunnels = gce.vpnTunnels().list(getContext().getAccountNumber(), region.getName()).execute();
                } catch ( IOException e ) {
                    throw new CloudException(e);
                }
                if ((null != tunnels) && (null != tunnels.getItems())) {
                    for (VpnTunnel tunnel : tunnels.getItems()) {
                        if (tunnel.getStatus().equals("ESTABLISHED")) {
                            ResourceStatus status = new ResourceStatus(tunnel.getName(), VPNState.AVAILABLE);
                            statusList.add(status);
                        } else {
                            ResourceStatus status = new ResourceStatus(tunnel.getName(), VPNState.PENDING);
                            statusList.add(status);
                        }
                    }
                }
            }
        } finally {
            APITrace.end();
        }
        return statusList;
    }

    @Override
    public Iterable<VPN> listVPNs() throws CloudException, InternalException {
        APITrace.begin(provider, "listVPNs");
        List<VPN> vpns = new ArrayList<VPN>();
        try {
            Compute gce = getProvider().getGoogleCompute();

            Collection<Region> regions = getProvider().getDataCenterServices().listRegions();
            for (Region region : regions) {
                VpnTunnelList tunnels = gce.vpnTunnels().list(getContext().getAccountNumber(), region.getName()).execute();
                if (null != tunnels.getItems()) {
                    for (VpnTunnel tunnel : tunnels.getItems()) {
                        VPN vpn = new VPN();
                        vpn.setName(tunnel.getName());
                        vpn.setDescription(tunnel.getDescription());
                        vpn.setProviderVpnId(tunnel.getId().toString());
                        if (1 == tunnel.getIkeVersion()) {
                            vpn.setProtocol(VPNProtocol.IKE_V1);
                        } else if (2 == tunnel.getIkeVersion()) {
                            vpn.setProtocol(VPNProtocol.IKE_V2);
                        }
                        if (tunnel.getStatus().equals("ESTABLISHED")) {
                            vpn.setCurrentState(VPNState.AVAILABLE);
                        } else {
                            vpn.setCurrentState(VPNState.PENDING); // TODO does it have more states?
                        }

                        TargetVpnGateway gateway = gce.targetVpnGateways().get(getContext().getAccountNumber(), region.getName(), tunnel.getTargetVpnGateway().replaceAll(".*/", "")).execute();
                        String[] networks = {gateway.getNetwork().replaceAll(".*/", "")};
                        vpn.setProviderVlanIds(networks);

                        ForwardingRuleList frl = gce.forwardingRules().list(getContext().getAccountNumber(), region.getName()).execute();
                        if (null != frl.getItems()) {
                            for (ForwardingRule fr : frl.getItems()) {
                                if (fr.getTarget().equals(gateway.getSelfLink())) {
                                    vpn.setProviderVpnIP(fr.getIPAddress());
                                }
                            }
                        }
                        vpns.add(vpn);
                    }
                }
            }
        } catch ( Exception e ) {
            throw new CloudException(e);
        } finally {
            APITrace.end();
        }
        return vpns;
    }

    @Override
    public Iterable<VPNProtocol> listSupportedVPNProtocols() throws CloudException, InternalException {
        return getCapabilities().listSupportedVPNProtocols();
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        APITrace.begin(provider, "isSubscribed");
        try {
            listVPNs();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            APITrace.end();
        }
    }

    @Deprecated
    @Override
    public Requirement getVPNDataCenterConstraint() throws CloudException, InternalException {
        return Requirement.NONE;
    }
}
