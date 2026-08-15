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
 * The native library cannot be built on macOS, so this test compiles a tiny JNI
 * stub (see {@code ta4j-cli/src/test/resources/native-stubs}) that serves
 * configurable payloads through the real {@link JniOpenClNativeBridge} parsing
 * code. The stub is only used to exercise the Java-side payload contract.
 */
class OpenClProbePayloadTest {

    private static final Path PAYLOAD_FILE = Path.of("/tmp/ta4j-probe-payload.txt");
    private static final Path STUB_SOURCE = Path.of("src/test/resources/native-stubs/opencl_probe_stub.c")
            .toAbsolutePath();
    private static final Path STUB_LIBRARY = Path.of(System.getProperty("java.io.tmpdir"))
            .resolve("libJniOpenClProbeStub" + librarySuffix());
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

    @Test
    void stubCompilesAgainstALinuxStyleJdkLayout() throws Exception {
        // Hosted CI (test.yml) runs the full gate on ubuntu-latest, where the
        // JDK's platform include directory is include/linux (jni_md.h lives
        // there), not include/darwin. This untagged test must therefore not
        // hardcode the macOS layout. Reproduce the Linux JDK layout locally
        // and compile through the same production command path to prove the
        // stub builds on the CI platform.
        Path fakeJdk = Files.createTempDirectory("ta4j-fake-linux-jdk-");
        Path fakeInclude = fakeJdk.resolve("include");
        Path realInclude = Path.of(System.getProperty("java.home"), "include");
        try {
            Files.createDirectories(fakeInclude.resolve("linux"));
            Files.copy(realInclude.resolve("jni.h"), fakeInclude.resolve("jni.h"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.copy(platformIncludeDirectory(System.getProperty("java.home")).resolve("jni_md.h"),
                    fakeInclude.resolve("linux").resolve("jni_md.h"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            Path library = Path.of(System.getProperty("java.io.tmpdir"))
                    .resolve("libJniOpenClProbeStub-linux" + librarySuffix());
            Files.deleteIfExists(library);
            String originalOsName = System.getProperty("os.name");
            try {
                // The production compile path picks the platform include
                // directory from os.name; simulate the Linux CI host.
                System.setProperty("os.name", "linux");
                compileStub(fakeJdk.toString(), library);
                assertThat(Files.isRegularFile(library)).as("stub must compile against the Linux JDK layout").isTrue();
            } finally {
                if (originalOsName == null) {
                    System.clearProperty("os.name");
                } else {
                    System.setProperty("os.name", originalOsName);
                }
                Files.deleteIfExists(library);
            }
        } finally {
            try (var paths = Files.walk(fakeJdk)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // best-effort temp cleanup
                    }
                });
            }
        }
    }

    @Test
    void probeOkPayloadWithPipeInDeviceNameStillParses() throws Exception {
        // The native OK payload embeds the device name verbatim (the native
        // side sanitizes the ERROR detail but not the OK device name). Vendor
        // device names are arbitrary strings; a '|' inside the name shifts the
        // fixed numeric fields. The bridge must still parse the trailing
        // fields (major|minor|free|total|driver|runtime|gpu|detail) instead
        // of reporting the device unavailable with a number-parse error.
        ensureStubLoaded();
        Files.writeString(PAYLOAD_FILE, "OK|Pipe|Device|3|0|8589934592|8589934592|0|0|1|self-test passed",
                StandardCharsets.US_ASCII);

        OpenClProbeResult result = new JniOpenClNativeBridge().probe();

        assertThat(result.available()).isTrue();
        assertThat(result.deviceName()).isEqualTo("Pipe|Device");
        assertThat(result.gpuDevice()).isTrue();
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
            throw new IOException("timed out compiling the OpenCL probe stub");
        }
        if (process.exitValue() != 0) {
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new IOException("failed to compile the OpenCL probe stub:\n" + output);
        }
        if (!Files.isRegularFile(library)) {
            throw new IOException("OpenCL probe stub was not produced at " + library);
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
