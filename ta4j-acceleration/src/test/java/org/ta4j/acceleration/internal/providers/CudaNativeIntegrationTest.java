/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ta4j.core.acceleration.AccelerationRuntime.Determinism;
import org.ta4j.core.acceleration.AccelerationRuntime.KernelRequest;
import org.ta4j.core.acceleration.AccelerationRuntime.NumericEncoding;
import org.ta4j.core.acceleration.AccelerationRuntime.Operation;

/**
 * Exercises CUDA device qualification and real provider sample dispatch.
 * Distinct operation shock codes must retain their standardized, historical,
 * and normal semantics when translated to the native kernel.
 *
 * <p>
 * The native library cannot be built on macOS, so this test is excluded from
 * the canonical gate by {@code @Tag("integration")} +
 * {@code @Tag("requires-cuda")} and is driven by
 * {@code scripts/acceleration/windows-cuda-handoff.ps1} and
 * {@code scripts/acceleration/linux-cuda-handoff.sh} on CUDA hosts.
 */
@Tag("integration")
@Tag("requires-cuda")
class CudaNativeIntegrationTest {

    @Test
    void nativeProbeSelfTestReportsAvailableDevice() {
        String configuredLibrary = System.getProperty(CudaNativeLibrary.LIBRARY_PROPERTY);
        assertThat(configuredLibrary).as(CudaNativeLibrary.LIBRARY_PROPERTY).isNotBlank();
        assertThat(Files.isRegularFile(Path.of(configuredLibrary))).as("configured library path").isTrue();

        CudaNativeLibrary.LoadResult loaded = CudaNativeLibrary.load();
        assertThat(loaded.loaded()).as(loaded.detail()).isTrue();

        CudaProbeResult probe = new JniCudaNativeBridge().probe();
        assertThat(probe.available()).as(probe.detail()).isTrue();
        assertThat(probe.deviceName()).isNotBlank();
        CudaAccelerationProvider provider = new CudaAccelerationProvider();
        double historicalReturn = Math.log(2d);
        for (int shockCode : new int[] { 0, 1, 3 }) {
            KernelRequest request = new KernelRequest(Operation.MONTE_CARLO_SHOCK_PATHS_V1, 1, 1, 2,
                    NumericEncoding.FLOAT64, Determinism.APPROXIMATE, 42L, 0.01d,
                    new double[] { shockCode, 0d, 2d, 2d, 2d, 0.94d },
                    List.of(new double[] { 100d }, new double[] { historicalReturn }, new double[] { 0d },
                            new double[] { shockCode == 3 ? 0d : 1d },
                            new double[] { historicalReturn, historicalReturn }),
                    1_000_000L, 1_000_000L);
            double expected = shockCode == 1 ? 400d : 100d;

            assertThat(provider.execute(request).outputs()).containsExactly(expected, expected);
        }
    }
}