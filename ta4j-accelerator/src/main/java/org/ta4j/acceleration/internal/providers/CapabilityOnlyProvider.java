/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

import org.ta4j.acceleration.spi.IndicatorAccelerationProvider;
import org.ta4j.acceleration.spi.ProviderCapability;

final class CapabilityOnlyProvider implements IndicatorAccelerationProvider {

    private final ProviderCapability capability;

    CapabilityOnlyProvider(ProviderCapability capability) {
        this.capability = capability;
    }

    @Override
    public ProviderCapability capability() {
        return capability;
    }
}
