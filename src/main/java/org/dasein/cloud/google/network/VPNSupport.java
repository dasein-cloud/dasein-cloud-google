package org.dasein.cloud.google.network;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
import org.dasein.cloud.network.VPN;
import org.dasein.cloud.network.VPNCapabilities;
import org.dasein.cloud.network.VPNConnection;
import org.dasein.cloud.network.VPNGateway;
import org.dasein.cloud.network.VPNProtocol;
import org.dasein.cloud.network.VPNState;
import org.dasein.cloud.util.APITrace;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Address;
import com.google.api.services.compute.model.ForwardingRule;
import com.google.api.services.compute.model.ForwardingRuleList;
import com.google.api.services.compute.model.Network;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.Route;
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
    public void connectToGateway(String providerVpnId, String toGatewayId) throws CloudException, InternalException {
        APITrace.begin(provider, "connectVPNToGateway");
        try {
            // TODO Auto-generated method stub
        } finally {
            APITrace.end();
        }
    }


    @Override
    public VPN createVPN(String inProviderDataCenterId, String name, String description, VPNProtocol protocol) throws CloudException, InternalException {
        APITrace.begin(provider, "createVPN");
        try {
            // TODO Auto-generated method stub
        } finally {
            APITrace.end();
        }
        return null;
    }


    @Override
    public VPN createVPN(String inProviderDataCenterId, String name, String description, String providerVlanId, VPNProtocol protocol) throws CloudException, InternalException {
        APITrace.begin(provider, "createVPN");
        VPN vpn = new VPN();
        try {
            vpn.setName(name);
            vpn.setDescription(description);
            Compute gce = getProvider().getGoogleCompute();
            try {
                GoogleMethod method = new GoogleMethod(getProvider());

                TargetVpnGateway vpnGatewayContent = new TargetVpnGateway();
                vpnGatewayContent.setName(name);
                vpnGatewayContent.setDescription(description);
                Network net = gce.networks().get(getContext().getAccountNumber(), providerVlanId).execute();
                vpnGatewayContent.setNetwork(net.getSelfLink());

                Operation op = gce.targetVpnGateways().insert(getContext().getAccountNumber(), inProviderDataCenterId, vpnGatewayContent).execute();
                method.getOperationComplete(getContext(), op, GoogleOperationType.REGION_OPERATION, inProviderDataCenterId, null);

                vpn.setName(name);
                vpn.setDescription(description);
                vpn.setProtocol(protocol);
                vpn.setProviderVpnId(providerVlanId);

                Address ipContent = new Address();
                ipContent.setDescription(name);
                ipContent.setName(name);
                ipContent.setRegion(inProviderDataCenterId);
                op = gce.addresses().insert(getContext().getAccountNumber(), inProviderDataCenterId, ipContent).execute();
                method.getOperationComplete(getContext(), op, GoogleOperationType.REGION_OPERATION, inProviderDataCenterId, null);
                Address ipAddress = gce.addresses().get(getContext().getAccountNumber(), inProviderDataCenterId, name).execute();

                vpn.setProviderVpnIP(ipAddress.getAddress());

                ForwardingRule frContent = new ForwardingRule();
                frContent.setIPAddress(ipAddress.getAddress());
                frContent.setIPProtocol("ESP");
                frContent.setName(name + "-rule-esp");
                frContent.setDescription(name);
                frContent.setTarget(gce.getBaseUrl() + getContext().getAccountNumber() + "/regions/" + inProviderDataCenterId +"/targetVpnGateways/" + name);
                op = gce.forwardingRules().insert(getContext().getAccountNumber(), inProviderDataCenterId, frContent ).execute();
                method.getOperationComplete(getContext(), op, GoogleOperationType.REGION_OPERATION, inProviderDataCenterId, null);

                frContent = new ForwardingRule();
                frContent.setIPAddress(ipAddress.getAddress());
                frContent.setIPProtocol("UDP");
                frContent.setName(name + "-rule-udp4500");
                frContent.setDescription(name);
                frContent.setPortRange("4500");
                frContent.setTarget(gce.getBaseUrl() + getContext().getAccountNumber() + "/regions/" + inProviderDataCenterId +"/targetVpnGateways/" + name);
                op = gce.forwardingRules().insert(getContext().getAccountNumber(), inProviderDataCenterId, frContent ).execute();
                method.getOperationComplete(getContext(), op, GoogleOperationType.REGION_OPERATION, inProviderDataCenterId, null);

                frContent = new ForwardingRule();
                frContent.setIPAddress(ipAddress.getAddress());
                frContent.setIPProtocol("UDP");
                frContent.setName(name + "-rule-udp500");
                frContent.setDescription(name);
                frContent.setPortRange("500");
                frContent.setTarget(gce.getBaseUrl() + getContext().getAccountNumber() + "/regions/" + inProviderDataCenterId +"/targetVpnGateways/" + name);
                op = gce.forwardingRules().insert(getContext().getAccountNumber(), inProviderDataCenterId, frContent ).execute();
                method.getOperationComplete(getContext(), op, GoogleOperationType.REGION_OPERATION, inProviderDataCenterId, null);
            } catch ( Exception e ) { //IOException
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } finally {
            APITrace.end();
        }
        return vpn;
    }

    @Override
    public VPNGateway connectToVPNGateway(String vpnName, String endpoint, String name, String description, VPNProtocol protocol, String sharedSecret, String cidr) throws CloudException, InternalException {
        APITrace.begin(provider, "createVPNGateway");
        try {
            GoogleMethod method = new GoogleMethod(getProvider());
            Compute gce = getProvider().getGoogleCompute();
            Operation op = null;
            VPN vpn = getVPN(vpnName);

            VpnTunnel content = new VpnTunnel();
            content.setName(name);
            content.setDescription(description);
            if (VPNProtocol.IKE_V1 == protocol) {
                content.setIkeVersion(1);
            } else if (VPNProtocol.IKE_V2 == protocol) {
                content.setIkeVersion(2);
            }
            content.setPeerIp(endpoint);
            content.setSharedSecret(sharedSecret);

            content.setTargetVpnGateway(gce.getBaseUrl() + getContext().getAccountNumber() + "/regions/" + vpn.getProviderRegionId() +"/targetVpnGateways/" + vpnName);
            op = gce.vpnTunnels().insert(getContext().getAccountNumber(), vpn.getProviderRegionId(), content).execute();
            method.getOperationComplete(getContext(), op, GoogleOperationType.REGION_OPERATION, "us-central1", null);

            Route routeContent = new Route();
            routeContent.setName(name);
            routeContent.setDescription(description);
            routeContent.setNetwork(gce.getBaseUrl() + getContext().getAccountNumber() + "/global/networks/" + name);
            routeContent.setPriority(1000L);
            routeContent.setDestRange(cidr);
            routeContent.setNextHopVpnTunnel(gce.getBaseUrl() + getContext().getAccountNumber() + "/regions/" + vpn.getProviderRegionId() +"/vpnTunnels/" + vpnName);
            op = gce.routes().insert(getContext().getAccountNumber(), routeContent ).execute();
            method.getOperationComplete(getContext(), op, GoogleOperationType.GLOBAL_OPERATION, null, null);

            VpnTunnel vpnAfter = gce.vpnTunnels().get(getContext().getAccountNumber(), vpn.getProviderRegionId(), name).execute();

        } catch ( Exception e ) { //IO
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            APITrace.end();
        }
        return null; //TODO need to return something...
    }

    @Override
    public VPNGateway createVPNGateway(String endpoint, String name, String description, VPNProtocol protocol, String bgpAsn) throws CloudException, InternalException {
        APITrace.begin(provider, "createVPNGateway");
        try {
            // TODO Auto-generated method stub
        } finally {
            APITrace.end();
        }
        return null;
    }

    @Override
    public void deleteVPN(String providerVpnId) throws CloudException, InternalException {
        APITrace.begin(provider, "deleteVPN");
        try {
            Compute gce = getProvider().getGoogleCompute();
            VPN vpn = getVPN(providerVpnId);

            try {
                GoogleMethod method = new GoogleMethod(getProvider());
                Operation op = null;
                TargetVpnGateway v = gce.targetVpnGateways().get(getContext().getAccountNumber(), vpn.getProviderRegionId(), providerVpnId).execute();

                for (String forwardingRule : v.getForwardingRules()) {
                    ForwardingRule fr = gce.forwardingRules().get(getContext().getAccountNumber(), vpn.getProviderRegionId(), forwardingRule.replaceAll(".*/", "")).execute();
                    String ipAddress = fr.getIPAddress();
                    op = gce.forwardingRules().delete(getContext().getAccountNumber(), vpn.getProviderRegionId(), forwardingRule.replaceAll(".*/", "")).execute();
                    method.getOperationComplete(getContext(), op, GoogleOperationType.REGION_OPERATION, vpn.getProviderRegionId(), null);

                    try {
                        String ipAddressName = getProvider().getNetworkServices().getIpAddressSupport().getIpAddressIdFromIP(ipAddress, vpn.getProviderRegionId());
                        op = gce.addresses().delete(getContext().getAccountNumber(), vpn.getProviderRegionId(), ipAddressName).execute();
                        method.getOperationComplete(getContext(), op, GoogleOperationType.REGION_OPERATION, vpn.getProviderRegionId(), null);
                    } catch (Exception e ) {}// NOP - address does not exist
                }
                op = gce.targetVpnGateways().delete(getContext().getAccountNumber(), vpn.getProviderRegionId(), providerVpnId).execute();
                method.getOperationComplete(getContext(), op, GoogleOperationType.REGION_OPERATION, vpn.getProviderRegionId(), null);


            } catch (IOException e ) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } finally {
            APITrace.end();
        }
    }

    @Override
    public void deleteVPNGateway(String providerVPNGatewayId) throws CloudException, InternalException {
        APITrace.begin(provider, "deleteVPNGateway");
        try {
            // TODO Auto-generated method stub
        } finally {
            APITrace.end();
        }
    }

    @Override
    public void detachFromVLAN(String providerVpnId, String providerVlanId) throws CloudException, InternalException {
        APITrace.begin(provider, "detachVPNFromVLAN");
        try {
            // TODO Auto-generated method stub
        } finally {
            APITrace.end();
        }
    }

    @Override
    public void disconnectFromGateway(String providerVpnId, String fromGatewayId) throws CloudException, InternalException {
        APITrace.begin(provider, "disconnectVPNFromGateway");
        try {
            // TODO Auto-generated method stub
        } finally {
            APITrace.end();
        }
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
        APITrace.begin(provider, "getGateway");
        try {
            // TODO Auto-generated method stub
        } finally {
            APITrace.end();
        }
        return null;
    }

    @Override
    public VPN getVPN(String providerVpnId) throws CloudException, InternalException {
        APITrace.begin(provider, "getVPN");
        try {
            Compute gce = getProvider().getGoogleCompute();

            Collection<Region> regions = getProvider().getDataCenterServices().listRegions();
            for (Region region : regions) {
                TargetVpnGatewayList tunnels = gce.targetVpnGateways().list(getContext().getAccountNumber(), region.getName()).execute();

                if ((null != tunnels) && (null != tunnels.getItems())) {
                    List<TargetVpnGateway> tunnelItems = tunnels.getItems();
                    for (TargetVpnGateway tunnel : tunnelItems) {
                        if (providerVpnId.equals(tunnel.getName())) {
                            return toVPN(tunnel);
                        }
                    }
                }
            }
        } catch ( IOException e ) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            APITrace.end();
        }
        return null;
    }

    public VPN toVPN(TargetVpnGateway tunnel) {
        VPN vpn = new VPN();
        vpn.setName(tunnel.getName());
        vpn.setDescription(tunnel.getDescription());
        vpn.setProviderVpnId(tunnel.getId().toString());
        vpn.setProviderRegionId(tunnel.getRegion().replaceAll(".*/", ""));

        return vpn;
    }

    @Override
    public Requirement getVPNDataCenterConstraint() throws CloudException, InternalException {
        return getCapabilities().getVPNDataCenterConstraint();
    }

    @Override
    public Iterable<VPNConnection> listGatewayConnections(String toGatewayId) throws CloudException, InternalException {
        APITrace.begin(provider, "listGatewayConnections");
        try {
            // TODO Auto-generated method stub
        } finally {
            APITrace.end();
        }
        return null;
    }

    @Override
    public Iterable<ResourceStatus> listGatewayStatus() throws CloudException, InternalException {
        APITrace.begin(provider, "listGatewayStatus");
        try {
            // TODO Auto-generated method stub
        } finally {
            APITrace.end();
        }
        return null;
    }

    @Override
    public Iterable<VPNGateway> listGateways() throws CloudException, InternalException {
        APITrace.begin(provider, "listGateways");
        try {
            // TODO Auto-generated method stub
        } finally {
            APITrace.end();
        }
        return null;
    }

    @Override
    public Iterable<VPNGateway> listGatewaysWithBgpAsn(String bgpAsn) throws CloudException, InternalException {
        APITrace.begin(provider, "listGatewaysWithBgpAsn");
        try {
            // TODO Auto-generated method stub
        } finally {
            APITrace.end();
        }
        return null;
    }

    @Override
    public Iterable<VPNConnection> listVPNConnections(String toVpnId) throws CloudException, InternalException {
        APITrace.begin(provider, "listVPNConnections");
        try {
            // TODO Auto-generated method stub
        } finally {
            APITrace.end();
        }
        return null;
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
                    // TODO Auto-generated catch block
                    e.printStackTrace();
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
                        vpn.setProviderRegionId(tunnel.getRegion().replaceAll(".*/", ""));
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
        } catch ( Exception e ) { // IO
            // TODO Auto-generated catch block
            e.printStackTrace();
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
            // TODO Auto-generated method stub
        } finally {
            APITrace.end();
        }
        return false;
    }
}
