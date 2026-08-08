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
 * The native OpenCL probe reports failures as a 10-field payload whose last
 * field carries the actionable detail (for example
 * {@code ERROR||||||||0|device lacks FP64}). The Java bridge must surface that
 * detail; otherwise every native probe failure is reported as a meaningless
 * number-parse error and users cannot diagnose why OpenCL is unavailable.
 *
 * <p>
 * The native library cannot be built on macOS, so this test compiles a tiny
 * JNI stub (see {@code ta4j-cli/src/test/resources/native-stubs}) that serves
 * configurable payloads through the real {@link JniOpenClNativeBridge} parsing
 * code. The stub is only used to exercise the Java-side payload contract.
 */
class OpenClProbePayloadTest {

    private static final Path PAYLOAD_FILE = Path.of("/tmp/ta4j-probe-payload.txt");
    private static final Path STUB_SOURCE = Path.of("src/test/resources/native-stubs/opencl_probe_stub.c")
            .toAbsolutePath();
    private static final Path STUB_LIBRARY = Path.of(System.getProperty("java.io.tmpdir"))
            .resolve("libJniOpenClProbeStub.dylib");
    private static volatile boolean stubCompiled;
    private static volatile boolean stubLoaded;

    @AfterEach
    void deletePayload() throws IOException {
        Files.deleteIfExists(PAYLOAD_FILE);
    }

    @Test
    void probeErrorPayloadSurfacesTheNativeDetail() throws Exception {
        ensureStubLoaded();
        Files.writeString(PAYLOAD_FILE, "ERROR||||||||0|device lacks FP64", StandardCharsets.US_ASCII);

        OpenClProbeResult result = new JniOpenClNativeBridge().probe();

        assertThat(result.available()).isFalse();
        assertThat(result.detail()).contains("device lacks FP64");
    }

    @Test
    void probeOkPayloadStillParses() throws Exception {
        ensureStubLoaded();
        Files.writeString(PAYLOAD_FILE, "OK|PoCL CPU|3|0|8589934592|8589934592|0|0|0|self-test passed",
                StandardCharsets.US_ASCII);

        OpenClProbeResult result = new JniOpenClNativeBridge().probe();

        assertThat(result.available()).isTrue();
        assertThat(result.deviceName()).isEqualTo("PoCL CPU");
        assertThat(result.gpuDevice()).isFalse();
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
        String javaHome = System.getProperty("java.home");
        Path include = Path.of(javaHome, "include");
        Path includePlatform = Path.of(javaHome, "include", "darwin");
        Files.deleteIfExists(STUB_LIBRARY);
        Process process = new ProcessBuilder("cc", "-shared", "-fPIC", "-I" + include, "-I" + includePlatform, "-o",
                STUB_LIBRARY.toString(), STUB_SOURCE.toString()).redirectErrorStream(true).start();
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("timed out compiling the OpenCL probe stub");
        }
        if (process.exitValue() != 0) {
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new IOException("failed to compile the OpenCL probe stub:\n" + output);
        }
        if (!Files.isRegularFile(STUB_LIBRARY)) {
            throw new IOException("OpenCL probe stub was not produced at " + STUB_LIBRARY);
        }
    }
}
