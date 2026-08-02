/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

final class CudaNativeLibrary {

    static final String LIBRARY_PROPERTY = "ta4j.acceleration.cuda.library";

    private static final String RESOURCE_DIRECTORY = "/META-INF/native/windows-x86_64/";
    private static final String LIBRARY_NAME = "ta4j-cuda-accelerator.dll";
    private static final Object LOAD_LOCK = new Object();

    private static Path loadedPath;

    private CudaNativeLibrary() {
    }

    static LoadResult load() {
        String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (!operatingSystem.contains("windows")) {
            return LoadResult.failure("CUDA Windows provider requires Windows x86_64");
        }
        if (!(architecture.equals("amd64") || architecture.equals("x86_64"))) {
            return LoadResult.failure("CUDA Windows provider requires x86_64, found " + architecture);
        }

        String explicit = System.getProperty(LIBRARY_PROPERTY);
        try {
            Path library = explicit == null || explicit.isBlank() ? extractPackagedLibrary()
                    : validateExplicitLibrary(explicit);
            synchronized (LOAD_LOCK) {
                Path normalized = library.toRealPath();
                if (loadedPath != null) {
                    return loadedPath.equals(normalized) ? LoadResult.success(normalized)
                            : LoadResult
                                    .failure("A different CUDA native library is already loaded from " + loadedPath);
                }
                System.load(normalized.toString());
                loadedPath = normalized;
                return LoadResult.success(normalized);
            }
        } catch (IOException | LinkageError | RuntimeException exception) {
            return LoadResult.failure(exception.getClass().getSimpleName() + ": " + safeMessage(exception));
        }
    }

    private static Path validateExplicitLibrary(String configuredPath) throws IOException {
        Path library = Path.of(configuredPath);
        if (!library.isAbsolute()) {
            throw new IOException(LIBRARY_PROPERTY + " must be an absolute path");
        }
        if (!Files.isRegularFile(library)) {
            throw new IOException("CUDA native library is not a regular file: " + library);
        }
        return library;
    }

    private static Path extractPackagedLibrary() throws IOException {
        byte[] libraryBytes = readResource(RESOURCE_DIRECTORY + LIBRARY_NAME);
        String expectedSha = new String(readResource(RESOURCE_DIRECTORY + LIBRARY_NAME + ".sha256"),
                StandardCharsets.US_ASCII).trim();
        String actualSha = sha256(libraryBytes);
        if (!actualSha.equalsIgnoreCase(expectedSha)) {
            throw new IOException("Packaged CUDA library checksum mismatch");
        }
        String implementationVersion = CudaNativeLibrary.class.getPackage().getImplementationVersion();
        String version = implementationVersion == null || implementationVersion.isBlank() ? "development"
                : implementationVersion;
        Path directory = Path.of(System.getProperty("user.home"), ".ta4j", "native",
                "cuda-abi-" + CudaNativeBridge.ABI_VERSION, version, actualSha);
        Files.createDirectories(directory);
        Path target = directory.resolve(LIBRARY_NAME);
        if (Files.exists(target)) {
            if (!sha256(Files.readAllBytes(target)).equalsIgnoreCase(actualSha)) {
                throw new IOException("Extracted CUDA library checksum mismatch at " + target);
            }
            return target;
        }
        Path temporary = Files.createTempFile(directory, LIBRARY_NAME, ".tmp");
        try {
            Files.write(temporary, libraryBytes);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                try {
                    Files.move(temporary, target);
                } catch (FileAlreadyExistsException concurrentInstall) {
                    // Another process installed the same content first.
                }
            } catch (FileAlreadyExistsException concurrentInstall) {
                // Another process installed the same content first.
            }
            if (!sha256(Files.readAllBytes(target)).equalsIgnoreCase(actualSha)) {
                throw new IOException("Concurrent CUDA library extraction produced a checksum mismatch at " + target);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return target;
    }

    private static byte[] readResource(String resourceName) throws IOException {
        try (InputStream input = CudaNativeLibrary.class.getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new IOException("CUDA classifier resource is absent: " + resourceName + "; set "
                        + LIBRARY_PROPERTY + " for a developer build");
            }
            return input.readAllBytes();
        }
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? "no detail" : message;
    }

    record LoadResult(boolean loaded, Path path, String detail) {

        private static LoadResult success(Path path) {
            return new LoadResult(true, path, "");
        }

        private static LoadResult failure(String detail) {
            return new LoadResult(false, null, detail);
        }
    }
}
