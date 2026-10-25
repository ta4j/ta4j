/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Verifies the Java-side OpenCL probe payload contract. Native integration
 * tests cover the JNI bridge; these tests keep payload parsing deterministic
 * and independent of a compiler, filesystem fixture, and platform loader.
 */
class OpenClProbePayloadTest {

    @Test
    void probeErrorPayloadSurfacesTheNativeDetail() {
        OpenClProbeResult result = JniOpenClNativeBridge.parseProbePayload("ERROR||||||||0|device lacks FP64");

        assertThat(result.available()).isFalse();
        assertThat(result.detail()).isEqualTo("device lacks FP64");
    }

    @Test
    void probeOkPayloadStillParses() {
        OpenClProbeResult result = JniOpenClNativeBridge
                .parseProbePayload("OK|PoCL CPU|3|0|8589934592|8589934592|0|0|0|self-test passed");

        assertThat(result.available()).isTrue();
        assertThat(result.deviceName()).isEqualTo("PoCL CPU");
        assertThat(result.gpuDevice()).isFalse();
        assertThat(result.detail()).isEqualTo("self-test passed");
    }

    @Test
    void probeOkPayloadWithPipeInDeviceNameStillParses() {
        OpenClProbeResult result = JniOpenClNativeBridge
                .parseProbePayload("OK|Pipe|Device|3|0|8589934592|8589934592|0|0|1|self-test passed");

        assertThat(result.available()).isTrue();
        assertThat(result.deviceName()).isEqualTo("Pipe|Device");
        assertThat(result.gpuDevice()).isTrue();
    }

    @Test
    void missingPayloadIsReportedWithoutNativeCode() {
        OpenClProbeResult result = JniOpenClNativeBridge.parseProbePayload(null);

        assertThat(result.available()).isFalse();
        assertThat(result.detail()).isEqualTo("OpenCL probe returned no metadata");
    }

    @Test
    public void rejectsBlankTruncatedAndNonnumericOpenClMetadata() {
        for (String payload : new String[] { "", " \t", "OK|device", "OK|device|bad|0|1000|2000|0|0|1|ready" }) {
            OpenClProbeResult result = JniOpenClNativeBridge.parseProbePayload(payload);

            assertThat(result.available()).as("payload %s", payload).isFalse();
            assertThat(result.detail()).contains(payload.isBlank() ? "no metadata" : "Malformed OpenCL probe");
        }
    }

    @Test
    public void rejectsMissingAndTruncatedOpenClEvaluationHeaders() {
        assertThrows(IllegalStateException.class, () -> JniOpenClNativeBridge.parseEvaluationPayload(null));
        for (int length = 0; length < 4; length++) {
            double[] truncated = new double[length];
            assertThat(assertThrows(IllegalStateException.class,
                    () -> JniOpenClNativeBridge.parseEvaluationPayload(truncated)))
                    .hasMessage("OpenCL evaluation returned no result payload");
        }
    }

    @Test
    public void isolatesOpenClSamplesFromNativeAndConsumerMutation() {
        double[] payload = { 11d, 2d, 7d, 1d, 1e-50d, 1e40d, -0.25d };

        OpenClEvaluationResult result = JniOpenClNativeBridge.parseEvaluationPayload(payload);
        payload[4] = 42d;
        double[] consumerCopy = result.logReturns();
        consumerCopy[1] = -1d;

        assertThat(result.totalMicros()).isEqualTo(11d);
        assertThat(result.transferMicros()).isEqualTo(2d);
        assertThat(result.kernelMicros()).isEqualTo(7d);
        assertThat(result.reductionMicros()).isEqualTo(1d);
        assertThat(result.logReturns()).containsExactly(1e-50d, 1e40d, -0.25d);
    }
}
