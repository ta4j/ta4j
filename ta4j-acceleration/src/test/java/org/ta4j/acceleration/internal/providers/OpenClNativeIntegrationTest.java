/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

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
        ShockPathReference.assumeOpenClLane();

        OpenClProbeResult probe = new JniOpenClNativeBridge().probe();
        assertThat(probe.available()).as(probe.detail()).isTrue();
        assertThat(probe.deviceName()).isNotBlank();
        // Cumulative log-returns must match the double-precision contract to
        // within the lane's non-strict transcendental rounding.
        ShockPathReference.assertFp64LaneMatchesReference(new OpenClAccelerationProvider(), 1e-12d);
    }
}