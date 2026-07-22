/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

import java.util.Collection;
import java.util.List;

import org.ta4j.acceleration.internal.adapters.ForecastBatchAdapter;
import org.ta4j.acceleration.spi.IndicatorAccelerationProvider;
import org.ta4j.acceleration.spi.IndicatorAccelerationProviderFactory;
import org.ta4j.acceleration.spi.ProviderCapability;
import org.ta4j.core.acceleration.AccelerationMode;

/**
 * Compile-safe CUDA continuation skeleton.
 */
public final class CudaAccelerationProviderFactory implements IndicatorAccelerationProviderFactory {

    @Override
    public String providerId() {
        return "cuda";
    }

    @Override
    public AccelerationMode mode() {
        return AccelerationMode.CUDA;
    }

    @Override
    public IndicatorAccelerationProvider probe(Collection<String> operationIds) {
        ProviderCapability capability = new ProviderCapability(providerId(), mode(), false, false, "",
                List.of(ForecastBatchAdapter.OPERATION_ID),
                "NOT_IMPLEMENTED: complete CUDA probe, streams, kernels, reductions, self-test, and packaging on Windows 11 / RTX 5090");
        return new CapabilityOnlyProvider(capability);
    }
}
