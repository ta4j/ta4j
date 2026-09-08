/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;

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
}
