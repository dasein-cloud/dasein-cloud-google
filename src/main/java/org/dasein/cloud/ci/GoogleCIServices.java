package org.dasein.cloud.ci;

import org.dasein.cloud.google.Google;

import javax.annotation.Nullable;

public class GoogleCIServices extends AbstractConvergedInfrastructureServices<Google> {

    public GoogleCIServices(Google provider) {
        super(provider);
    }

    @Override
    public @Nullable ConvergedInfrastructureSupport getConvergedInfrastructureSupport() {
        return new ReplicapoolSupport(getProvider());
    }

    @Override
    public boolean hasConvergedInfrastructureSupport() {
        return (getConvergedInfrastructureSupport() != null);
    }


    @Override
    public @Nullable TopologySupport getTopologySupport() {
        return new GoogleTopologySupport(getProvider());
    }

    @Override
    public boolean hasTopologySupport() {
        return (getTopologySupport() != null);
    }
}
