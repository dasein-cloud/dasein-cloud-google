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

public class GCEVpnCapabilities extends AbstractCapabilities<Google> implements VpnCapabilities {

    public GCEVpnCapabilities(Google provider) {
        super(provider);
    }

    @Override
    public @Nonnull Iterable<VpnProtocol> listSupportedVpnProtocols() throws CloudException, InternalException {
        return Collections.unmodifiableList(Arrays.asList(VpnProtocol.IKE_V1, VpnProtocol.IKE_V2));
    }

    @Override
    public VisibleScope getVpnVisibleScope() {
        return VisibleScope.ACCOUNT_REGION;
    }

    @Override
    public Requirement identifyLabelsRequirement() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Override
    public Requirement identifyVlanIdRequirement() throws CloudException, InternalException {
        return Requirement.REQUIRED;
    }

    @Override
    public Requirement identifyDataCenterIdRequirement() throws CloudException, InternalException {
        return Requirement.REQUIRED;
    }

    @Override
    public Requirement identifyGatewayCidrRequirement() throws CloudException, InternalException {
        return Requirement.REQUIRED;
    }

    @Override
    public Requirement identifyGatewaySharedSecretRequirement() throws CloudException, InternalException {
        return Requirement.REQUIRED;
    }

    @Override
    public Requirement identifyGatewayBgpAsnRequirement() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Override
    public Requirement identifyGatewayVlanNameRequirement() throws CloudException, InternalException {
        return Requirement.REQUIRED;
    }

    @Override
    public Requirement identifyGatewayVpnNameRequirement() throws CloudException, InternalException {
        return Requirement.REQUIRED;
    }

    @Override
    public boolean supportsAutoConnect() throws CloudException, InternalException {
        return true;
    }
}
