/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class NativeLibraryLoaderTest {

    private static final String LIBRARY_PROPERTY = "ta4j.acceleration.loader-test.library";
    private static final String BACKEND = "loader-test";
    /**
     * Packaged resources the tests load, served from memory instead of test
     * resource files so each fixture sits next to the tests that depend on it.
     */
    private static final String RESOURCE_DIRECTORY = "/fixtures/";
    private static final String LIBRARY_NAME = "invalid-native.elf";
    private static final String BAD_CHECKSUM_NAME = "bad-checksum.bin";
    private static final String NO_SIDECAR_NAME = "no-sidecar.bin";
    private static final Map<String, byte[]> FIXTURES = Map.of(
            // A library whose bytes match its sidecar checksum but cannot be loaded.
            RESOURCE_DIRECTORY + LIBRARY_NAME, invalidNativeElf(), RESOURCE_DIRECTORY + LIBRARY_NAME + ".sha256",
            ascii(sha256(invalidNativeElf())),
            // A library whose sidecar checksum does not match its bytes.
            RESOURCE_DIRECTORY + BAD_CHECKSUM_NAME,
            ascii("This resource must be rejected before any native load is attempted.\n"),
            RESOURCE_DIRECTORY + BAD_CHECKSUM_NAME + ".sha256", ascii("0".repeat(64)),
            // A library without a sidecar checksum.
            RESOURCE_DIRECTORY + NO_SIDECAR_NAME, ascii("no checksum sidecar"));

    private final Map<String, String> originalProperties = new HashMap<>();

    @TempDir
    Path tempDir;

    @BeforeEach
    public void preserveProperties() {
        for (String property : List.of(LIBRARY_PROPERTY, CudaNativeLibrary.LIBRARY_PROPERTY,
                MetalNativeLibrary.LIBRARY_PROPERTY, OpenClNativeLibrary.LIBRARY_PROPERTY,
                NativeLibraryLoader.NATIVE_DIR_PROPERTY, "user.home", "os.name", "os.arch")) {
            originalProperties.put(property, System.getProperty(property));
        }
        System.clearProperty(LIBRARY_PROPERTY);
        System.clearProperty(CudaNativeLibrary.LIBRARY_PROPERTY);
        System.clearProperty(MetalNativeLibrary.LIBRARY_PROPERTY);
        System.clearProperty(OpenClNativeLibrary.LIBRARY_PROPERTY);
        System.clearProperty(NativeLibraryLoader.NATIVE_DIR_PROPERTY);
        System.setProperty("user.home", tempDir.toString());
    }

    @AfterEach
    public void restoreProperties() {
        for (Map.Entry<String, String> property : originalProperties.entrySet()) {
            if (property.getValue() == null) {
                System.clearProperty(property.getKey());
            } else {
                System.setProperty(property.getKey(), property.getValue());
            }
        }
    }

    @Test
    public void rejectsRelativeExplicitLibraryPath() {
        System.setProperty(LIBRARY_PROPERTY, "relative-native-library");

        assertThat(load(RESOURCE_DIRECTORY, LIBRARY_NAME)).isEqualTo(new NativeLibraryLoader.LoadResult(false, null,
                "IOException: " + LIBRARY_PROPERTY + " must be an absolute path"));
        assertThat(tempDir.resolve(".ta4j")).doesNotExist();
    }

    @Test
    public void rejectsMissingExplicitLibraryPath() {
        Path missing = tempDir.resolve("missing-native-library");
        System.setProperty(LIBRARY_PROPERTY, missing.toString());

        assertThat(load(RESOURCE_DIRECTORY, LIBRARY_NAME)).isEqualTo(new NativeLibraryLoader.LoadResult(false, null,
                "IOException: Native library is not a regular file: " + missing));
        assertThat(tempDir.resolve(".ta4j")).doesNotExist();
    }

    @Test
    public void rejectsDirectoryConfiguredAsExplicitLibrary() throws Exception {
        Path directory = Files.createDirectory(tempDir.resolve("not-a-library"));
        System.setProperty(LIBRARY_PROPERTY, directory.toString());

        assertThat(load(RESOURCE_DIRECTORY, LIBRARY_NAME)).isEqualTo(new NativeLibraryLoader.LoadResult(false, null,
                "IOException: Native library is not a regular file: " + directory));
        assertThat(directory).isEmptyDirectory();
        assertThat(tempDir.resolve(".ta4j")).doesNotExist();
    }

    @Test
    public void returnsSafeFailureForInvalidExplicitPathSyntax() {
        System.setProperty(LIBRARY_PROPERTY, "invalid\0native-library");

        NativeLibraryLoader.LoadResult result = load(RESOURCE_DIRECTORY, LIBRARY_NAME);

        assertThat(result.loaded()).isFalse();
        assertThat(result.path()).isNull();
        assertThat(result.detail()).startsWith("InvalidPathException: ").isNotBlank();
        assertThat(tempDir.resolve(".ta4j")).doesNotExist();
    }

    @Test
    public void returnsRealNativeLoadFailureWithoutCachingAnInvalidExplicitLibrary() throws Exception {
        Path invalidLibrary = tempDir.resolve(LIBRARY_NAME);
        Files.write(invalidLibrary, fixtureContents());
        System.setProperty(LIBRARY_PROPERTY, invalidLibrary.toString());

        assertInvalidNativeLoad(load("/missing-resource-directory/", "missing-resource"));
        assertThat(Files.readAllBytes(invalidLibrary)).containsExactly(fixtureContents());
        assertThat(tempDir.resolve(".ta4j")).doesNotExist();

        Path missing = tempDir.resolve("missing-after-failed-load");
        System.setProperty(LIBRARY_PROPERTY, missing.toString());
        assertThat(load(RESOURCE_DIRECTORY, LIBRARY_NAME)).isEqualTo(new NativeLibraryLoader.LoadResult(false, null,
                "IOException: Native library is not a regular file: " + missing));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "\t" })
    public void unsetOrBlankExplicitPathReportsMissingPackagedResource(String explicit) {
        if (explicit != null) {
            System.setProperty(LIBRARY_PROPERTY, explicit);
        }

        assertThat(load("/missing-native-classifier/", "missing-native-library"))
                .isEqualTo(new NativeLibraryLoader.LoadResult(false, null,
                        "IOException: Native classifier resource is absent: /missing-native-classifier/missing-native-library"));
        assertThat(tempDir.resolve(".ta4j")).doesNotExist();
    }

    @Test
    public void rejectsPackagedLibraryWithoutChecksumSidecar() {
        assertThat(load(RESOURCE_DIRECTORY, NO_SIDECAR_NAME)).isEqualTo(
                new NativeLibraryLoader.LoadResult(false, null, "IOException: Native classifier resource is absent: "
                        + RESOURCE_DIRECTORY + NO_SIDECAR_NAME + ".sha256"));
        assertThat(tempDir.resolve(".ta4j")).doesNotExist();
    }

    @Test
    public void rejectsPackagedLibraryWhoseChecksumDoesNotMatch() {
        assertThat(load(RESOURCE_DIRECTORY, BAD_CHECKSUM_NAME)).isEqualTo(new NativeLibraryLoader.LoadResult(false,
                null, "IOException: Packaged " + BACKEND + " library checksum mismatch"));
        assertThat(tempDir.resolve(".ta4j")).doesNotExist();
    }

    @Test
    public void extractsVerifiedPackagedLibraryPrivatelyBeforeRealNativeLoadFailure() throws Exception {
        assertInvalidNativeLoad(load(RESOURCE_DIRECTORY, LIBRARY_NAME));

        Path extracted = extractedLibrary();
        assertThat(Files.readAllBytes(extracted)).containsExactly(fixtureContents());
        try (Stream<Path> files = Files.list(extracted.getParent())) {
            assertThat(files).containsExactly(extracted);
        }
        if (tempDir.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Set<PosixFilePermission> privatePermissions = Set.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
            assertThat(Files.getPosixFilePermissions(tempDir.resolve(".ta4j")))
                    .containsExactlyInAnyOrderElementsOf(privatePermissions);
            assertThat(Files.getPosixFilePermissions(extracted.getParent()))
                    .containsExactlyInAnyOrderElementsOf(privatePermissions);
            assertThat(Files.getPosixFilePermissions(extracted))
                    .containsExactlyInAnyOrderElementsOf(privatePermissions);
        }
    }

    @Test
    public void extractsUnderTheConfiguredNativeDirectoryInsteadOfTheHomeDirectory() throws Exception {
        Path nativeDir = tempDir.resolve("native-cache");
        System.setProperty(NativeLibraryLoader.NATIVE_DIR_PROPERTY, nativeDir.toString());

        assertInvalidNativeLoad(load(RESOURCE_DIRECTORY, LIBRARY_NAME));

        Path extracted = nativeDir.resolve(tempDir.resolve(".ta4j/native").relativize(extractedLibrary()));
        assertThat(Files.readAllBytes(extracted)).containsExactly(fixtureContents());
        assertThat(tempDir.resolve(".ta4j")).doesNotExist();
        if (tempDir.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            assertThat(Files.getPosixFilePermissions(nativeDir)).containsExactlyInAnyOrder(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
        }
    }

    @Test
    public void rejectsARelativeConfiguredNativeDirectory() {
        System.setProperty(NativeLibraryLoader.NATIVE_DIR_PROPERTY, "relative-native-cache");

        assertThat(load(RESOURCE_DIRECTORY, LIBRARY_NAME)).isEqualTo(new NativeLibraryLoader.LoadResult(false, null,
                "IOException: " + NativeLibraryLoader.NATIVE_DIR_PROPERTY + " must be an absolute path"));
        assertThat(tempDir.resolve(".ta4j")).doesNotExist();
    }

    @Test
    public void validatesAndReusesExistingExtractionWithoutRewritingIt() throws Exception {
        Path extracted = extractedLibrary();
        NativeLibraryLoader.createPrivateDirectories(tempDir.resolve(".ta4j"), extracted.getParent());
        Files.write(extracted, fixtureContents());
        Files.setLastModifiedTime(extracted, FileTime.fromMillis(1_000L));
        FileTime lastModified = Files.getLastModifiedTime(extracted);
        boolean posix = tempDir.getFileSystem().supportedFileAttributeViews().contains("posix");
        Set<PosixFilePermission> originalPermissions = posix ? Files.getPosixFilePermissions(extracted) : Set.of();

        try {
            assertInvalidNativeLoad(load(RESOURCE_DIRECTORY, LIBRARY_NAME));

            assertThat(Files.getLastModifiedTime(extracted)).isEqualTo(lastModified);
            assertThat(Files.readAllBytes(extracted)).containsExactly(fixtureContents());
            try (Stream<Path> files = Files.list(extracted.getParent())) {
                assertThat(files).containsExactly(extracted);
            }
        } finally {
            if (posix) {
                Files.setPosixFilePermissions(extracted, originalPermissions);
            }
        }
    }

    @Test
    public void rejectsTamperedExistingExtractionWithoutReplacingItOrChangingPermissions() throws Exception {
        Path extracted = extractedLibrary();
        NativeLibraryLoader.createPrivateDirectories(tempDir.resolve(".ta4j"), extracted.getParent());
        Files.writeString(extracted, "tampered");
        boolean posix = tempDir.getFileSystem().supportedFileAttributeViews().contains("posix");
        Set<PosixFilePermission> originalPermissions = posix ? Files.getPosixFilePermissions(extracted) : Set.of();

        try {
            assertThat(load(RESOURCE_DIRECTORY, LIBRARY_NAME)).isEqualTo(new NativeLibraryLoader.LoadResult(false, null,
                    "IOException: Extracted " + BACKEND + " library checksum mismatch at " + extracted));

            assertThat(extracted).hasContent("tampered");
            if (posix) {
                assertThat(Files.getPosixFilePermissions(extracted))
                        .containsExactlyInAnyOrderElementsOf(originalPermissions);
            }
        } finally {
            if (posix) {
                Files.setPosixFilePermissions(extracted, originalPermissions);
            }
        }
    }

    @Test
    public void rejectsDirectoryAtExistingExtractedLibraryPath() throws Exception {
        Path extracted = extractedLibrary();
        NativeLibraryLoader.createPrivateDirectories(tempDir.resolve(".ta4j"), extracted);

        assertThat(load(RESOURCE_DIRECTORY, LIBRARY_NAME)).isEqualTo(new NativeLibraryLoader.LoadResult(false, null,
                "IOException: Extracted " + BACKEND + " library is not a regular file: " + extracted));
        assertThat(extracted).isEmptyDirectory();
    }

    @ParameterizedTest
    @ValueSource(strings = { "../escaped", "../.ta4j-sibling/native" })
    public void rejectsExtractionEscapingPrivateRootBeforeCreatingAnything(String relativeDirectory) {
        Path privateRoot = tempDir.resolve(".ta4j");
        Path destination = privateRoot.resolve(relativeDirectory);

        assertThatIOException().isThrownBy(() -> NativeLibraryLoader.createPrivateDirectories(privateRoot, destination))
                .withMessageContaining("escapes its private root");
        assertThat(privateRoot).doesNotExist();
        assertThat(destination.normalize()).doesNotExist();
    }

    @Test
    public void returnsSafeFailureWhenBackendWouldEscapePrivateExtractionRoot() {
        NativeLibraryLoader.LoadResult result = NativeLibraryLoader.load("../../escaped", LIBRARY_PROPERTY,
                RESOURCE_DIRECTORY, LIBRARY_NAME, 1, FIXTURES::get);

        assertThat(result.loaded()).isFalse();
        assertThat(result.path()).isNull();
        assertThat(result.detail()).startsWith("IOException: Native extraction directory escapes its private root: ");
        assertThat(tempDir.resolve(".ta4j")).doesNotExist();
        assertThat(tempDir.resolve("escaped-abi-1")).doesNotExist();
    }

    @Test
    public void rejectsRegularFileAsPrivateExtractionRootWithoutChangingIt() throws Exception {
        Path privateRoot = tempDir.resolve(".ta4j");
        Files.writeString(privateRoot, "keep-root-file");

        assertThatIOException().isThrownBy(
                () -> NativeLibraryLoader.createPrivateDirectories(privateRoot, privateRoot.resolve("native/backend")))
                .withMessageContaining("not a directory");
        assertThat(load(RESOURCE_DIRECTORY, LIBRARY_NAME)).isEqualTo(new NativeLibraryLoader.LoadResult(false, null,
                "IOException: Native extraction path is not a directory: " + privateRoot));
        assertThat(privateRoot).hasContent("keep-root-file");
    }

    @Test
    public void rejectsRegularFileAsIntermediateExtractionDirectoryWithoutChangingIt() throws Exception {
        Path privateRoot = tempDir.resolve(".ta4j");
        NativeLibraryLoader.createPrivateDirectories(privateRoot, privateRoot);
        Path intermediate = privateRoot.resolve("native");
        Files.writeString(intermediate, "keep-intermediate-file");

        assertThatIOException().isThrownBy(
                () -> NativeLibraryLoader.createPrivateDirectories(privateRoot, intermediate.resolve("backend")))
                .withMessageContaining("not a directory");
        assertThat(load(RESOURCE_DIRECTORY, LIBRARY_NAME)).isEqualTo(new NativeLibraryLoader.LoadResult(false, null,
                "IOException: Native extraction path is not a directory: " + intermediate));
        assertThat(intermediate).hasContent("keep-intermediate-file");
    }

    @Test
    public void rejectsSymbolicLinkExtractionRootAndIntermediateDirectory() throws Exception {
        assumeTrue(tempDir.getFileSystem().supportedFileAttributeViews().contains("posix"));
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Path linkedRoot = Files.createSymbolicLink(tempDir.resolve("linked-root"), outside);

        assertThatIOException()
                .isThrownBy(
                        () -> NativeLibraryLoader.createPrivateDirectories(linkedRoot, linkedRoot.resolve("backend")))
                .withMessageContaining("not a directory");

        Path privateRoot = tempDir.resolve(".ta4j");
        NativeLibraryLoader.createPrivateDirectories(privateRoot, privateRoot);
        Path intermediate = Files.createSymbolicLink(privateRoot.resolve("native"), outside);
        assertThatIOException().isThrownBy(
                () -> NativeLibraryLoader.createPrivateDirectories(privateRoot, intermediate.resolve("backend")))
                .withMessageContaining("not a directory");
        assertThat(load(RESOURCE_DIRECTORY, LIBRARY_NAME)).isEqualTo(new NativeLibraryLoader.LoadResult(false, null,
                "IOException: Native extraction path is not a directory: " + intermediate));
        assertThat(outside).isEmptyDirectory();
        assertThat(linkedRoot).isSymbolicLink();
        assertThat(intermediate).isSymbolicLink();
    }

    @Test
    public void rejectsUnsafeIntermediateExtractionDirectory() throws Exception {
        assumeTrue(tempDir.getFileSystem().supportedFileAttributeViews().contains("posix"));
        Path privateRoot = tempDir.resolve(".ta4j");
        Path intermediate = privateRoot.resolve("native");
        NativeLibraryLoader.createPrivateDirectories(privateRoot, intermediate);
        Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(intermediate);
        try {
            Files.setPosixFilePermissions(intermediate,
                    Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OTHERS_WRITE));

            assertThatIOException().isThrownBy(
                    () -> NativeLibraryLoader.createPrivateDirectories(privateRoot, intermediate.resolve("backend")))
                    .withMessageContaining("writable by other users");
            assertThat(load(RESOURCE_DIRECTORY, LIBRARY_NAME)).isEqualTo(new NativeLibraryLoader.LoadResult(false, null,
                    "IOException: Native extraction directory is writable by other users: " + intermediate));
            assertThat(intermediate.resolve("backend")).doesNotExist();
        } finally {
            Files.setPosixFilePermissions(intermediate, originalPermissions);
        }
    }

    @Test
    public void rejectsMissingExtractedLibrary() {
        Path missing = tempDir.resolve("missing-extracted-library");

        assertThatIOException()
                .isThrownBy(() -> NativeLibraryLoader.finalizeExtractedLibrary(missing, "unused", BACKEND))
                .withMessageContaining("not a regular file");
        assertThat(missing).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(strings = { "aarch64", "s390x" })
    public void cudaRejectsUnsupportedArchitectureWithoutExtractingOrLoading(String architecture) {
        System.setProperty("os.name", "Linux");
        System.setProperty("os.arch", architecture);

        assertThat(CudaNativeLibrary.packagedResourcePresent()).isFalse();
        assertThat(CudaNativeLibrary.load()).isEqualTo(
                new CudaNativeLibrary.LoadResult(false, null, "CUDA provider requires x86_64, found " + architecture));
        assertThat(tempDir.resolve(".ta4j")).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(strings = { "Mac OS X", "FreeBSD" })
    public void cudaRejectsUnsupportedOperatingSystem(String operatingSystem) {
        System.setProperty("os.name", operatingSystem);
        System.setProperty("os.arch", "amd64");

        assertThat(CudaNativeLibrary.packagedResourcePresent()).isFalse();
        assertThat(CudaNativeLibrary.load()).isEqualTo(
                new CudaNativeLibrary.LoadResult(false, null, "CUDA provider requires Windows or Linux x86_64"));
        assertThat(tempDir.resolve(".ta4j")).doesNotExist();
    }

    @ParameterizedTest
    @CsvSource({ "Windows 11, amd64, /META-INF/native/windows-x86_64/ta4j-cuda-accelerator.dll",
            "WINDOWS 11, x86_64, /META-INF/native/windows-x86_64/ta4j-cuda-accelerator.dll",
            "Linux, amd64, /META-INF/native/linux-x86_64/libta4j-cuda-accelerator.so",
            "LINUX, x86_64, /META-INF/native/linux-x86_64/libta4j-cuda-accelerator.so" })
    public void cudaSelectsExpectedClassifierAndReportsSafeAbsence(String operatingSystem, String architecture,
            String resource) {
        System.setProperty("os.name", operatingSystem);
        System.setProperty("os.arch", architecture);
        String detail = classifierFailure(CudaNativeLibrary.LIBRARY_PROPERTY, resource);

        assertThat(CudaNativeLibrary.packagedResourcePresent())
                .isEqualTo(NativeLibraryLoaderTest.class.getResource(resource) != null);
        assertThat(CudaNativeLibrary.load()).isEqualTo(new CudaNativeLibrary.LoadResult(false, null, detail));
        assertThat(tempDir.resolve(".ta4j")).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(strings = { "ppc64le", "s390x" })
    public void openClRejectsUnsupportedArchitectureWithoutExtractingOrLoading(String architecture) {
        System.setProperty("os.name", "Linux");
        System.setProperty("os.arch", architecture);

        assertThat(OpenClNativeLibrary.packagedResourcePresent()).isFalse();
        assertThat(OpenClNativeLibrary.load()).isEqualTo(new OpenClNativeLibrary.LoadResult(false, null,
                "OpenCL provider requires Linux x86_64 or aarch64, found " + architecture));
        assertThat(tempDir.resolve(".ta4j")).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(strings = { "Windows 11", "Mac OS X" })
    public void openClRejectsUnsupportedOperatingSystem(String operatingSystem) {
        System.setProperty("os.name", operatingSystem);
        System.setProperty("os.arch", "amd64");

        assertThat(OpenClNativeLibrary.packagedResourcePresent()).isFalse();
        assertThat(OpenClNativeLibrary.load())
                .isEqualTo(new OpenClNativeLibrary.LoadResult(false, null, "OpenCL provider requires Linux"));
        assertThat(tempDir.resolve(".ta4j")).doesNotExist();
    }

    @ParameterizedTest
    @CsvSource({ "Linux, amd64, /META-INF/native/linux-x86_64/libta4j-opencl-accelerator.so",
            "LINUX, x86_64, /META-INF/native/linux-x86_64/libta4j-opencl-accelerator.so",
            "Linux, aarch64, /META-INF/native/linux-aarch64/libta4j-opencl-accelerator.so",
            "Linux, arm64, /META-INF/native/linux-aarch64/libta4j-opencl-accelerator.so" })
    public void openClSelectsExpectedClassifierAndReportsSafeAbsence(String operatingSystem, String architecture,
            String resource) {
        System.setProperty("os.name", operatingSystem);
        System.setProperty("os.arch", architecture);
        String detail = classifierFailure(OpenClNativeLibrary.LIBRARY_PROPERTY, resource);

        assertThat(OpenClNativeLibrary.packagedResourcePresent())
                .isEqualTo(NativeLibraryLoaderTest.class.getResource(resource) != null);
        assertThat(OpenClNativeLibrary.load()).isEqualTo(new OpenClNativeLibrary.LoadResult(false, null, detail));
        assertThat(tempDir.resolve(".ta4j")).doesNotExist();
    }

    @ParameterizedTest
    @CsvSource({ "Linux, arm64", "Windows 11, aarch64", "Mac OS X, amd64", "Mac OS X, s390x" })
    public void metalRejectsUnsupportedOperatingSystemOrArchitecture(String operatingSystem, String architecture) {
        System.setProperty("os.name", operatingSystem);
        System.setProperty("os.arch", architecture);

        assertThat(MetalNativeLibrary.packagedResourcePresent()).isFalse();
        assertThat(MetalNativeLibrary.load())
                .isEqualTo(new MetalNativeLibrary.LoadResult(false, null, "Metal provider requires macOS arm64"));
        assertThat(tempDir.resolve(".ta4j")).doesNotExist();
    }

    @ParameterizedTest
    @CsvSource({ "Mac OS X, aarch64, /META-INF/native/macos-aarch64/libta4j-metal-accelerator.dylib",
            "MAC OS X, ARM64, /META-INF/native/macos-aarch64/libta4j-metal-accelerator.dylib" })
    public void metalSelectsExpectedClassifierAndReportsSafeAbsence(String operatingSystem, String architecture,
            String resource) {
        System.setProperty("os.name", operatingSystem);
        System.setProperty("os.arch", architecture);
        String detail = classifierFailure(MetalNativeLibrary.LIBRARY_PROPERTY, resource);

        assertThat(MetalNativeLibrary.packagedResourcePresent())
                .isEqualTo(NativeLibraryLoaderTest.class.getResource(resource) != null);
        assertThat(MetalNativeLibrary.load()).isEqualTo(new MetalNativeLibrary.LoadResult(false, null, detail));
        assertThat(tempDir.resolve(".ta4j")).doesNotExist();
    }

    @Test
    public void missingPlatformPropertiesFailClosedWithoutNativeExtraction() {
        System.clearProperty("os.name");
        System.clearProperty("os.arch");

        assertThat(CudaNativeLibrary.packagedResourcePresent()).isFalse();
        assertThat(OpenClNativeLibrary.packagedResourcePresent()).isFalse();
        assertThat(MetalNativeLibrary.packagedResourcePresent()).isFalse();
        assertThat(CudaNativeLibrary.load())
                .isEqualTo(new CudaNativeLibrary.LoadResult(false, null, "CUDA provider requires x86_64, found "));
        assertThat(OpenClNativeLibrary.load()).isEqualTo(new OpenClNativeLibrary.LoadResult(false, null,
                "OpenCL provider requires Linux x86_64 or aarch64, found "));
        assertThat(MetalNativeLibrary.load())
                .isEqualTo(new MetalNativeLibrary.LoadResult(false, null, "Metal provider requires macOS arm64"));
        assertThat(tempDir.resolve(".ta4j")).doesNotExist();
    }

    @Test
    public void createsOwnerPrivateExtractionDirectories() throws Exception {
        Path privateRoot = tempDir.resolve(".ta4j");
        Path destination = privateRoot.resolve("native/metal-abi-1/version/hash");

        NativeLibraryLoader.createPrivateDirectories(privateRoot, destination);
        NativeLibraryLoader.createPrivateDirectories(privateRoot, destination);

        assertThat(privateRoot).isDirectory();
        assertThat(destination).isDirectory();
        if (!tempDir.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            return;
        }

        assertThat(Files.getPosixFilePermissions(privateRoot)).containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
        assertThat(Files.getPosixFilePermissions(destination)).containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
    }

    @ParameterizedTest
    @EnumSource(value = PosixFilePermission.class, names = { "GROUP_WRITE", "OTHERS_WRITE" })
    public void rejectsExtractionThroughDirectoryWritableByOtherUsers(PosixFilePermission unsafePermission)
            throws Exception {
        assumeTrue(tempDir.getFileSystem().supportedFileAttributeViews().contains("posix"));
        Path privateRoot = tempDir.resolve(".ta4j");
        Files.createDirectory(privateRoot);
        Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(privateRoot);
        try {
            Files.setPosixFilePermissions(privateRoot, Set.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE, unsafePermission));

            assertThatIOException()
                    .isThrownBy(() -> NativeLibraryLoader.createPrivateDirectories(privateRoot,
                            privateRoot.resolve("native/metal-abi-1")))
                    .withMessageContaining("writable by other users");
            assertThat(load(RESOURCE_DIRECTORY, LIBRARY_NAME)).isEqualTo(new NativeLibraryLoader.LoadResult(false, null,
                    "IOException: Native extraction directory is writable by other users: " + privateRoot));
        } finally {
            Files.setPosixFilePermissions(privateRoot, originalPermissions);
        }
    }

    @Test
    public void rejectsSymbolicLinkBeforeChangingDestinationPermissions() throws Exception {
        assumeTrue(tempDir.getFileSystem().supportedFileAttributeViews().contains("posix"));
        Path destination = tempDir.resolve("destination.dylib");
        Files.writeString(destination, "native");
        Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(destination);
        Path symbolicLink = Files.createSymbolicLink(tempDir.resolve("ta4j.dylib"), destination);

        try {
            assertThatIOException()
                    .isThrownBy(() -> NativeLibraryLoader.finalizeExtractedLibrary(symbolicLink, "unused", "metal"))
                    .withMessageContaining("not a regular file");
            assertThat(Files.getPosixFilePermissions(destination))
                    .containsExactlyInAnyOrderElementsOf(originalPermissions);
        } finally {
            Files.setPosixFilePermissions(destination, originalPermissions);
        }
    }

    @Test
    public void rejectsExtractedLibraryWithChecksumMismatch() throws Exception {
        Path target = tempDir.resolve("checksum-mismatch.dylib");
        Files.writeString(target, "native");

        assertThatIOException()
                .isThrownBy(() -> NativeLibraryLoader.finalizeExtractedLibrary(target, "0".repeat(64), "metal"))
                .withMessageContaining("checksum mismatch");
    }

    @Test
    public void finalizesExtractedLibraryWithMatchingChecksum() throws Exception {
        Path target = tempDir.resolve("checksum-match.dylib");
        byte[] contents = "native".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        Files.write(target, contents);
        String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contents));
        boolean posix = target.getFileSystem().supportedFileAttributeViews().contains("posix");
        Set<PosixFilePermission> originalPermissions = posix ? Files.getPosixFilePermissions(target) : Set.of();

        try {
            NativeLibraryLoader.finalizeExtractedLibrary(target, checksum.toUpperCase(Locale.ROOT), "metal");

            assertThat(Files.readAllBytes(target)).containsExactly(contents);
            if (posix) {
                assertThat(Files.getPosixFilePermissions(target)).containsExactlyInAnyOrder(
                        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE);
            }
        } finally {
            if (posix) {
                Files.setPosixFilePermissions(target, originalPermissions);
            }
        }
    }

    private NativeLibraryLoader.LoadResult load(String resourceDirectory, String libraryName) {
        return NativeLibraryLoader.load(BACKEND, LIBRARY_PROPERTY, resourceDirectory, libraryName, 1, FIXTURES::get);
    }

    private static byte[] fixtureContents() {
        return invalidNativeElf();
    }

    /**
     * Builds a 176-byte little-endian ELF64 executable that every OS refuses to
     * load as a library: one read-only {@code PT_LOAD} segment, a non-executable
     * {@code PT_GNU_STACK}, and no dynamic section or code. The non-executable
     * stack keeps Linux from warning about executable stacks.
     */
    private static byte[] invalidNativeElf() {
        ByteBuffer elf = ByteBuffer.allocate(176).order(ByteOrder.LITTLE_ENDIAN);
        // ELF header: magic, 64-bit, little-endian, version 1, System V ABI.
        elf.put(new byte[] { 0x7f, 'E', 'L', 'F', 2, 1, 1, 0 }).position(16);
        elf.putShort((short) 2) // e_type: ET_EXEC
                .putShort((short) 0x3e) // e_machine: x86-64
                .putInt(1) // e_version
                .putLong(0) // e_entry
                .putLong(64) // e_phoff: program headers follow the header
                .putLong(0) // e_shoff: no section headers
                .putInt(0) // e_flags
                .putShort((short) 64) // e_ehsize
                .putShort((short) 56) // e_phentsize
                .putShort((short) 2) // e_phnum
                .putShort((short) 64) // e_shentsize
                .putShort((short) 0) // e_shnum
                .putShort((short) 0); // e_shstrndx
        // PT_LOAD, readable only, mapping the whole file.
        elf.putInt(1).putInt(4).putLong(0).putLong(0).putLong(0).putLong(176).putLong(176).putLong(0x1000);
        // PT_GNU_STACK, readable and writable but not executable.
        elf.putInt(0x6474e551).putInt(6).putLong(0).putLong(0).putLong(0).putLong(0).putLong(0).putLong(16);
        return elf.array();
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private Path extractedLibrary() throws Exception {
        String implementationVersion = NativeLibraryLoader.class.getPackage().getImplementationVersion();
        String version = implementationVersion == null || implementationVersion.isBlank() ? "development"
                : implementationVersion;
        String checksum = sha256(fixtureContents());
        return tempDir.resolve(".ta4j/native")
                .resolve(BACKEND + "-abi-1")
                .resolve(version)
                .resolve(checksum)
                .resolve(LIBRARY_NAME);
    }

    private static void assertInvalidNativeLoad(NativeLibraryLoader.LoadResult result) {
        assertThat(result.loaded()).isFalse();
        assertThat(result.path()).isNull();
        assertThat(result.detail()).startsWith("UnsatisfiedLinkError: ").isNotBlank();
    }

    private String classifierFailure(String libraryProperty, String resource) {
        // Native-enabled Maven profiles may supply this classifier. Keep those tests
        // failure-only too: never load an installed SDK or a real packaged library.
        if (NativeLibraryLoaderTest.class.getResource(resource) != null) {
            Path missing = tempDir.resolve("missing-configured-native-library");
            System.setProperty(libraryProperty, missing.toString());
            return "IOException: Native library is not a regular file: " + missing;
        }
        System.clearProperty(libraryProperty);
        return "IOException: Native classifier resource is absent: " + resource;
    }
}
