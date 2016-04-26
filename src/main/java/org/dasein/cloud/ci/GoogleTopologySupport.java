package org.dasein.cloud.ci;

import com.google.api.services.compute.Compute.InstanceTemplates;
import com.google.api.services.compute.model.InstanceTemplate;
import com.google.api.services.compute.model.InstanceTemplateList;
import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.GeneralCloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.google.Google;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GoogleTopologySupport extends AbstractTopologySupport<Google> {
    static private final Logger logger = Google.getLogger(GoogleTopologySupport.class);
    private InstanceTemplates instanceTemplates = null;;

    public GoogleTopologySupport(Google provider) {
        super(provider);
        try {
            instanceTemplates = getProvider().getGoogleCompute().instanceTemplates();

        } catch ( CloudException e ) {
            logger.error(e);
        } catch ( InternalException e ) {
            logger.error(e);
        }
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        return true;
    }

    @Override
    public Iterable<Topology> listTopologies(TopologyFilterOptions options) throws CloudException, InternalException {
        List<Topology> topologies = new ArrayList<Topology>();
        try {
            InstanceTemplateList templateList = instanceTemplates.list(getContext().getAccountNumber()).execute();
            if (templateList != null && templateList.getItems() != null) {
                for (InstanceTemplate template : templateList.getItems()) {
                    Topology topology = Topology.getInstance(getContext().getAccountNumber(), null, template.getName(), template.getName(), template.getDescription());

                    if ( (null == options) || (options.matches(topology)) ) {
                        topologies.add(topology);
                    }
                }
            }
        } catch ( IOException e ) {
            throw new GeneralCloudException("Problem listing topologies", e);
        }

        return topologies;
    }

    private transient volatile GCETopologyCapabilities capabilities;

    @Override
    public @Nonnull GCETopologyCapabilities getCapabilities() throws CloudException, InternalException {
        if( capabilities == null ) {
            capabilities = new GCETopologyCapabilities(getProvider());
        }
        return capabilities;
    }

}
