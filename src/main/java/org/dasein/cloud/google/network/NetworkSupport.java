/**
 * Copyright (C) 2012-2014 Dell, Inc
 * See annotations for authorship information
 *
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */

package org.dasein.cloud.google.network;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Network;
import com.google.api.services.compute.model.NetworkList;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.RouteList;
import org.apache.log4j.Logger;
import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.GeneralCloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.Tag;
import org.dasein.cloud.VisibleScope;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.GoogleException;
import org.dasein.cloud.google.GoogleMethod;
import org.dasein.cloud.google.GoogleOperationType;
import org.dasein.cloud.google.capabilities.GCENetworkCapabilities;
import org.dasein.cloud.network.AbstractVLANSupport;
import org.dasein.cloud.network.FirewallRule;
import org.dasein.cloud.network.IPVersion;
import org.dasein.cloud.network.InternetGateway;
import org.dasein.cloud.network.Route;
import org.dasein.cloud.network.RoutingTable;
import org.dasein.cloud.network.VLAN;
import org.dasein.cloud.network.VLANState;
import org.dasein.cloud.util.APITrace;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Implements the network services supported in the Google API.
 * @author INSERT NAME HERE
 * @version 2013.01 initial version
 * @since 2013.01
 */
public class NetworkSupport extends AbstractVLANSupport {

	static private final Logger logger = Google.getLogger(NetworkSupport.class);
	private Google provider;

	NetworkSupport(Google provider) {
        super(provider);
        this.provider = provider;
    }

	@Override
	public Route addRouteToAddress(String toRoutingTableId, IPVersion version, String destinationCidr, String address) throws CloudException, InternalException {
		throw new OperationNotSupportedException("GCE currently only supports routing to instances.");
	}

	@Override
	public Route addRouteToGateway(String toRoutingTableId, IPVersion version, String destinationCidr, String gatewayId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("GCE currently only supports routing to instances.");
	}

	@Override
	public Route addRouteToNetworkInterface(String toRoutingTableId, IPVersion version, String destinationCidr, String nicId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("GCE currently only supports routing to instances.");
	}

	@Override
	public Route addRouteToVirtualMachine(String toRoutingTableId, IPVersion version, String destinationCidr, String vmId) throws CloudException, InternalException {
		//Using toRoutingTableId as vlanId - GCE supports vlan specific routes
        ProviderContext ctx = provider.getContext();

        Operation job = null;
        try{
            Compute gce = provider.getGoogleCompute();
            VirtualMachine vm = provider.getComputeServices().getVirtualMachineSupport().getVirtualMachine(vmId);

            com.google.api.services.compute.model.Route route = new com.google.api.services.compute.model.Route();
            route.setName((destinationCidr + "-" + vmId).toLowerCase());
            route.setDestRange(destinationCidr);
            route.setNextHopInstance((String)vm.getTag("contentLink"));
            route.setNextHopIp(vm.getPrivateAddresses()[0].getIpAddress());
            route.setNextHopGateway("/projects/<project-id>/global/gateways/default-internet-gateway");//Currently only supports Internet Gateway

            job = gce.routes().insert(ctx.getAccountNumber(), route).execute();

            GoogleMethod method = new GoogleMethod(provider);
            String routeName = method.getOperationTarget(ctx, job, GoogleOperationType.GLOBAL_OPERATION, "", "", false);
            com.google.api.services.compute.model.Route googleRoute = gce.routes().get(ctx.getAccountNumber(), routeName).execute();

            Route r = toRoute(googleRoute);
            return r;
	    } catch (IOException ex) {
            logger.error(ex.getMessage());
			if (ex.getClass() == GoogleJsonResponseException.class) {
				GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
			} else {
				throw new GeneralCloudException("An error occurred while creating the route: " + ex.getMessage(), ex);
			}
		}
	}

	@Override
    public @Nonnull VLAN createVlan(@Nonnull String cidr, @Nonnull String name, @Nonnull String description, @Nonnull String domainName, @Nonnull String[] dnsServers, @Nonnull String[] ntpServers) throws CloudException, InternalException {
		if(!getCapabilities().allowsNewVlanCreation()) {
			throw new OperationNotSupportedException("Creating vlans is not supported for Google");
		}
		ProviderContext ctx = provider.getContext();

		String regionId = ctx.getRegionId();
		if( regionId == null ) {
			logger.error("No region was set for this request");
			throw new InternalException("No region was set for this request");
		}

        Operation job = null;
        try{
            Compute gce = provider.getGoogleCompute();
            Network network = new Network();
            name = getCapabilities().getVlanNamingConstraints().convertToValidName(name, Locale.US);
            network.setName(name);
            network.setDescription(description);
            network.setIPv4Range(cidr);
            job = gce.networks().insert(ctx.getAccountNumber(), network).execute();

            GoogleMethod method = new GoogleMethod(provider);
            String vLanName = method.getOperationTarget(ctx, job, GoogleOperationType.GLOBAL_OPERATION, "", "", false);

            Network googleVLan = gce.networks().get(ctx.getAccountNumber(), vLanName).execute();
            VLAN vLan = toVlan(googleVLan, ctx);
            return vLan;
	    } catch (IOException ex) {
			logger.error("An error occurred while creating vlan: " + ex.getMessage());
			if (ex.getClass() == GoogleJsonResponseException.class) {
				GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
			} else {
				throw new GeneralCloudException("An error occurred while creating vlan: " + ex.getMessage(), ex);
			}
		}
	}

    private transient volatile GCENetworkCapabilities capabilities;
    @Override
    public @Nonnull GCENetworkCapabilities getCapabilities(){
        if(capabilities == null){
            capabilities = new GCENetworkCapabilities(provider);
        }
        return capabilities;
    }

	@Override
	public RoutingTable getRoutingTableForVlan(@Nonnull String vlanId)throws CloudException, InternalException {
		throw new OperationNotSupportedException("Routing tables not supported.");
	}

	@Override
	public @Nullable VLAN getVlan(@Nonnull String vlanId) throws CloudException, InternalException {
		ProviderContext ctx = provider.getContext();

		try{
            Compute gce = provider.getGoogleCompute();
            Network network = gce.networks().get(ctx.getAccountNumber(), vlanId).execute();
            return toVlan(network, ctx);
	    } catch (IOException ex) {
	    	if ((ex.getMessage() != null) && (ex.getMessage().contains("404 Not Found"))) {// vlan not found, its ok, return null.
				return null;
			}
			logger.error("An error occurred while getting network " + vlanId + ": " + ex.getMessage());
			if (ex.getClass() == GoogleJsonResponseException.class) {
	            GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
			} else {
				throw new GeneralCloudException(ex.getMessage(), ex);
			}
		}
	}

	@Override
	public boolean isSubscribed() throws CloudException, InternalException {
		return true;
	}

	@Override
	public @Nonnull Collection<String> listFirewallIdsForNIC(@Nonnull String nicId) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Not currently implemented for " + provider.getCloudName());
	}

    @Override
    public InternetGateway getInternetGatewayById(@Nonnull String gatewayId) throws CloudException, InternalException{
        throw new OperationNotSupportedException("Not currently implemented for " + provider.getCloudName());
    }

	@Override
    public @Nullable String getAttachedInternetGatewayId(@Nonnull String vlanId) throws CloudException, InternalException{
        throw new OperationNotSupportedException("Not currently implemented for " + provider.getCloudName());
    }

    @Override
    public void removeInternetGatewayById(@Nonnull String id) throws CloudException, InternalException{
        throw new OperationNotSupportedException("Not currently implemented for " + provider.getCloudName());
    }

	@Override
	public @Nonnull Iterable<ResourceStatus> listVlanStatus() throws CloudException, InternalException {
        APITrace.begin(provider, "VLAN.listVlanStatus");
        try{
            Compute gce = provider.getGoogleCompute();
            ArrayList<ResourceStatus> statuses = new ArrayList<ResourceStatus>();
            try{
                NetworkList networks = gce.networks().list(provider.getContext().getAccountNumber()).execute();
                for(Network network : networks.getItems()){
                    statuses.add(new ResourceStatus(network.getName(), VLANState.AVAILABLE));
                }
                return statuses;
    	    } catch (IOException ex) {
                logger.error(ex.getMessage());
    			if (ex.getClass() == GoogleJsonResponseException.class) {
    				GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
    				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
    			} else {
					throw new GeneralCloudException("An error occurred getting VLAN statuses", ex);
				}
			}
        }
        finally {
            APITrace.end();
        }
	}

	@Override
	public @Nonnull Iterable<VLAN> listVlans() throws CloudException, InternalException {
		ProviderContext ctx = provider.getContext();

		ArrayList<VLAN> vlans = new ArrayList<VLAN>();
        try{
            Compute gce = provider.getGoogleCompute();
            NetworkList networkList = gce.networks().list(ctx.getAccountNumber()).execute();
            if (null != networkList) {
                List<Network> networks = networkList.getItems();
                if (networks != null) {
                    for (Network net : networks) {
                        VLAN vlan = toVlan(net, ctx);
                        if(vlan != null) {
							vlans.add(vlan);
						}
                    }
                }
            }
	    } catch (IOException ex) {
            logger.error(ex.getMessage());
			if (ex.getClass() == GoogleJsonResponseException.class) {
				GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
			} else {
				throw new GeneralCloudException("An error occurred while listing VLans: " + ex.getMessage(), ex);
			}
		} catch (Exception e) {
		    throw new GeneralCloudException("An error occurred while listing VLans for " + ctx.getAccountNumber() + ": " + e.getMessage(), e);
		}
        return vlans;
	}

	@Override
	public void removeVlan(String vlanId) throws CloudException, InternalException {
        APITrace.begin(provider, "VLAN.removeVlan");
        try{
            Operation op = null;
            try{
                GoogleMethod method = new GoogleMethod(provider);
                Compute gce = provider.getGoogleCompute();
                VLAN vlan = getVlan(vlanId);

                // Check if vlan contains any FW rules, and if so, revoke them.
                FirewallSupport fws = new FirewallSupport(provider);
                Collection<FirewallRule> rules = fws.getRules("fw-" + vlanId);
                for (FirewallRule rule : rules) {
                    fws.revoke(rule.getProviderRuleId());
                }

                // need to remove routes if present before vlan can be removed.
                RouteList routes = gce.routes().list(getContext().getAccountNumber()).execute();
                for (com.google.api.services.compute.model.Route route : routes.getItems()) {
                    if (route.getNetwork().replaceAll(".*/", "").equals(vlanId) && !route.getName().startsWith("default-route-")) {
                        op = gce.routes().delete(getContext().getAccountNumber(), route.getName()).execute();
                        method.getOperationComplete(provider.getContext(), op, GoogleOperationType.GLOBAL_OPERATION, "", "");
                    }
                }

                op = gce.networks().delete(provider.getContext().getAccountNumber(), vlan.getName()).execute();

                if(!method.getOperationComplete(provider.getContext(), op, GoogleOperationType.GLOBAL_OPERATION, "", "")){
                    throw new GeneralCloudException("An error occurred while removing network: " + vlanId + ": Operation timed out");
                }
    	    } catch (IOException ex) {
	            logger.error(ex.getMessage());
    			if (ex.getClass() == GoogleJsonResponseException.class) {
    				GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
    				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
    			} else {
					throw new GeneralCloudException("An error occurred while removing network: " + vlanId + ": " + ex.getMessage(), ex);
				}
			}
        }
        finally{
            APITrace.end();
        }
	}

    private @Nullable VLAN toVlan(Network network, ProviderContext ctx){
        VLAN vLan = new VLAN();
        //vLan.setProviderVlanId(network.getId() + ""); - GCE uses name as IDs
        vLan.setProviderOwnerId(provider.getContext().getAccountNumber());
        if (null != network.getName()) {
            vLan.setProviderVlanId(network.getName());
        }
        if (null != network.getName()) {
            vLan.setName(network.getName());
        }
        if (null != network.getSelfLink()) {
            vLan.setTag("contentLink", network.getSelfLink());
        }
        if (null != network.getIPv4Range()) {
            vLan.setCidr(network.getIPv4Range());
        }
        vLan.setDescription((network.getDescription() == null || network.getDescription().equals("")) ? network.getName() : network.getDescription());
        //VLANs in GCE don't have regions - using new VisibleScope variable instead
        //vLan.setProviderRegionId(ctx.getRegionId());
        vLan.setVisibleScope(VisibleScope.ACCOUNT_GLOBAL);
        vLan.setCurrentState(VLANState.AVAILABLE);
        vLan.setSupportedTraffic(IPVersion.IPV4);

        return vLan;
    }

    private @Nullable Route toRoute(com.google.api.services.compute.model.Route googleRoute){
        //TODO: This needs some work
        return Route.getRouteToVirtualMachine(IPVersion.IPV4, googleRoute.getDestRange(), provider.getContext().getAccountNumber(), googleRoute.getNextHopInstance());
    }

	@Override
	public void removeInternetGatewayTags(String internetGatewayId, Tag... tags)
			throws CloudException, InternalException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void removeRoutingTableTags(String routingTableId, Tag... tags)
			throws CloudException, InternalException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateRoutingTableTags(String routingTableId, Tag... tags)
			throws CloudException, InternalException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateInternetGatewayTags(String internetGatewayId, Tag... tags)
			throws CloudException, InternalException {
		// TODO Auto-generated method stub
		
	}
}
