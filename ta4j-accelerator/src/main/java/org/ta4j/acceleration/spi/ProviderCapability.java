/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.spi;

import java.util.List;
import java.util.Objects;

import org.ta4j.core.acceleration.AccelerationMode;

/**
 * Immutable provider capability report.
 *
 * @param providerId          stable provider identifier
 * @param mode                backend mode represented by the provider
 * @param available           whether the provider can execute operations
 * @param nativeInitialized   whether native code was initialized during probing
 * @param deviceName          device/runtime name
 * @param supportedOperations operation IDs supported by this provider
 * @param rejectionReason     reason when unavailable
 * @since 0.23.1
 */
public record ProviderCapability(String providerId, AccelerationMode mode, boolean available, boolean nativeInitialized,
        String deviceName, List<String> supportedOperations, String rejectionReason) {

    public ProviderCapability {
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        Objects.requireNonNull(deviceName, "deviceName must not be null");
        supportedOperations = List
                .copyOf(Objects.requireNonNull(supportedOperations, "supportedOperations must not be null"));
        Objects.requireNonNull(rejectionReason, "rejectionReason must not be null");
    }

    /**
     * @param operationId operation identifier
     * @return true when this provider can execute the operation
     * @since 0.23.1
     */
    public boolean supports(String operationId) {
        return supportedOperations.contains(operationId);
    }
}
