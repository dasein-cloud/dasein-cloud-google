package org.dasein.cloud.google.capabilities;

import java.util.Arrays;
import java.util.Collections;

import javax.annotation.Nonnull;

import org.dasein.cloud.AbstractCapabilities;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.network.VPNCapabilities;
import org.dasein.cloud.network.VPNProtocol;

public class GCEVPNCapabilities extends AbstractCapabilities<Google> implements VPNCapabilities {

    public GCEVPNCapabilities(Google provider) {
        super(provider);
    }

    @Override
    public @Nonnull Requirement getVPNVLANConstraint() throws CloudException, InternalException {
        return Requirement.REQUIRED;
    }

    @Override
    public @Nonnull Iterable<VPNProtocol> listSupportedVPNProtocols() throws CloudException, InternalException {
        return Collections.unmodifiableList(Arrays.asList(VPNProtocol.IKE_V1, VPNProtocol.IKE_V2));
    }

    @Override
    public @Nonnull boolean supportsGateway() throws CloudException, InternalException {
        return false;
    }

    @Override
    public @Nonnull boolean supportsVPNGateway() throws CloudException, InternalException {
        return true;
    }
}