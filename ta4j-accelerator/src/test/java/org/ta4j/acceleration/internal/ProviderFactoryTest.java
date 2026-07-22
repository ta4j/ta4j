/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.acceleration.internal.adapters.ForecastBatchAdapter;
import org.ta4j.acceleration.internal.providers.CudaAccelerationProviderFactory;
import org.ta4j.acceleration.internal.providers.MetalAccelerationProviderFactory;
import org.ta4j.acceleration.spi.IndicatorAccelerationProvider;
import org.ta4j.core.acceleration.AccelerationMode;

class ProviderFactoryTest {

    @Test
    void cudaSkeletonIsUnavailableAndNotImplemented() {
        CudaAccelerationProviderFactory factory = new CudaAccelerationProviderFactory();

        IndicatorAccelerationProvider provider = factory.probe(List.of(ForecastBatchAdapter.OPERATION_ID));

        assertThat(provider.capability().mode()).isEqualTo(AccelerationMode.CUDA);
        assertThat(provider.capability().available()).isFalse();
        assertThat(provider.capability().nativeInitialized()).isFalse();
        assertThat(provider.capability().rejectionReason()).contains("NOT_IMPLEMENTED");
    }

    @Test
    void metalProbeDoesNotInitializeNativeCodeWhenLibraryIsAbsent() {
        MetalAccelerationProviderFactory factory = new MetalAccelerationProviderFactory();

        IndicatorAccelerationProvider provider = factory.probe(List.of(ForecastBatchAdapter.OPERATION_ID));

        assertThat(provider.capability().nativeInitialized()).isFalse();
        if (!provider.capability().available()) {
            assertThat(provider.capability().rejectionReason()).isNotBlank();
        }
    }
}
