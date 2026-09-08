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
 * Exercises OpenCL device qualification and real provider sample dispatch
 * through the operation-level ABI. The result must contain terminal samples,
 * not reduced forecast rows.
 *
 * <p>
 * The native library cannot be built on macOS, so this test is excluded from
 * the canonical gate by {@code @Tag("integration")} +
 * {@code @Tag("requires-opencl")} and is driven by
 * {@code scripts/acceleration/validate-opencl-linux.sh} inside a PoCL
 * container.
 */
@Tag("integration")
@Tag("requires-opencl")
class OpenClNativeIntegrationTest {

    @Test
    void nativeProbeSelfTestReportsAvailableDevice() {
        String configuredLibrary = System.getProperty(OpenClNativeLibrary.LIBRARY_PROPERTY);
        assertThat(configuredLibrary).as(OpenClNativeLibrary.LIBRARY_PROPERTY).isNotBlank();
        assertThat(Files.isRegularFile(Path.of(configuredLibrary))).as("configured library path").isTrue();

        OpenClNativeLibrary.LoadResult loaded = OpenClNativeLibrary.load();
        assertThat(loaded.loaded()).as(loaded.detail()).isTrue();

        OpenClProbeResult probe = new JniOpenClNativeBridge().probe();
        assertThat(probe.available()).as(probe.detail()).isTrue();
        assertThat(probe.deviceName()).isNotBlank();
        OpenClAccelerationProvider provider = new OpenClAccelerationProvider();
        double historicalReturn = Math.log(2d);
        for (int shockCode : new int[] { 0, 1, 3 }) {
            KernelRequest request = new KernelRequest(Operation.MONTE_CARLO_SHOCK_PATHS_V1, 1, 1, 2,
                    NumericEncoding.FLOAT64, Determinism.APPROXIMATE, 42L, 0.01d,
                    new double[] { shockCode, 0d, 2d, 2d, 2d, 0.94d },
                    List.of(new double[] { 100d }, new double[] { historicalReturn }, new double[] { 0d },
                            new double[] { shockCode == 3 ? 0d : 1d },
                            new double[] { historicalReturn, historicalReturn }),
                    1_000_000L, 1_000_000L);
            double expected = shockCode == 0 ? 400d : 100d;

            assertThat(provider.execute(request).outputs()).containsExactly(expected, expected);
        }
    }
}