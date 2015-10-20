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

/* Contains code based on example code from https://cloud.google.com/compute/docs/instances/automate-pw-generation 
 * which is licensed under the following license.
 * 
 * Copyright 2015 Google Inc. All Rights Reserved.
 *
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
 */




package org.dasein.cloud.google.compute.server;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.spec.RSAPublicKeySpec;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.crypto.Cipher;

import org.apache.log4j.Logger;
import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.Tag;
import org.dasein.cloud.VisibleScope;
import org.dasein.cloud.compute.AbstractVMSupport;
import org.dasein.cloud.compute.Architecture;
import org.dasein.cloud.compute.MachineImage;
import org.dasein.cloud.compute.Platform;
import org.dasein.cloud.compute.VMFilterOptions;
import org.dasein.cloud.compute.VMLaunchOptions;
import org.dasein.cloud.compute.VMScalingOptions;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.compute.VirtualMachineProduct;
import org.dasein.cloud.compute.VirtualMachineProductFilterOptions;
import org.dasein.cloud.compute.VmState;
import org.dasein.cloud.compute.VolumeAttachment;
import org.dasein.cloud.compute.VolumeCreateOptions;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.GoogleException;
import org.dasein.cloud.google.GoogleMethod;
import org.dasein.cloud.google.GoogleOperationType;
import org.dasein.cloud.google.capabilities.GCEInstanceCapabilities;
import org.dasein.cloud.network.RawAddress;
import org.dasein.cloud.network.VLAN;
import org.dasein.cloud.util.APITrace;
import org.dasein.cloud.util.Cache;
import org.dasein.cloud.util.CacheLevel;
import org.dasein.cloud.util.NamingConstraints;
import org.dasein.util.Jiterator;
import org.dasein.util.JiteratorPopulator;
import org.dasein.util.PopulatorThread;
import org.dasein.util.uom.storage.Gigabyte;
import org.dasein.util.uom.storage.Megabyte;
import org.dasein.util.uom.storage.Storage;
import org.dasein.util.uom.time.Day;
import org.dasein.util.uom.time.TimePeriod;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.simple.parser.JSONParser;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.AttachedDisk;
import com.google.api.services.compute.model.AttachedDiskInitializeParams;
import com.google.api.services.compute.model.Disk;
import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceAggregatedList;
import com.google.api.services.compute.model.MachineType;
import com.google.api.services.compute.model.MachineTypeAggregatedList;
import com.google.api.services.compute.model.MachineTypeList;
import com.google.api.services.compute.model.Metadata;
import com.google.api.services.compute.model.Metadata.Items;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.Scheduling;
import com.google.api.services.compute.model.SerialPortOutput;
import com.google.api.services.compute.model.Tags;
import com.google.common.io.BaseEncoding;

public class ServerSupport extends AbstractVMSupport<Google> {

	private Google provider;
	static private final Logger logger = Google.getLogger(ServerSupport.class);
	private Cache<MachineTypeAggregatedList> machineTypesCache;
	public ServerSupport(Google provider){
        super(provider);
        this.provider = provider;
        machineTypesCache = Cache.getInstance(provider, "MachineTypes", MachineTypeAggregatedList.class, CacheLevel.CLOUD, new TimePeriod<Day>(1, TimePeriod.DAY));
    }

	@Override
	public VirtualMachine alterVirtualMachine(@Nonnull String vmId, @Nonnull VMScalingOptions options) throws InternalException, CloudException {
		throw new OperationNotSupportedException("GCE does not support altering of existing instances.");
	}

    @Override
    public VirtualMachine modifyInstance(@Nonnull String vmId, @Nonnull String[] firewalls) throws InternalException, CloudException{
        throw new OperationNotSupportedException("GCE does not support altering of existing instances.");
    }

	@Override
	public @Nonnull VirtualMachine clone(@Nonnull String vmId, @Nonnull String intoDcId, @Nonnull String name, @Nonnull String description, boolean powerOn, String... firewallIds) throws InternalException, CloudException {
		throw new OperationNotSupportedException("GCE does not support cloning of instances via the API.");
	}

	@Override
	public void disableAnalytics(@Nonnull String vmId) throws InternalException, CloudException {
        throw new OperationNotSupportedException("GCE does not currently support analytics.");
	}

	@Override
	public void enableAnalytics(@Nonnull String vmId) throws InternalException, CloudException {
        throw new OperationNotSupportedException("GCE does not currently support analytics.");
	}

    private transient volatile GCEInstanceCapabilities capabilities;
    @Override
    public @Nonnull GCEInstanceCapabilities getCapabilities(){
        if( capabilities == null ) {
            capabilities = new GCEInstanceCapabilities(provider);
        }
        return capabilities;
    }

    @Override
    public String getPassword(@Nonnull String vmId) throws InternalException, CloudException {
        throw new OperationNotSupportedException("GCE instances do not have passwords");
    }

    public @Nonnull String getVmNameFromId(String vmId) throws InternalException, CloudException {
        if (null == vmId) {
            throw new InternalException("vmId cannot be null");
        }

        if (vmId.contains("_")) {
            String[] parts = vmId.split("_");
            if (null == parts[0]) {
                throw new InternalException("vmId cannot begin with '_'");
            }
            return parts[0];
        } else {
            return vmId;
        }
    }

    public @Nonnull String getVmIdFromName(String vmName) throws InternalException, CloudException {
        if (null == vmName) {
            throw new InternalException("vmName cannot be null ");
        }
        VirtualMachine vm = getVirtualMachine(vmName);
        if ((null != vm) && (null != vm.getProviderVirtualMachineId())) {
            return vm.getProviderVirtualMachineId();
        } else {
            throw new CloudException("Unable to lookup vmId for vm named: " + vmName);
        }
    }

	@Override
	public @Nonnull String getConsoleOutput(@Nonnull String vmId) throws InternalException, CloudException {
		try{
            for(VirtualMachine vm : listVirtualMachines()){
                if(vm.getProviderVirtualMachineId().equalsIgnoreCase(vmId)){
                    Compute gce = provider.getGoogleCompute();
                    SerialPortOutput output = gce.instances().getSerialPortOutput(provider.getContext().getAccountNumber(), vm.getProviderDataCenterId(), getVmNameFromId(vmId)).execute();
                    return output.getContents();
                }
            }
		} catch (IOException ex) {
			logger.error(ex.getMessage());
			if (ex.getClass() == GoogleJsonResponseException.class) {
				GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
			} else
				throw new CloudException("An error occurred when getting console output for VM: " + vmId + ": " + ex.getMessage());
		}
        throw new InternalException("The Virtual Machine: " + vmId + " could not be found.");
	}

	@Override
	public VirtualMachineProduct getProduct(@Nonnull String productId) throws InternalException, CloudException {
        try{
            Compute gce = provider.getGoogleCompute();
            String[] parts = productId.split("\\+");
            if ((parts != null) && (parts.length > 1)) {
                MachineTypeList types = gce.machineTypes().list(provider.getContext().getAccountNumber(), parts[1]).setFilter("name eq " + parts[0]).execute();
                if ((null != types) && (null != types.getItems())) {
                    for(MachineType type : types.getItems()){
                        if(parts[0].equals(type.getName()))return toProduct(type);
                    }
                }
            }
            return null;  // Tests indicate null should come back, rather than exception
            //throw new CloudException("The product: " + productId + " could not be found.");
		} catch (IOException ex) {
			logger.error(ex.getMessage());
			if (ex.getClass() == GoogleJsonResponseException.class) {
				GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
			} else
				throw new CloudException("An error occurred retrieving the product: " + productId + ": " + ex.getMessage());
		}
	}

	@Override
	public VirtualMachine getVirtualMachine(@Nonnull String vmId)throws InternalException, CloudException {
        APITrace.begin(getProvider(), "getVirtualMachine");
        try{
            try{
                Compute gce = provider.getGoogleCompute();
                InstanceAggregatedList instances = gce.instances().aggregatedList(provider.getContext().getAccountNumber()).setFilter("name eq " + getVmNameFromId(vmId)).execute();
                Iterator<String> it = instances.getItems().keySet().iterator();
                while (it.hasNext()){
                    String zone = it.next();
                    if(instances.getItems() != null && instances.getItems().get(zone) != null && instances.getItems().get(zone).getInstances() != null){
                        for(Instance instance : instances.getItems().get(zone).getInstances()){
                            if(instance.getName().equals(getVmNameFromId(vmId))) {
                                return toVirtualMachine(instance);
                            }
                        }
                    }
                }
                return null; // not found
            } catch (IOException ex) {
				logger.error(ex.getMessage());
				if (ex.getClass() == GoogleJsonResponseException.class) {
					GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
					throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
				} else
					throw new CloudException("An error occurred retrieving VM: " + vmId + ": " + ex.getMessage());
			}
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        listVirtualMachines();
        return true;
    }

    public void validateLaunchOptions(@Nonnull VMLaunchOptions withLaunchOptions) throws CloudException, InternalException {

        if (withLaunchOptions.getDataCenterId() == null || withLaunchOptions.getDataCenterId().equals("")) {
            throw new InternalException("A datacenter must be specified when launching an instance");
        }

        if (withLaunchOptions.getMachineImageId() == null || withLaunchOptions.getMachineImageId().equals("")) {
            throw new InternalException("A MachineImage must be specified when launching an instance");
        }

        if (withLaunchOptions.getHostName() == null || withLaunchOptions.getHostName().equals("")) {
            throw new InternalException("A hostname must be specified when launching an instance");
        }

        if (withLaunchOptions.getVlanId() == null || withLaunchOptions.getVlanId().equals("")) {
            throw new InternalException("A vlan must be specified when launching an instance");
        } else {
            VLAN vlan = provider.getNetworkServices().getVlanSupport().getVlan(withLaunchOptions.getVlanId());
            if ((null == vlan) || (null == vlan.getTag("contentLink"))) {
                throw new InternalException("Problem getting Vlan for " + withLaunchOptions.getVlanId());
            }
        }

        String hostName = getCapabilities().getVirtualMachineNamingConstraints().convertToValidName(withLaunchOptions.getHostName(), Locale.US);
        if (null != provider.getComputeServices().getVolumeSupport().getVolume(hostName)) {
            throw new InternalException("Root disk " + hostName + " already exists.");
        }
    }

    @Override
    public @Nonnull VirtualMachine launch(@Nonnull VMLaunchOptions withLaunchOptions) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "launchVM"); //  windows-cloud_windows-server-2012-r2-dc-v20150629

        validateLaunchOptions(withLaunchOptions); // this will exception out on problem.
        boolean windows = false;
        try{
            Compute gce = provider.getGoogleCompute();
            GoogleMethod method = new GoogleMethod(provider);

            String hostName = getCapabilities().getVirtualMachineNamingConstraints().convertToValidName(withLaunchOptions.getHostName(), Locale.US);
            Instance instance = new Instance();
            instance.setName(hostName);
            instance.setDescription(withLaunchOptions.getDescription());
            if (withLaunchOptions.getStandardProductId().contains("+")) {
                instance.setMachineType(getProduct(withLaunchOptions.getStandardProductId()).getDescription());
            } else {
                instance.setMachineType(getProduct(withLaunchOptions.getStandardProductId() + "+" + withLaunchOptions.getDataCenterId()).getDescription());
            }
            MachineImage image = provider.getComputeServices().getImageSupport().getImage(withLaunchOptions.getMachineImageId());

            AttachedDisk rootVolume = new AttachedDisk();
            rootVolume.setBoot(Boolean.TRUE);
            rootVolume.setType("PERSISTENT");
            rootVolume.setMode("READ_WRITE");
            AttachedDiskInitializeParams params = new AttachedDiskInitializeParams();
            // do not use withLaunchOptions.getFriendlyName() it is non compliant!!!
            params.setDiskName(hostName);
            // Not Optimum solution, update in core should come next release to have this be part of MachineImage

            try {
                String[] parts = withLaunchOptions.getMachineImageId().split("_");
                Image img = gce.images().get(parts[0], parts[1]).execute();

                windows = guessWindows(img);

                Long size = img.getDiskSizeGb();
                String diskSizeGb = size.toString();
                if (null == diskSizeGb) {
                    diskSizeGb = img.getUnknownKeys().get("diskSizeGb").toString();
                }
                Long MinimumDiskSizeGb = Long.valueOf(diskSizeGb).longValue();
                params.setDiskSizeGb(MinimumDiskSizeGb); 
            } catch ( Exception e ) {
                params.setDiskSizeGb(10L);
            }
            if ((image != null) && (image.getTag("contentLink") != null))
                params.setSourceImage((String)image.getTag("contentLink"));
            else
                throw new CloudException("Problem getting the contentLink tag value from the image for " + withLaunchOptions.getMachineImageId());
            rootVolume.setInitializeParams(params);

            List<AttachedDisk> attachedDisks = new ArrayList<AttachedDisk>();
            attachedDisks.add(rootVolume);

            if (withLaunchOptions.getVolumes().length > 0) {
                for (VolumeAttachment volume : withLaunchOptions.getVolumes()) {
                    AttachedDisk vol = new AttachedDisk();
                    vol.setBoot(Boolean.FALSE);
                    vol.setType("PERSISTENT");
                    vol.setMode("READ_WRITE");
                    vol.setAutoDelete(Boolean.FALSE);
                    vol.setKind("compute#attachedDisk");
                    if (null != volume.getExistingVolumeId()) {
                        vol.setDeviceName(volume.getExistingVolumeId());
                        vol.setSource(provider.getComputeServices().getVolumeSupport().getVolume(volume.getExistingVolumeId()).getMediaLink());
                    } else {
                        VolumeCreateOptions volumeOptions = volume.getVolumeToCreate();
                        volumeOptions.setDataCenterId(withLaunchOptions.getDataCenterId());
                        String newDisk = provider.getComputeServices().getVolumeSupport().createVolume(volume.getVolumeToCreate());
                        vol.setDeviceName(newDisk);
                        vol.setSource(provider.getComputeServices().getVolumeSupport().getVolume(newDisk).getMediaLink());
                    }
                    attachedDisks.add(vol);
                }
            }

            instance.setDisks(attachedDisks);

            AccessConfig nicConfig = new AccessConfig();
            nicConfig.setName("External NAT");
            nicConfig.setType("ONE_TO_ONE_NAT");//Currently the only type supported
            if (withLaunchOptions.getStaticIpIds().length > 0) {
                nicConfig.setNatIP(withLaunchOptions.getStaticIpIds()[0]);
            }
            List<AccessConfig> accessConfigs = new ArrayList<AccessConfig>();
            accessConfigs.add(nicConfig);

            NetworkInterface nic = new NetworkInterface();
            nic.setName("nic0");
            if (null != withLaunchOptions.getVlanId()) {
                VLAN vlan = provider.getNetworkServices().getVlanSupport().getVlan(withLaunchOptions.getVlanId());
                nic.setNetwork(vlan.getTag("contentLink"));
            } else {
                nic.setNetwork(provider.getNetworkServices().getVlanSupport().getVlan("default").getTag("contentLink"));
            }
            nic.setAccessConfigs(accessConfigs);
            List<NetworkInterface> nics = new ArrayList<NetworkInterface>();
            nics.add(nic);
            instance.setNetworkInterfaces(nics);
            instance.setCanIpForward(Boolean.FALSE);

            Scheduling scheduling = new Scheduling();
            scheduling.setAutomaticRestart(Boolean.TRUE);
            scheduling.setOnHostMaintenance("TERMINATE");
            instance.setScheduling(scheduling);

            Map<String,String> keyValues = new HashMap<String, String>();
            if(withLaunchOptions.getBootstrapUser() != null && withLaunchOptions.getBootstrapKey() != null && !withLaunchOptions.getBootstrapUser().equals("") && !withLaunchOptions.getBootstrapKey().equals("")){
                keyValues.put("sshKeys", withLaunchOptions.getBootstrapUser() + ":" + withLaunchOptions.getBootstrapKey());
            }
            if(!withLaunchOptions.getMetaData().isEmpty()) {
                for( Map.Entry<String,Object> entry : withLaunchOptions.getMetaData().entrySet() ) {
                    keyValues.put(entry.getKey(), (String)entry.getValue());
                }
            }
            if (!keyValues.isEmpty()) {
                Metadata metadata = new Metadata();
                ArrayList<Metadata.Items> items = new ArrayList<Metadata.Items>();

                for (Map.Entry<String, String> entry : keyValues.entrySet()) {
                    Metadata.Items item = new Metadata.Items();
                    item.set("key", entry.getKey());
                    if ((entry.getValue() == null) || (entry.getValue().isEmpty() == true) || (entry.getValue().equals("")))
                        item.set("value", ""); // GCE HATES nulls...
                    else 
                        item.set("value", entry.getValue());
                    items.add(item);
                }
                // https://github.com/GoogleCloudPlatform/compute-image-packages/tree/master/google-startup-scripts
                if (null != withLaunchOptions.getUserData()) {
                    Metadata.Items item = new Metadata.Items();
                    item.set("key", "startup-script");
                    item.set("value", withLaunchOptions.getUserData());
                    items.add(item);
                }
                metadata.setItems(items);
                instance.setMetadata(metadata);
            }

            Tags tags = new Tags();
            ArrayList<String> tagItems = new ArrayList<String>();
            tagItems.add(hostName); // Each tag must be 1-63 characters long, and comply with RFC1035
            tags.setItems(tagItems);
            instance.setTags(tags);

            String vmId = "";
            try {
                Operation job = gce.instances().insert(provider.getContext().getAccountNumber(), withLaunchOptions.getDataCenterId(), instance).execute();
                vmId = method.getOperationTarget(provider.getContext(), job, GoogleOperationType.ZONE_OPERATION, "", withLaunchOptions.getDataCenterId(), false);
            } catch (IOException ex) {
				if (ex.getClass() == GoogleJsonResponseException.class) {
					GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
					throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
				} else
					throw new CloudException("An error occurred launching the instance: " + ex.getMessage());
			} catch (Exception e) {
			    if ((e.getMessage().contains("The resource")) && 
                        (e.getMessage().contains("disks")) &&
                        (e.getMessage().contains("already exists"))) {
			        throw new CloudException("A disk named '" + withLaunchOptions.getFriendlyName() + "' already exists.");
			    } else {
			        throw new CloudException(e);
			    }
			}

            if (!vmId.equals("")) {
                VirtualMachine vm = getVirtualMachine(vmId);

                if (windows) {
                    // Generate the public/private key pair for encryption and decryption.
                    KeyPair keys = null;
                    try {
                        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
                        keyGen.initialize(2048); 

                        keys = keyGen.genKeyPair();
                    } catch ( NoSuchAlgorithmException e ) { 
                        throw new InternalException(e);
                    }

                    resetPassword(vmId, withLaunchOptions.getDataCenterId(), keys);

                    int retryCount = 20;
                    while (retryCount-- > 0) {
                        SerialPortOutput output = null;
                        try {
                            output = gce.instances().getSerialPortOutput(provider.getContext().getAccountNumber(), withLaunchOptions.getDataCenterId(), vmId).setPort(4).execute();
                        } catch ( IOException e ) { 
                            throw new CloudException(e);
                        }
                        // Get the last line - this will be a JSON string corresponding to the most recent password reset attempt.
                        String[] entries = output.getContents().split("\n");
                        String outputEntry = entries[entries.length - 1];

                        // Parse output using the json-simple library.
                        JSONParser parser = new JSONParser();
                        try {
                            org.json.simple.JSONObject passwordDict = (org.json.simple.JSONObject)parser.parse(outputEntry);
                            vm.setRootUser(passwordDict.get("userName").toString());
                            vm.setRootPassword(decryptPassword(passwordDict.get("encryptedPassword").toString(), keys));
                            break;
                        } catch ( Exception e ) { } // ignore exception, just means metadata not yet avail.

                        try {
                            Thread.sleep(10000);
                        } catch ( InterruptedException e ) { }
                    }
                }
                return vm;
            } else {
                throw new CloudException("Could not find the instance: " + withLaunchOptions.getFriendlyName() + " after launch.");
            }
        }
        finally {
            APITrace.end();
        }
    }

    private boolean guessWindows(Image img) {
        for (String license : img.getLicenses()) {
            if (license.contains("windows")) {
                return true;
            }
        }

        if (img.getDescription().toLowerCase().contains("windows") ||
            img.getName().toLowerCase().contains("windows")) {
            return true;
        }

        return false;
    }

    @Override
    public @Nonnull Iterable<String> listFirewalls(@Nonnull String vmId) throws InternalException, CloudException {
        ArrayList<String> firewalls = new ArrayList<String>();
        for(org.dasein.cloud.network.Firewall firewall : provider.getNetworkServices().getFirewallSupport().list()){
            for(String key : firewall.getTags().keySet()){
                if (firewall.getTags().get(key).equals(getVmNameFromId(vmId))) {
                    firewalls.add(firewall.getName());
                }
            }
        }
        return firewalls;
    }

    @Override
    public @Nonnull Iterable<VirtualMachineProduct> listAllProducts() throws CloudException, InternalException{
        return listProducts(VirtualMachineProductFilterOptions.getInstance(), null);
    }

	public @Nonnull Iterable<VirtualMachineProduct> listProducts(@Nonnull Architecture architecture, String preferredDataCenterId) throws InternalException, CloudException {
        MachineTypeAggregatedList machineTypes = null;

        Compute gce = provider.getGoogleCompute();
        Iterable<MachineTypeAggregatedList> machineTypesCachedList = machineTypesCache.get(provider.getContext());

        if (machineTypesCachedList != null) {
            Iterator<MachineTypeAggregatedList> machineTypesCachedListIterator = machineTypesCachedList.iterator();
            if (machineTypesCachedListIterator.hasNext())
                machineTypes = machineTypesCachedListIterator.next();
        } else {
            try {
                machineTypes = gce.machineTypes().aggregatedList(provider.getContext().getAccountNumber()).execute();
                machineTypesCache.put(provider.getContext(), Arrays.asList(machineTypes));
            } catch (IOException ex) {
                logger.error(ex.getMessage());
                if (ex.getClass() == GoogleJsonResponseException.class) {
                    GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                    throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
                } else
                    throw new CloudException("An error occurred listing VM products.");
            }
        }

        Collection<VirtualMachineProduct> products = new ArrayList<VirtualMachineProduct>();

        Iterator<String> it = machineTypes.getItems().keySet().iterator();
        while(it.hasNext()){
            Object dataCenterId = it.next();
            if ((preferredDataCenterId == null) || (dataCenterId.toString().endsWith(preferredDataCenterId)))
                for(MachineType type : machineTypes.getItems().get(dataCenterId).getMachineTypes()){
                   //TODO: Filter out deprecated states somehow
                   if ((preferredDataCenterId == null) || (type.getZone().equals(preferredDataCenterId))) {
                       VirtualMachineProduct product = toProduct(type);
                       products.add(product);
                   }
               }
        }

        return products;
    }

    @Override
    public @Nonnull Iterable<VirtualMachineProduct> listProducts(@Nonnull String machineImageId, @Nonnull VirtualMachineProductFilterOptions options) throws InternalException, CloudException{
        Collection<VirtualMachineProduct> products = new ArrayList<VirtualMachineProduct>();

        Iterable<VirtualMachineProduct> candidateProduct = listProducts(options, null);
        for (VirtualMachineProduct product : candidateProduct) {
            if (options == null || options.matches(product)) {
                products.add(product);
            }
        }
        return products;  
    }

    private Iterable<VirtualMachineProduct> listProducts(VirtualMachineProductFilterOptions options, Architecture architecture) throws InternalException, CloudException{
        if ((architecture == null) || (Architecture.I64 == architecture)) { // GCE only has I64 architecture
            String dataCenterId = null;
            if (options != null)
                dataCenterId = options.getDataCenterId();
            Iterable<VirtualMachineProduct> result = listProducts(Architecture.I64, dataCenterId);
            return result;
        } else
            return new ArrayList<VirtualMachineProduct>(); // empty!
    }

	@Override
	public @Nonnull Iterable<VirtualMachine> listVirtualMachines(VMFilterOptions options)throws InternalException, CloudException {
        APITrace.begin(getProvider(), "listVirtualMachines");
        try{
            try{
                ArrayList<VirtualMachine> vms = new ArrayList<VirtualMachine>();
                Compute gce = provider.getGoogleCompute();
                InstanceAggregatedList instances = gce.instances().aggregatedList(provider.getContext().getAccountNumber()).execute();
                Iterator<String> it = instances.getItems().keySet().iterator();
                while(it.hasNext()){
                    String zone = it.next();
                    if(getContext().getRegionId().equals(provider.getDataCenterServices().getRegionFromZone(zone))){
                        if(instances.getItems() != null && instances.getItems().get(zone) != null && instances.getItems().get(zone).getInstances() != null){
                            for(Instance instance : instances.getItems().get(zone).getInstances()){
                                VirtualMachine vm = toVirtualMachine(instance);
                                if (options == null || options.matches(vm)) {
                                    vms.add(vm);
                                }
                            }
                        }
                    }
                }
                return vms;
	        } catch (IOException ex) {
				logger.error(ex.getMessage());
				if (ex.getClass() == GoogleJsonResponseException.class) {
					GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
					throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
				} else
					throw new CloudException("An error occurred while listing Virtual Machines.");
			}
        }
        finally{
            APITrace.end();
        }
	}

    @Override
    public @Nonnull Iterable<VirtualMachine> listVirtualMachines()throws InternalException, CloudException {
        VMFilterOptions options = VMFilterOptions.getInstance();
        return listVirtualMachines(options);
    }

    @Override
    public @Nonnull Iterable<ResourceStatus> listVirtualMachineStatus() throws InternalException, CloudException {
        ArrayList<ResourceStatus> vmStatuses = new ArrayList<ResourceStatus>();
        for(VirtualMachine vm : listVirtualMachines()){
            ResourceStatus status = new ResourceStatus(vm.getProviderVirtualMachineId(), vm.getCurrentState());
            vmStatuses.add(status);
        }
        return vmStatuses;
    }

	@Override
	public void pause(@Nonnull String vmId) throws InternalException, CloudException {
		throw new OperationNotSupportedException("GCE does not support pausing vms.");
	}

	@Override
	public void reboot(@Nonnull String vmId) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "rebootVM");
        try{
            try{
                Operation job = null;
                String zone = null;
                for (VirtualMachine vm : listVirtualMachines()) {
                    if (vm.getProviderVirtualMachineId().equalsIgnoreCase(vmId)) {
                        zone = vm.getProviderDataCenterId();
                        Compute gce = provider.getGoogleCompute();
                        job = gce.instances().reset(provider.getContext().getAccountNumber(), vm.getProviderDataCenterId(), getVmNameFromId(vmId)).execute();
                        break;
                    }
                }
                if(job != null){
                    GoogleMethod method = new GoogleMethod(provider);
                    method.getOperationComplete(provider.getContext(), job, GoogleOperationType.ZONE_OPERATION, null, zone);
                }
	        } catch (IOException ex) {
				logger.error(ex.getMessage());
				if (ex.getClass() == GoogleJsonResponseException.class) {
					GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
					throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
				} else
					throw new CloudException("An error occurred while rebooting VM: " + vmId + ": " + ex.getMessage());
			}
        }
        finally{
            APITrace.end();
        }
	}

	@Override
	public void resume(@Nonnull String vmId) throws CloudException, InternalException {
		throw new OperationNotSupportedException("GCE does not support suspend/resume of instances.");
	}

    @Override
    public void suspend(@Nonnull String vmId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("GCE does not support suspend/resume of instances.");
    }

    @Override
    public void start(@Nonnull String vmId) throws InternalException, CloudException {
        Compute gce = provider.getGoogleCompute();
        try {
            VirtualMachine vm = getVirtualMachine(vmId);
            gce.instances().start(provider.getContext().getAccountNumber(), vm.getProviderDataCenterId(), getVmNameFromId(vmId)).execute();
        } catch (IOException ex) {
            logger.error(ex.getMessage());
            if (ex.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException("An error occurred while rebooting VM: " + vmId + ": " + ex.getMessage());
        }
    }

    @Override
    public void stop(@Nonnull String vmId, boolean force) throws InternalException, CloudException {
        Compute gce = provider.getGoogleCompute();
        try {
            VirtualMachine vm = getVirtualMachine(vmId);
            gce.instances().stop(provider.getContext().getAccountNumber(), vm.getProviderDataCenterId(), getVmNameFromId(vmId)).execute();
        } catch (IOException ex) {
            logger.error(ex.getMessage());
            if (ex.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException("An error occurred while rebooting VM: " + vmId + ": " + ex.getMessage());
        }
    }

    @Override
    public void terminate(@Nonnull String vmId) throws InternalException, CloudException {
        VirtualMachine vm = getVirtualMachine(vmId);
        terminateVm(vmId);
        terminateVmDisk(getVmNameFromId(vmId), vm.getProviderDataCenterId());
    }

    @Override
    public void terminate(@Nonnull String vmId, String reason) throws InternalException, CloudException{
        VirtualMachine vm = getVirtualMachine(vmId);
        terminateVm(vmId, null);
        terminateVmDisk(vmId, vm.getProviderDataCenterId());
    }

    public void terminateVm(@Nonnull String vmId) throws InternalException, CloudException {
        terminateVm(vmId, null);
    }

    public void terminateVm(@Nonnull String vmId, String reason) throws InternalException, CloudException {
        try {
            APITrace.begin(getProvider(), "terminateVM");
            Operation job = null;
            GoogleMethod method = null;
            String zone = null;
            Compute gce = provider.getGoogleCompute();
            VirtualMachine vm = getVirtualMachine(vmId);

            if (null == vm) {
                throw new CloudException("Virtual Machine " + vmId + " was not found.");
            }

            try {
                zone = vm.getProviderDataCenterId();
                job = gce.instances().delete(provider.getContext().getAccountNumber(), zone, getVmNameFromId(vmId)).execute();
                if(job != null) {
                    method = new GoogleMethod(provider);
                    if (false == method.getOperationComplete(provider.getContext(), job, GoogleOperationType.ZONE_OPERATION, null, zone)) {
                        throw new CloudException("An error occurred while terminating the VM. Note: The root disk might also still exist");
                    }
                }
            } catch (IOException ex) {
                logger.error(ex.getMessage());
                if (ex.getClass() == GoogleJsonResponseException.class) {
                    GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                    throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
                } else
                    throw new CloudException("An error occurred while terminating VM: " + vmId + ": " + ex.getMessage());
            } catch (Exception ex) {
                throw new CloudException(ex); // catch exception from getOperationComplete
            }

        } finally {
            APITrace.end();
        }
    }

    public void terminateVmDisk(@Nonnull String diskName, String zone) throws InternalException, CloudException {
        try {
            APITrace.begin(getProvider(), "terminateVM");
            try {
                Compute gce = provider.getGoogleCompute();
                Operation job = gce.disks().delete(provider.getContext().getAccountNumber(), zone, diskName).execute();
                GoogleMethod method = new GoogleMethod(provider);
                method.getOperationComplete(provider.getContext(), job, GoogleOperationType.ZONE_OPERATION, null, zone);
            } catch (IOException ex) {
                if (ex.getClass() == GoogleJsonResponseException.class) {
                    GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                    if ((404 == gjre.getStatusCode()) &&
                        (gjre.getStatusMessage().equals("Not Found"))) {
                        // remain silent. this happens when instance is created with delete root volume on terminate is selected.
                        //throw new CloudException("Virtual Machine disk image '" + vmId + "' was not found.");
                    } else {
                        throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
                    }
                } else
                    throw new CloudException("An error occurred while deleting VM disk: " + diskName + ": " + ex.getMessage());
            } catch (Exception ex) {
                throw new CloudException(ex); // catch exception from getOperationComplete
            }
        }
        finally{
            APITrace.end();
        }
    }

	@Override
	public void unpause(@Nonnull String vmId) throws CloudException, InternalException {
		throw new OperationNotSupportedException("GCE does not support unpausing vms.");
	}

	@Override
	public void updateTags(String vmId, Tag... tags) throws CloudException, InternalException {
		updateTags(new String[]{vmId}, tags);
	}

	@Override
	public void updateTags(String[] vmIds, Tag... tags) throws CloudException, InternalException {
        //TODO: Implement me
	}

	@Override
	public void removeTags(String vmId, Tag... tags) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google does not support removing meta data from vms");
	}

	@Override
	public void removeTags(String[] vmIds, Tag... tags) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google does not support removing meta data from vms");
	}

    private VirtualMachine toVirtualMachine(Instance instance) throws InternalException, CloudException{
        VirtualMachine vm = new VirtualMachine();
        vm.setProviderVirtualMachineId(instance.getName() + "_" + instance.getId().toString());
        vm.setName(instance.getName());
        if (instance.getDescription() != null) {
            vm.setDescription(instance.getDescription());
        } else {
            vm.setDescription(instance.getName());
        }
        vm.setProviderOwnerId(provider.getContext().getAccountNumber());

        VmState vmState = null;
        if (instance.getStatus().equalsIgnoreCase("provisioning") || 
            instance.getStatus().equalsIgnoreCase("staging")) {
            if ((null != instance.getStatusMessage()) && (instance.getStatusMessage().contains("failed"))) {
                vmState = VmState.ERROR;
            } else {
                vmState = VmState.PENDING;
            }
        } else if (instance.getStatus().equalsIgnoreCase("stopping")) {
            vmState = VmState.STOPPING;
        } else if (instance.getStatus().equalsIgnoreCase("terminated")) {
            vmState = VmState.STOPPED;
        } else {
            vmState = VmState.RUNNING;
        }
        vm.setCurrentState(vmState);
        String regionId = "";
        try {
            regionId = provider.getDataCenterServices().getRegionFromZone(instance.getZone().substring(instance.getZone().lastIndexOf("/") + 1));
        }
        catch (Exception ex) {
            logger.error("An error occurred getting the region for the instance");
            return null;
        }
        vm.setProviderRegionId(regionId);
        String zone = instance.getZone();
        zone = zone.substring(zone.lastIndexOf("/") + 1);
        vm.setProviderDataCenterId(zone);

        DateTimeFormatter fmt = ISODateTimeFormat.dateTime();
        DateTime dt = DateTime.parse(instance.getCreationTimestamp(), fmt);
        vm.setCreationTimestamp(dt.toDate().getTime());

        if (instance.getDisks() != null) {
            for (AttachedDisk disk : instance.getDisks()) {
                if (disk != null && disk.getBoot() != null && disk.getBoot()) {
                    String diskName = disk.getSource().substring(disk.getSource().lastIndexOf("/") + 1);
                    Compute gce = provider.getGoogleCompute();
                    try {
                        Disk sourceDisk = gce.disks().get(provider.getContext().getAccountNumber(), zone, diskName).execute();
                        if (sourceDisk != null && sourceDisk.getSourceImage() != null) {
                            String project = "";
                            Pattern p = Pattern.compile("/projects/(.*?)/");
                            Matcher m = p.matcher(sourceDisk.getSourceImage());
                            while(m.find()){
                                project = m.group(1);
                                break;
                            }
                            vm.setProviderMachineImageId(project + "_" + sourceDisk.getSourceImage().substring(sourceDisk.getSourceImage().lastIndexOf("/") + 1));
                        }
                    } catch (IOException ex) {
                        logger.error(ex.getMessage());
                        if (ex.getClass() == GoogleJsonResponseException.class) {
                            GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                            throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
                        } else
                            throw new InternalException("IOException: " + ex.getMessage());
                    }
                }
            }
        }

        String machineTypeName = instance.getMachineType().substring(instance.getMachineType().lastIndexOf("/") + 1);
        vm.setProductId(machineTypeName + "+" + zone);

        ArrayList<RawAddress> publicAddresses = new ArrayList<RawAddress>();
        ArrayList<RawAddress> privateAddresses = new ArrayList<RawAddress>();
        boolean firstPass = true;
        boolean isSet = false;
        String providerAssignedIpAddressId = null;
        for (NetworkInterface nic : instance.getNetworkInterfaces()) {
            if (firstPass) {
                vm.setProviderVlanId(nic.getNetwork().substring(nic.getNetwork().lastIndexOf("/") + 1));
                firstPass = false;
            }
            if (nic.getNetworkIP() != null) {
                privateAddresses.add(new RawAddress(nic.getNetworkIP()));
            }
            if (nic.getAccessConfigs() != null && !nic.getAccessConfigs().isEmpty()) {
                for (AccessConfig accessConfig : nic.getAccessConfigs()) {
                    if (accessConfig.getNatIP() != null) {
                        publicAddresses.add(new RawAddress(accessConfig.getNatIP()));
                        if (!isSet) {
                            try {
                                isSet = true;
                                providerAssignedIpAddressId = provider.getNetworkServices().getIpAddressSupport().getIpAddressIdFromIP(accessConfig.getNatIP(), regionId);
                            } catch(InternalException ex) {
                                /*Likely to be an ephemeral IP*/
                            }
                        }
                    }
                }
            }
        }

        if(instance.getMetadata() != null && instance.getMetadata().getItems() != null){
            for (Items metadataItem : instance.getMetadata().getItems()) {
                if (metadataItem.getKey().equals("sshKeys")) {
                    vm.setRootUser(metadataItem.getValue().replaceAll(":.*", ""));
                }
            }
        }

        vm.setPublicAddresses(publicAddresses.toArray(new RawAddress[publicAddresses.size()]));
        vm.setPrivateAddresses(privateAddresses.toArray(new RawAddress[privateAddresses.size()]));
        vm.setProviderAssignedIpAddressId(providerAssignedIpAddressId);

        vm.setRebootable(true);
        vm.setPersistent(true);
        vm.setIpForwardingAllowed(true);
        vm.setImagable(false);
        vm.setClonable(false);

        vm.setPlatform(Platform.guess(instance.getName()));
        vm.setArchitecture(Architecture.I64);

        vm.setTag("contentLink", instance.getSelfLink());


        return vm;
    }

    private VirtualMachineProduct toProduct(MachineType machineType){
        VirtualMachineProduct product = new VirtualMachineProduct();
        product.setProviderProductId(machineType.getName() + "+" + machineType.getZone());
        product.setName(machineType.getName());
        product.setDescription(machineType.getSelfLink());
        product.setCpuCount(machineType.getGuestCpus());
        product.setRamSize(new Storage<Megabyte>(machineType.getMemoryMb(), Storage.MEGABYTE));
        if (machineType.getImageSpaceGb() != null)
            product.setRootVolumeSize(new Storage<Gigabyte>(machineType.getImageSpaceGb(), Storage.GIGABYTE));
        else
            product.setRootVolumeSize(new Storage<Gigabyte>(0, Storage.GIGABYTE));  // defined at creation time by specified root volume size.
        product.setVisibleScope(VisibleScope.ACCOUNT_DATACENTER);
        return product;
    }

    // the default implementation does parallel launches and throws an exception only if it is unable to launch any virtual machines
    @Override
    public @Nonnull Iterable<String> launchMany(final @Nonnull VMLaunchOptions withLaunchOptions, final @Nonnegative int count) throws CloudException, InternalException {
        if( count < 1 ) {
            throw new InternalException("Invalid attempt to launch less than 1 virtual machine (requested " + count + ").");
        }
        if( count == 1 ) {
            return Collections.singleton(launch(withLaunchOptions).getProviderVirtualMachineId());
        }
        final List<Future<String>> results = new ArrayList<Future<String>>();

        // windows on GCE follows same naming constraints as regular instances, 1-62 lower and numbers, must begin with a letter.
        NamingConstraints c = NamingConstraints.getAlphaNumeric(1, 63).withNoSpaces().withRegularExpression("(?:[a-z](?:[-a-z0-9]{0,61}[a-z0-9])?)").lowerCaseOnly().constrainedBy('-');
        String baseHost = c.convertToValidName(withLaunchOptions.getHostName(), Locale.US);

        if( baseHost == null ) {
            baseHost = withLaunchOptions.getHostName();
        }
        for (int i = 1; i <= count; i++) {
            String hostName = c.incrementName(baseHost, i);
            String friendlyName = withLaunchOptions.getFriendlyName() + "-" + i;
            VMLaunchOptions options = withLaunchOptions.copy(hostName == null ? withLaunchOptions.getHostName() + "-" + i : hostName, friendlyName);

            results.add(launchAsync(options));
        }

        PopulatorThread<String> populator = new PopulatorThread<String>(new JiteratorPopulator<String>() {
            @Override
            public void populate( @Nonnull Jiterator<String> iterator ) throws Exception {
                List<Future<String>> original = results;
                List<Future<String>> copy = new ArrayList<Future<String>>();
                Exception exception = null;
                boolean loaded = false;

                while( !original.isEmpty() ) {
                    for( Future<String> result : original ) {
                        if( result.isDone() ) {
                            try {
                                iterator.push(result.get());
                                loaded = true;
                            } catch( Exception e ) {
                                exception = e;
                            }
                        }
                        else {
                            copy.add(result);
                        }
                    }
                    original = copy;
                    // copy has to be a new list else we'll get into concurrently modified list state
                    copy = new ArrayList<Future<String>>();
                }
                if( exception != null && !loaded ) {
                    throw exception;
                }
            }
        });

        populator.populate();
        return populator.getResult();
    }

    @Override
    public @Nullable String getUserData( @Nonnull String vmId ) throws InternalException, CloudException {
        APITrace.begin(getProvider(), "getVirtualMachine");
        try{
            try{
                Compute gce = provider.getGoogleCompute();
                InstanceAggregatedList instances = gce.instances().aggregatedList(provider.getContext().getAccountNumber()).setFilter("name eq " + getVmNameFromId(vmId)).execute();
                Iterator<String> it = instances.getItems().keySet().iterator();
                while (it.hasNext()){
                    String zone = it.next();
                    if(instances.getItems() != null && instances.getItems().get(zone) != null && instances.getItems().get(zone).getInstances() != null){
                        for(Instance instance : instances.getItems().get(zone).getInstances()){
                            if(instance.getName().equals(getVmNameFromId(vmId))) {
                                Metadata metadata = instance.getMetadata();
                                if (null != metadata) {
                                List<Items> items = metadata.getItems();
                                if (null != items) {
                                    for (Items item : items) {
                                        if ("startup-script".equals(item.getKey())) {
                                            return item.getValue();
                                        }
                                    }
                                }
                                }
                            }

                        }
                    }
                }
                return null; // not found
            } catch (IOException ex) {
                logger.error(ex.getMessage());
                if (ex.getClass() == GoogleJsonResponseException.class) {
                    GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                    throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
                } else
                    throw new CloudException("An error occurred retrieving VM: " + vmId + ": " + ex.getMessage());
            }
        }
        finally {
            APITrace.end();
        }
    }

    private void resetPassword(String vmId, String dataCenterId, KeyPair keys) throws InternalException, CloudException {
        Compute gce = provider.getGoogleCompute();
        Instance inst;
        try {
            inst = gce.instances().get(provider.getContext().getAccountNumber(), dataCenterId, vmId).execute();
        } catch ( IOException e ) {
            throw new CloudException(e); 
        }
        Metadata metadata = inst.getMetadata();

        replaceMetadata(metadata, buildKeyMetadata(keys, "Admin", "")); // administrator appears to be reserved

        // Tell Compute Engine to update the instance metadata with our changes.
        try {
            gce.instances().setMetadata(provider.getContext().getAccountNumber(), dataCenterId, vmId, metadata).execute();
        } catch ( IOException e ) {
            throw new CloudException(e); 
        }
        try {
            Thread.sleep(30000);
        } catch ( InterruptedException e ) { }
    }

    private String decryptPassword(String message, KeyPair keys) throws InternalException {
        try {
            Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

            Cipher rsaOAEPPadding = Cipher.getInstance("RSA/NONE/OAEPPadding", "BC");
            rsaOAEPPadding.init(Cipher.DECRYPT_MODE, keys.getPrivate());

            return new String(rsaOAEPPadding.doFinal(Base64.decodeBase64(message)), "UTF8");
        } catch (Exception e) {
            throw new InternalException(e);
        }
    }

    private void replaceMetadata(Metadata metadata, JSONObject newMetadataItem) {
        String newItemString = newMetadataItem.toString();

        List<Items> items = metadata.getItems();

        if (items == null) {
            items = new LinkedList<Items>();
            metadata.setItems(items);
        }

        // Find the "windows-keys" entry and update it.
        for (Items item : items) {
            if (item.getKey().compareTo("windows-keys") == 0) {
                item.setValue(newItemString);
                return;
            }
        }
        items.add(new Items().setKey("windows-keys").setValue(newItemString));
    }

      private JSONObject buildKeyMetadata(KeyPair pair, String userName, String email) throws InternalException {
          JSONObject metadataValues = jsonEncode(pair);

          for(String key : JSONObject.getNames(metadataValues)) {
              try {
                  metadataValues.put(key, metadataValues.get(key));
              } catch ( JSONException e ) { }
          }

          // Create the date on which the new keys expire.
          Date now = new Date();
          Date expireDate = new Date(now.getTime() + 360000); // 6 minutes

          SimpleDateFormat rfc3339Format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
          rfc3339Format.setTimeZone(TimeZone.getTimeZone("UTC"));
          String dateString = rfc3339Format.format(expireDate);

          try {
              metadataValues.put("userName", userName);
              metadataValues.put("email", email);
              metadataValues.put("expireOn", dateString);
          } catch ( JSONException e ) { 
              throw new InternalException(e);
          }

          return metadataValues;
      }

      private JSONObject jsonEncode(KeyPair keys) throws InternalException {
          JSONObject returnJson = new JSONObject();
          try {
              KeyFactory factory = KeyFactory.getInstance("RSA");

              RSAPublicKeySpec pubSpec = factory.getKeySpec(keys.getPublic(), RSAPublicKeySpec.class);

              BigInteger modulus = pubSpec.getModulus();
              BigInteger exponent = pubSpec.getPublicExponent();

              BaseEncoding stringEncoder = BaseEncoding.base64();

              // Strip out the leading 0 byte in the modulus.
              byte[] arr = Arrays.copyOfRange(modulus.toByteArray(), 1, modulus.toByteArray().length);

              returnJson.put("modulus", stringEncoder.encode(arr).replaceAll("\n", ""));
              returnJson.put("exponent", stringEncoder.encode(exponent.toByteArray()).replaceAll("\n", ""));
          } catch ( Exception e ) { 
              throw new InternalException(e);
          }

          return returnJson;
      }
}
