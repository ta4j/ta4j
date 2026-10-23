/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

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
        ShockPathReference.assumeCudaLane();

        CudaProbeResult probe = new JniCudaNativeBridge().probe();
        assertThat(probe.available()).as(probe.detail()).isTrue();
        assertThat(probe.deviceName()).isNotBlank();
        // Cumulative log-returns must match the double-precision contract to
        // within the lane's non-strict transcendental rounding.
        ShockPathReference.assertFp64LaneMatchesReference(new CudaAccelerationProvider(), 1e-12d);
    }
}