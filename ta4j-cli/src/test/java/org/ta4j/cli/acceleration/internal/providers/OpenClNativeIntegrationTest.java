/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Exercises the real OpenCL JNI lane against a built native library: the bridge
 * is loaded through {@link OpenClNativeLibrary#load()} and then probed via
 * {@link JniOpenClNativeBridge#probe()}. The probe performs the native device
 * self-test, so a green run proves the library links, a device with FP64 is
 * advertised, and the metadata round-trips.
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
    }
}