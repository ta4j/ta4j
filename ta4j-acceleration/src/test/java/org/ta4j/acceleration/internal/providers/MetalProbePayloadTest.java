/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Verifies the Java-side Metal probe payload contract. Native integration tests
 * cover the JNI bridge; these tests keep payload parsing deterministic and
 * independent of a compiler, filesystem fixture, and platform loader.
 */
class MetalProbePayloadTest {

    @Test
    void probeErrorPayloadSurfacesTheNativeDetail() {
        MetalProbeResult result = JniMetalNativeBridge.parseProbePayload("ERROR|||metal_device_unavailable");

        assertThat(result.available()).isFalse();
        assertThat(result.detail()).isEqualTo("metal_device_unavailable");
    }

    @Test
    void probeOkPayloadStillParses() {
        MetalProbeResult result = JniMetalNativeBridge.parseProbePayload("OK|Apple M5 Max|68719476736|ready");

        assertThat(result.available()).isTrue();
        assertThat(result.deviceName()).isEqualTo("Apple M5 Max");
        assertThat(result.recommendedMaxWorkingSetBytes()).isEqualTo(68_719_476_736L);
        assertThat(result.detail()).isEqualTo("ready");
    }

    @Test
    void missingPayloadIsReportedWithoutNativeCode() {
        MetalProbeResult result = JniMetalNativeBridge.parseProbePayload(null);

        assertThat(result.available()).isFalse();
        assertThat(result.detail()).isEqualTo("Metal probe returned no metadata");
    }

    @Test
    public void rejectsBlankTruncatedAndNonnumericMetalMetadata() {
        for (String payload : new String[] { "", " \t", "OK|device", "OK|device|not-a-number|ready" }) {
            MetalProbeResult result = JniMetalNativeBridge.parseProbePayload(payload);

            assertThat(result.available()).as("payload %s", payload).isFalse();
            assertThat(result.detail()).contains(payload.isBlank() ? "no metadata" : "Malformed Metal probe");
        }
    }

    @Test
    public void rejectsMissingMetalSamplesBeforeReconstruction() {
        assertThat(assertThrows(IllegalStateException.class,
                () -> JniMetalNativeBridge.parseEvaluationPayload(null, new long[3])))
                .hasMessage("Metal evaluation returned no samples");
    }

    @Test
    public void keepsMetalTimingHeaderSeparateFromSamples() {
        float[] samples = { 0.125f, -0.25f };

        MetalEvaluationResult result = JniMetalNativeBridge.parseEvaluationPayload(samples,
                new long[] { 123L, 20L, 100L });

        assertThat(result.totalMicros()).isEqualTo(123d);
        assertThat(result.transferMicros()).isEqualTo(20d);
        assertThat(result.kernelMicros()).isEqualTo(100d);
        assertThat(result.logReturns()).containsExactly(0.125f, -0.25f);
    }
}
