package org.dasein.cloud.google.capabilities;

import java.util.Arrays;
import java.util.Collections;

import javax.annotation.Nonnull;

import org.dasein.cloud.AbstractCapabilities;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.VisibleScope;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.network.VpnCapabilities;
import org.dasein.cloud.network.VpnProtocol;

public class GCEVPNCapabilities extends AbstractCapabilities<Google> implements VpnCapabilities {

    public GCEVPNCapabilities(Google provider) {
        super(provider);
    }

    @Override
    public @Nonnull Requirement getVPNVLANConstraint() throws CloudException, InternalException {
        return Requirement.REQUIRED;
    }

    @Override
    public @Nonnull Iterable<VpnProtocol> listSupportedVPNProtocols() throws CloudException, InternalException {
        return Collections.unmodifiableList(Arrays.asList(VpnProtocol.IKE_V1, VpnProtocol.IKE_V2));
    }

    @Override
    public VisibleScope getVpnVisibleScope() {
        return VisibleScope.ACCOUNT_REGION;
    }
}
