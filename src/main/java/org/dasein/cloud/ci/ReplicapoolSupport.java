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

package org.dasein.cloud.ci;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceList;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.replicapool.Replicapool;
import com.google.api.services.replicapool.model.InstanceGroupManager;
import com.google.api.services.replicapool.model.InstanceGroupManagerList;
import com.google.api.services.replicapool.model.Operation;
import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.GeneralCloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.ResourceType;
import org.dasein.cloud.dc.DataCenter;
import org.dasein.cloud.dc.Region;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.GoogleMethod;
import org.dasein.cloud.google.GoogleOperationType;
import org.dasein.cloud.google.capabilities.GCEReplicapoolCapabilities;
import org.dasein.cloud.util.APITrace;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Implements the replicapool services supported in the Google API.
 * @author Roger Unwin
 * @version 2015.03 initial version
 * @since 2015.03
 */
public class ReplicapoolSupport extends AbstractConvergedInfrastructureSupport <Google> {
    static private final Logger logger = Google.getLogger(ReplicapoolSupport.class);
    private Google provider = null;

    public ReplicapoolSupport(Google provider) {
        super(provider);
        this.provider = provider;
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        APITrace.begin(getProvider(), "GoogleConvergedInfrastructure.isSubscribed");
        try {
            return true;  // cop-out for now.
        } finally{
            APITrace.end();
        }
    }

    private transient volatile GCEReplicapoolCapabilities capabilities;

    public @Nonnull GCEReplicapoolCapabilities getCapabilities() {
        if( capabilities == null ) {
            capabilities = new GCEReplicapoolCapabilities(provider);
        }
        return capabilities;
    }

    @Override
    public Iterable<ConvergedInfrastructure> listConvergedInfrastructures(ConvergedInfrastructureFilterOptions options) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "GoogleConvergedInfrastructure.listConvergedInfrastructures");
        List<ConvergedInfrastructure> convergedInfrastrutures = new ArrayList<ConvergedInfrastructure>();
        try {
             Replicapool rp = provider.getGoogleReplicapool();

             InstanceGroupManagerList result = null;
             try {
                 for (Region region : provider.getDataCenterServices().listRegions()) {
                     String regionName = region.getProviderRegionId();
                     for (DataCenter dataCenter : provider.getDataCenterServices().listDataCenters(regionName)) {
                         String dataCenterId = dataCenter.getProviderDataCenterId();
                         result = rp.instanceGroupManagers().list(provider.getContext().getAccountNumber(), dataCenterId).execute(); //provider.getContext().getRegionId()
                         if (null != result.getItems()) {
                             for (InstanceGroupManager item : result.getItems()) {
                                 ConvergedInfrastructure ci = ConvergedInfrastructure.getInstance(item.getName(), item.getName(), item.getDescription(),
                                         ConvergedInfrastructureState.READY, System.currentTimeMillis(), dataCenterId, regionName, null);
                                 ci.setTag("selfLink", item.getSelfLink());
                                 ci.setTag("instanceGroupLink", item.getGroup());
                                 loadResources(ci, dataCenterId);
                                 if (options != null) {
                                     if (options.matches(ci)) {
                                         convergedInfrastrutures.add(ci);
                                     }
                                 }
                                 else {
                                     convergedInfrastrutures.add(ci);
                                 }
                             }
                         }
                     }
                 }
             } catch ( IOException e ) {
                 throw new GeneralCloudException("Error listing converged infrastructure", e);
             }
        } finally{
            APITrace.end();
        }
        return convergedInfrastrutures;
    }

    private void loadResources(ConvergedInfrastructure ci, String datacenterId) throws InternalException, CloudException {
        APITrace.begin(getProvider(), "GoogleConvergedInfrastructure.loadResources");
        List<ConvergedInfrastructureResource> list = new ArrayList<>();
        try {
            Replicapool rp = provider.getGoogleReplicapool();
            Compute gce = provider.getGoogleCompute();

            InstanceGroupManager pool = rp.instanceGroupManagers().get(provider.getContext().getAccountNumber(), datacenterId, ci.getProviderCIId()).execute();
            String baseInstanceName = pool.getBaseInstanceName();
            InstanceList result = gce.instances().list(provider.getContext().getAccountNumber(), datacenterId).execute();
            for (Instance instance : result.getItems()) {
                if (instance.getName().startsWith(baseInstanceName + "-")) {
                    ConvergedInfrastructureResource resource = ConvergedInfrastructureResource.getInstance(ResourceType.VIRTUAL_MACHINE, instance.getName());
                    list.add(resource);
                    if (null != instance.getNetworkInterfaces()) {
                        for (NetworkInterface net : instance.getNetworkInterfaces()) {
                            String vlan = net.getNetwork().replaceAll(".*/", "");
                            resource = ConvergedInfrastructureResource.getInstance(ResourceType.VLAN, vlan);
                            list.add(resource);
                        }
                    }
                }
            }
            if (!list.isEmpty()){
                ConvergedInfrastructureResource[] resources = new ConvergedInfrastructureResource[list.size()];
                resources = list.toArray(resources);
                ci.withResources(resources);
            }
        } catch ( IOException e ) {
            throw new GeneralCloudException("Error listing virtual machines", e);
        } finally{
            APITrace.end();
        }
    }

    /*
     * Create a replicaPool based on options in CIProvisionOptions options
     * @see org.dasein.cloud.ci.ConvergedInfrastructureSupport#provision(org.dasein.cloud.ci.CIProvisionOptions)
     */
    @Override
    public ConvergedInfrastructure provision(ConvergedInfrastructureProvisionOptions options) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "GoogleConvergedInfrastructure.provision");
        Replicapool rp = provider.getGoogleReplicapool();
        try {
            ProviderContext ctx = provider.getContext();
            InstanceGroupManager content = new InstanceGroupManager();
            content.setBaseInstanceName(getCapabilities().getConvergedInfrastructureNamingConstraints().convertToValidName(options.getBaseName(), Locale.US));
            content.setDescription(options.getDescription());
            content.setInstanceTemplate("https://www.googleapis.com/compute/v1/projects/" + ctx.getAccountNumber() + "/global/instanceTemplates/" + options.getTemplate());
            content.setName(getCapabilities().getConvergedInfrastructureNamingConstraints().convertToValidName(options.getName(), Locale.US));
            String region = options.getProviderDatacenterId().replaceFirst("-.$", "");
            //content.setTargetPools(targetPools);
            Operation job = rp.instanceGroupManagers().insert(ctx.getAccountNumber(), options.getProviderDatacenterId(), options.getInstanceCount(), content).execute();
            GoogleMethod method = new GoogleMethod(provider);
            method.getCIOperationComplete(ctx, job, GoogleOperationType.ZONE_OPERATION, region, options.getProviderDatacenterId());
            return ConvergedInfrastructure.getInstance(options.getName(), options.getName(), options.getDescription(), ConvergedInfrastructureState.READY, System.currentTimeMillis(), options.getProviderDatacenterId(), region, null);
        } catch ( IOException e ) {
            throw new GeneralCloudException("Error provisioning", e);
        } finally{
            APITrace.end();
        }
    }

    @Override
    public void terminate(String ciId, String explanation) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "GoogleConvergedInfrastructure.terminate");
        ProviderContext ctx = provider.getContext();

        try {
             Replicapool rp = provider.getGoogleReplicapool();
             for (ConvergedInfrastructure ci : listConvergedInfrastructures(null)) {
                 if (ci.getName().equals(ciId)) {
                     Operation job = rp.instanceGroupManagers().delete(provider.getContext().getAccountNumber(), ci.getProviderDatacenterId(), ciId).execute();
                     GoogleMethod method = new GoogleMethod(provider);
                     method.getCIOperationComplete(ctx, job, GoogleOperationType.ZONE_OPERATION, ctx.getRegionId(), ci.getProviderDatacenterId());
                 }
             }
        } catch ( IOException e ) {
            throw new GeneralCloudException("Error terminating", e);

        } finally {
            APITrace.end();
        }
    }

    @Override
    public void cancelDeployment(@Nonnull String ciId, @Nullable String explanation) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Cancelling deployment not supported in "+getProvider().getCloudName());
    }

    @Override
    public ConvergedInfrastructure validateDeployment(@Nonnull ConvergedInfrastructureProvisionOptions options) throws CloudException, InternalException {
        return null;
    }
}
