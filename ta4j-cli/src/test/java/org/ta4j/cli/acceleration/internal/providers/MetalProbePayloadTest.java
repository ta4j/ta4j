/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The native Metal probe reports failures as a 4-field payload whose last field
 * carries the actionable detail (for example
 * {@code ERROR|||metal_device_unavailable}). The Java bridge must surface that
 * detail; otherwise every native probe failure is reported as a meaningless
 * number-parse error and users cannot diagnose why Metal is unavailable.
 *
 * <p>
 * The native library cannot be built in this environment, so this test compiles
 * a tiny JNI stub (see {@code ta4j-cli/src/test/resources/native-stubs}) that
 * serves configurable payloads through the real {@link JniMetalNativeBridge}
 * parsing code. The stub is only used to exercise the Java-side payload
 * contract.
 */
class MetalProbePayloadTest {

    private static final Path PAYLOAD_FILE = Path.of("/tmp/ta4j-metal-probe-payload.txt");
    private static final Path STUB_SOURCE = Path.of("src/test/resources/native-stubs/metal_probe_stub.c")
            .toAbsolutePath();
    private static final Path STUB_LIBRARY = Path.of(System.getProperty("java.io.tmpdir"))
            .resolve("libJniMetalProbeStub" + librarySuffix());
    private static volatile boolean stubCompiled;
    private static volatile boolean stubLoaded;

    @AfterEach
    void deletePayload() throws IOException {
        Files.deleteIfExists(PAYLOAD_FILE);
    }

    @Test
    void probeErrorPayloadSurfacesTheNativeDetail() throws Exception {
        ensureStubLoaded();
        Files.writeString(PAYLOAD_FILE, "ERROR|||metal_device_unavailable", StandardCharsets.US_ASCII);

        MetalProbeResult result = new JniMetalNativeBridge().probe();

        assertThat(result.available()).isFalse();
        assertThat(result.detail()).contains("metal_device_unavailable");
    }

    @Test
    void probeOkPayloadStillParses() throws Exception {
        ensureStubLoaded();
        Files.writeString(PAYLOAD_FILE, "OK|Apple M5 Max|68719476736|ready", StandardCharsets.US_ASCII);

        MetalProbeResult result = new JniMetalNativeBridge().probe();

        assertThat(result.available()).isTrue();
        assertThat(result.deviceName()).isEqualTo("Apple M5 Max");
        assertThat(result.recommendedMaxWorkingSetBytes()).isEqualTo(68_719_476_736L);
    }

    private static void ensureStubLoaded() throws Exception {
        if (stubLoaded) {
            return;
        }
        if (!stubCompiled) {
            compileStub();
            stubCompiled = true;
        }
        System.load(STUB_LIBRARY.toString());
        stubLoaded = true;
    }

    private static void compileStub() throws Exception {
        compileStub(System.getProperty("java.home"), STUB_LIBRARY);
    }

    private static void compileStub(String javaHome, Path library) throws Exception {
        Path include = Path.of(javaHome, "include");
        Path includePlatform = platformIncludeDirectory(javaHome);
        Files.deleteIfExists(library);
        Process process = new ProcessBuilder("cc", "-shared", "-fPIC", "-I" + include, "-I" + includePlatform, "-o",
                library.toString(), STUB_SOURCE.toString()).redirectErrorStream(true).start();
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("timed out compiling the Metal probe stub");
        }
        if (process.exitValue() != 0) {
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new IOException("failed to compile the Metal probe stub:\n" + output);
        }
        if (!Files.isRegularFile(library)) {
            throw new IOException("Metal probe stub was not produced at " + library);
        }
    }

    /**
     * The JDK's platform-specific include directory (where {@code jni_md.h} lives)
     * differs per OS: {@code include/darwin} on macOS, {@code
     * include/linux} on Linux, {@code include/win32} on Windows. Hosted CI runs on
     * ubuntu-latest, so the macOS layout must not be hardcoded.
     */
    private static Path platformIncludeDirectory(String javaHome) {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        String platform;
        if (os.contains("mac")) {
            platform = "darwin";
        } else if (os.contains("win")) {
            platform = "win32";
        } else {
            platform = "linux";
        }
        return Path.of(javaHome, "include", platform);
    }

    private static String librarySuffix() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (os.contains("win")) {
            return ".dll";
        }
        if (os.contains("mac")) {
            return ".dylib";
        }
        return ".so";
    }
}
