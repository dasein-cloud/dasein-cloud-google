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

package org.dasein.cloud.google.capabilities;

import org.dasein.cloud.AbstractCapabilities;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.ci.ConvergedInfrastructureCapabilities;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.util.NamingConstraints;
import javax.annotation.Nonnull;

/**
 * @author Roger Unwin
 * @version 2015.03 initial version
 * @since 2015.03
 */
public class GCEReplicapoolCapabilities extends AbstractCapabilities<Google> implements ConvergedInfrastructureCapabilities {

    public GCEReplicapoolCapabilities(Google provider) {
        super(provider);
        // TODO Auto-generated constructor stub
    }

    @Override
    public @Nonnull NamingConstraints getConvergedInfrastructureNamingConstraints() {
        return NamingConstraints.getAlphaNumeric(1, 63)
                .withRegularExpression("^[a-z][-a-z0-9]{0,61}[a-z0-9]$")
                .lowerCaseOnly()
                .withNoSpaces()
                .withLastCharacterSymbolAllowed(false)
                .constrainedBy('-');
    }

    @Nonnull
    @Override
    public Requirement identifyResourcePoolLaunchRequirement() {
        return Requirement.NONE;
    }

    @Nonnull
    @Override
    public Requirement identifyTemplateContentLaunchRequirement() {
        return Requirement.NONE;
    }
}
