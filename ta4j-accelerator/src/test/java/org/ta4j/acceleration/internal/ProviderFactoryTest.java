/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ta4j.acceleration.internal.adapters.ForecastBatchAdapter;
import org.ta4j.acceleration.internal.providers.CudaAccelerationProviderFactory;
import org.ta4j.acceleration.internal.providers.MetalAccelerationProviderFactory;
import org.ta4j.acceleration.spi.IndicatorAccelerationProvider;
import org.ta4j.core.acceleration.AccelerationMode;

class ProviderFactoryTest {

    @Test
    void cudaProviderStaysUnavailableWithoutAQualifiedPlatformAndLibrary() {
        String previous = System.getProperty(CudaAccelerationProviderFactory.LIBRARY_PROPERTY);
        System.clearProperty(CudaAccelerationProviderFactory.LIBRARY_PROPERTY);
        try {
            CudaAccelerationProviderFactory factory = new CudaAccelerationProviderFactory();

            IndicatorAccelerationProvider provider = factory.probe(List.of(ForecastBatchAdapter.OPERATION_ID));

            assertThat(provider.capability().mode()).isEqualTo(AccelerationMode.CUDA);
            assertThat(provider.capability().available()).isFalse();
            assertThat(provider.capability().nativeInitialized()).isFalse();
            assertThat(provider.capability().rejectionReason()).isNotBlank();
        } finally {
            restoreProperty(CudaAccelerationProviderFactory.LIBRARY_PROPERTY, previous);
        }
    }

    @Test
    void metalProbeDoesNotInitializeNativeCodeWhenLibraryIsAbsent(@TempDir Path tempDir) {
        String previous = System.getProperty(MetalAccelerationProviderFactory.LIBRARY_PROPERTY);
        System.setProperty(MetalAccelerationProviderFactory.LIBRARY_PROPERTY,
                tempDir.resolve("missing-libta4j-metal-accelerator.dylib").toString());
        try {
            MetalAccelerationProviderFactory factory = new MetalAccelerationProviderFactory();

            IndicatorAccelerationProvider provider = factory.probe(List.of(ForecastBatchAdapter.OPERATION_ID));

            assertThat(provider.capability().nativeInitialized()).isFalse();
            assertThat(provider.capability().available()).isFalse();
            assertThat(provider.capability().rejectionReason()).isNotBlank();
        } finally {
            if (previous == null) {
                System.clearProperty(MetalAccelerationProviderFactory.LIBRARY_PROPERTY);
            } else {
                System.setProperty(MetalAccelerationProviderFactory.LIBRARY_PROPERTY, previous);
            }
        }
    }

    private static void restoreProperty(String property, String previous) {
        if (previous == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, previous);
        }
    }
}
