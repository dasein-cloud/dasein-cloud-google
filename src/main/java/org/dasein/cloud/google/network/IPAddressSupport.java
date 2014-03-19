package org.dasein.cloud.google.network;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.Address;
import com.google.api.services.compute.model.AddressAggregatedList;
import com.google.api.services.compute.model.Operation;
import org.apache.log4j.Logger;
import org.dasein.cloud.*;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.GoogleMethod;
import org.dasein.cloud.google.GoogleOperationType;
import org.dasein.cloud.google.capabilities.GCEIPAddressCapabilities;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.network.*;
import org.dasein.cloud.util.APITrace;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IPAddressSupport implements IpAddressSupport {
    static private final Logger logger = Google.getLogger(IPAddressSupport.class);
    private Google provider = null;

    IPAddressSupport(Google provider){
        this.provider = provider;
    }

    @Override
    public void assign(@Nonnull String addressId, @Nonnull String serverId) throws InternalException, CloudException {
        APITrace.begin(provider, "IpAddress.assign");
        try{
            Compute gce = provider.getGoogleCompute();
            IpAddress ipAddress = getIpAddress(addressId);

            VirtualMachine vm = provider.getComputeServices().getVirtualMachineSupport().getVirtualMachine(serverId);

            AccessConfig accessConfig = new AccessConfig();
            accessConfig.setName("External NAT");
            accessConfig.setKind("compute#accessConfig");
            accessConfig.setType("ONE_TO_ONE_NAT");
            accessConfig.setNatIP(ipAddress.getRawAddress().getIpAddress());

            try{
                System.out.println(vm.getProviderDataCenterId());
                Operation job = gce.instances().addAccessConfig(provider.getContext().getAccountNumber(), vm.getProviderDataCenterId(), serverId, "nic0", accessConfig).execute();

                GoogleMethod method = new GoogleMethod(provider);
                if(!method.getOperationComplete(provider.getContext(), job, GoogleOperationType.ZONE_OPERATION, "", vm.getProviderDataCenterId())){
                    throw new CloudException("An error occurred assigning the IP: " + addressId + ": Operation timed out");
                }
            }
            catch(IOException ex){
                logger.error(ex.getMessage());
                throw new CloudException("An error occurred assigning the IP: " + addressId + ": " + ex.getMessage());
            }
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public void assignToNetworkInterface(@Nonnull String addressId, @Nonnull String nicId) throws InternalException, CloudException {
        throw new OperationNotSupportedException("GCE does not support NICs");
    }

    @Nonnull
    @Override
    public String forward(@Nonnull String addressId, int publicPort, @Nonnull Protocol protocol, int privatePort, @Nonnull String onServerId) throws InternalException, CloudException {
        throw new OperationNotSupportedException("Forwarding rules are not supported by GCE");
    }

    private transient volatile GCEIPAddressCapabilities capabilities;
    @Override
    public @Nonnull GCEIPAddressCapabilities getCapabilities(){
        if(capabilities == null){
            capabilities = new GCEIPAddressCapabilities(provider);
        }
        return capabilities;
    }

    @Nullable
    @Override
    public IpAddress getIpAddress(@Nonnull String addressId) throws InternalException, CloudException {
        APITrace.begin(provider, "IpAddress.getIpAddress");
        try{
            try{
                Compute gce = provider.getGoogleCompute();
                AddressAggregatedList addressList = gce.addresses().aggregatedList(provider.getContext().getAccountNumber()).setFilter("name eq " + addressId).execute();
                Iterator<String> regions = addressList.getItems().keySet().iterator();
                while(regions.hasNext()){
                    String region = regions.next();

                    for(Address address : addressList.getItems().get(region).getAddresses()){
                        if(address.getName().equals(addressId))return toIpAddress(address);
                    }
                }
            }
            catch(IOException ex){
                logger.error(ex.getMessage());
                throw new CloudException("An error occurred getting the IPAddress: " + ex.getMessage());
            }
            throw new InternalException("Could not find IPAddress: " + addressId);
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    @Deprecated
    public @Nonnull String getProviderTermForIpAddress(@Nonnull Locale locale) {
        return "Address";
    }

    @Override
    @Deprecated
    public @Nonnull Requirement identifyVlanForVlanIPRequirement() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Override
    @Deprecated
    public boolean isAssigned(@Nonnull AddressType type) {
        if(type.equals(AddressType.PUBLIC))return true;
        return false;
    }

    @Override
    @Deprecated
    public boolean isAssigned(@Nonnull IPVersion version) throws CloudException, InternalException {
        if(version.equals(IPVersion.IPV4))return true;
        return false;
    }

    @Override
    @Deprecated
    public boolean isAssignablePostLaunch(@Nonnull IPVersion version) throws CloudException, InternalException {
        return true;
    }

    @Override
    @Deprecated
    public boolean isForwarding() {
        return false;
    }

    @Override
    @Deprecated
    public boolean isForwarding(IPVersion version) throws CloudException, InternalException {
        if(version.equals(IPVersion.IPV4))return true;
        return false;
    }

    @Override
    @Deprecated
    public boolean isRequestable(@Nonnull AddressType type) {
        return true;
    }

    @Override
    @Deprecated
    public boolean isRequestable(@Nonnull IPVersion version) throws CloudException, InternalException {
        if(version.equals(IPVersion.IPV4))return true;
        return false;
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        return true;
    }

    @Nonnull
    @Override
    public Iterable<IpAddress> listPrivateIpPool(boolean unassignedOnly) throws InternalException, CloudException {
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public Iterable<IpAddress> listPublicIpPool(boolean unassignedOnly) throws InternalException, CloudException {
        return listIpPool(IPVersion.IPV4, unassignedOnly);
    }

    @Nonnull
    @Override
    public Iterable<IpAddress> listIpPool(@Nonnull IPVersion version, boolean unassignedOnly) throws InternalException, CloudException {
        APITrace.begin(provider, "IpAddress.listIpPool");
        try{
            if( !version.equals(IPVersion.IPV4) ) {
                return Collections.emptyList();
            }
            ProviderContext ctx = provider.getContext();

            if( ctx == null ) {
                throw new CloudException("No context was set for this request");
            }

            ArrayList<IpAddress> addresses = new ArrayList<IpAddress>();
            try{
                Compute gce = provider.getGoogleCompute();
                AddressAggregatedList addressList = gce.addresses().aggregatedList(provider.getContext().getAccountNumber()).execute();
                Iterator<String> regions = addressList.getItems().keySet().iterator();
                while(regions.hasNext()){
                    String region = regions.next();

                    for(Address address : addressList.getItems().get(region).getAddresses()){
                        IpAddress ipAddress = toIpAddress(address);
                        if(ipAddress == null)addresses.add(ipAddress);
                    }
                }
                return addresses;
            }
            catch(IOException ex){
                logger.error(ex.getMessage());
                throw new CloudException("An error occurred listing IPs: " + ex.getMessage());
            }
        }
        finally {
            APITrace.end();
        }
    }

    @Nonnull
    @Override
    public Iterable<ResourceStatus> listIpPoolStatus(@Nonnull IPVersion version) throws InternalException, CloudException {
        APITrace.begin(provider, "IpAddress.listIpPoolStatus");
        try{
            if( !version.equals(IPVersion.IPV4) ) {
                return Collections.emptyList();
            }
            ProviderContext ctx = provider.getContext();

            if( ctx == null ) {
                throw new CloudException("No context was set for this request");
            }

            ArrayList<ResourceStatus> statuses = new ArrayList<ResourceStatus>();
            try{
                Compute gce = provider.getGoogleCompute();
                AddressAggregatedList addressList = gce.addresses().aggregatedList(provider.getContext().getAccountNumber()).execute();
                Iterator<String> regions = addressList.getItems().keySet().iterator();
                while(regions.hasNext()){
                    String region = regions.next();

                    for(Address address : addressList.getItems().get(region).getAddresses()){
                        ResourceStatus status = toStatus(address);
                        if(status == null)statuses.add(status);
                    }
                }
                return statuses;
            }
            catch(IOException ex){
                logger.error(ex.getMessage());
                throw new CloudException("An error occurred listing IPs: " + ex.getMessage());
            }
        }
        finally {
            APITrace.end();
        }
    }

    @Nonnull
    @Override
    public Iterable<IpForwardingRule> listRules(@Nonnull String addressId) throws InternalException, CloudException {
        throw new OperationNotSupportedException("Forwarding rules are not supported by GCE");
    }

    @Nonnull
    @Override
    public Iterable<IPVersion> listSupportedIPVersions() throws CloudException, InternalException {
        return Collections.singletonList(IPVersion.IPV4);
    }

    @Override
    public void releaseFromPool(@Nonnull String addressId) throws InternalException, CloudException {
        APITrace.begin(provider, "IpAddress.releaseFromPool");
        try{
            try{
                IpAddress ipAddress = getIpAddress(addressId);
                Compute gce = provider.getGoogleCompute();
                Operation job = gce.addresses().delete(provider.getContext().getAccountNumber(), ipAddress.getRegionId(), addressId).execute();

                GoogleMethod method = new GoogleMethod(provider);
                if(!method.getOperationComplete(provider.getContext(), job, GoogleOperationType.REGION_OPERATION, ipAddress.getRegionId(), "")){
                    throw new CloudException("An error occurred releasing address: " + addressId + ": Operation timed out");
                }
            }
            catch(IOException ex){
                logger.error(ex.getMessage());
                throw new CloudException("An error occurred releasing address: " + addressId + ": " + ex.getMessage());
            }
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public void releaseFromServer(@Nonnull String addressId) throws InternalException, CloudException {
        APITrace.begin(provider, "IpAddress.releaseFromServer");
        try{
            Compute gce = provider.getGoogleCompute();
            try{
                Address address = gce.addresses().get(provider.getContext().getAccountNumber(), provider.getContext().getRegionId(), addressId).execute();
                String zone = "";
                String instance = "";
                for(String vm : address.getUsers()){
                    Pattern p = Pattern.compile("/zones/(.*?)/instances");
                    Matcher m = p.matcher(vm);
                    if(m.find()){
                        zone = m.group();
                        instance = vm;
                        break;
                    }
                }
                Operation job = gce.instances().deleteAccessConfig(provider.getContext().getAccountNumber(), zone, instance, "External NAT", "nic0").execute();

                GoogleMethod method = new GoogleMethod(provider);
                if(!method.getOperationComplete(provider.getContext(), job, GoogleOperationType.ZONE_OPERATION, "", zone)){
                    throw new CloudException("An error occurred releasing the address from the server: Operation timed out");
                }
            }
            catch(IOException ex){
                logger.error(ex.getMessage());
                throw new CloudException("An error occurred releasing the address from the server: " + ex.getMessage());
            }
        }
        finally {
            APITrace.end();
        }
    }

    @Nonnull
    @Override
    public String request(@Nonnull AddressType typeOfAddress) throws InternalException, CloudException {
        if(typeOfAddress.equals(AddressType.PUBLIC))return request(IPVersion.IPV4);
        else throw new CloudException("GCE only supports creation of public IP Addresses");
    }

    @Nonnull
    @Override
    public String request(@Nonnull IPVersion version) throws InternalException, CloudException {
        APITrace.begin(provider, "IpAddress.request");
        try{
            if(version.equals(IPVersion.IPV4)){
                Compute gce = provider.getGoogleCompute();

                try{
                    Address address = new Address();
                    address.setName("a" + UUID.randomUUID().toString());
                    Operation job = gce.addresses().insert(provider.getContext().getAccountNumber(), provider.getContext().getRegionId(), address).execute();

                    GoogleMethod method = new GoogleMethod(provider);
                    return method.getOperationTarget(provider.getContext(), job, GoogleOperationType.REGION_OPERATION, provider.getContext().getRegionId(), "", false);
                }
                catch(IOException ex){
                    logger.error(ex.getMessage());
                    throw new CloudException("An error occurred requesting an IPAddress: " + ex.getMessage());
                }
            }
            else throw new CloudException("GCE currently only supports IPv4");
        }
        finally {
            APITrace.end();
        }
    }

    @Nonnull
    @Override
    public String requestForVLAN(@Nonnull IPVersion version) throws InternalException, CloudException {
        throw new CloudException("GCE does not support manual creation of IP Addresses for VLANs");
    }

    @Nonnull
    @Override
    public String requestForVLAN(@Nonnull IPVersion version, @Nonnull String vlanId) throws InternalException, CloudException {
        throw new CloudException("GCE does not support manual creation of IP Addresses for VLANs");
    }

    @Override
    public void stopForward(@Nonnull String ruleId) throws InternalException, CloudException {
        throw new OperationNotSupportedException("Forwarding rules are not supported by GCE");
    }

    @Override
    public boolean supportsVLANAddresses(@Nonnull IPVersion ofVersion) throws InternalException, CloudException {
        return false;
    }

    private IpAddress toIpAddress(Address address){
        IpAddress ipAddress = new IpAddress();

        ipAddress.setIpAddressId(address.getName());
        ipAddress.setAddress(address.getAddress());
        ipAddress.setRegionId(address.getRegion());
        ipAddress.setAddressType(AddressType.PUBLIC);
        ipAddress.setVersion(IPVersion.IPV4);
        ipAddress.setForVlan(false);
        if(address.getUsers() != null && address.getUsers().size() > 0){
            for(String user : address.getUsers()){
                user = user.substring(user.lastIndexOf("/") + 1);
                ipAddress.setServerId(user);
            }
        }

        return ipAddress;
    }

    private ResourceStatus toStatus(Address address){
        return new ResourceStatus(address.getName(), address.getStatus().equals("RESERVED") ? false : true);
    }

    @Override
    public @Nonnull String[] mapServiceAction(@Nonnull ServiceAction action) {
        return new String[]{};
    }
}