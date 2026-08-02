/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

final class NativeLibraryLoader {

    private static final Object LOAD_LOCK = new Object();
    private static final Map<String, Path> LOADED_PATHS = new HashMap<>();

    private NativeLibraryLoader() {
    }

    static LoadResult load(String backend, String libraryProperty, String resourceDirectory, String libraryName,
            int abiVersion) {
        String explicit = System.getProperty(libraryProperty);
        try {
            Path library = explicit == null || explicit.isBlank()
                    ? extractPackagedLibrary(backend, resourceDirectory, libraryName, abiVersion)
                    : validateExplicitLibrary(libraryProperty, explicit);
            synchronized (LOAD_LOCK) {
                Path normalized = library.toRealPath();
                Path loaded = LOADED_PATHS.get(backend);
                if (loaded != null) {
                    return loaded.equals(normalized) ? LoadResult.success(normalized)
                            : LoadResult.failure(
                                    "A different " + backend + " native library is already loaded from " + loaded);
                }
                System.load(normalized.toString());
                LOADED_PATHS.put(backend, normalized);
                return LoadResult.success(normalized);
            }
        } catch (IOException | LinkageError | RuntimeException exception) {
            return LoadResult.failure(exception.getClass().getSimpleName() + ": " + safeMessage(exception));
        }
    }

    private static Path validateExplicitLibrary(String libraryProperty, String configuredPath) throws IOException {
        Path library = Path.of(configuredPath);
        if (!library.isAbsolute()) {
            throw new IOException(libraryProperty + " must be an absolute path");
        }
        if (!Files.isRegularFile(library)) {
            throw new IOException("Native library is not a regular file: " + library);
        }
        return library;
    }

    private static Path extractPackagedLibrary(String backend, String resourceDirectory, String libraryName,
            int abiVersion) throws IOException {
        byte[] libraryBytes = readResource(resourceDirectory + libraryName);
        String expectedSha = new String(readResource(resourceDirectory + libraryName + ".sha256"),
                StandardCharsets.US_ASCII).trim();
        String actualSha = sha256(libraryBytes);
        if (!actualSha.equalsIgnoreCase(expectedSha)) {
            throw new IOException("Packaged " + backend + " library checksum mismatch");
        }
        String implementationVersion = NativeLibraryLoader.class.getPackage().getImplementationVersion();
        String version = implementationVersion == null || implementationVersion.isBlank() ? "development"
                : implementationVersion;
        Path directory = Path.of(System.getProperty("user.home"), ".ta4j", "native", backend + "-abi-" + abiVersion,
                version, actualSha);
        Files.createDirectories(directory);
        Path target = directory.resolve(libraryName);
        if (Files.exists(target)) {
            verifyExtracted(target, actualSha, backend);
            makeExecutable(target);
            return target;
        }

        Path temporary = Files.createTempFile(directory, libraryName, ".tmp");
        try {
            Files.write(temporary, libraryBytes);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                try {
                    Files.move(temporary, target);
                } catch (FileAlreadyExistsException concurrentInstall) {
                    // Another process installed the identical checksum first.
                }
            } catch (FileAlreadyExistsException concurrentInstall) {
                // Another process installed the identical checksum first.
            }
            verifyExtracted(target, actualSha, backend);
            makeExecutable(target);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return target;
    }

    private static void verifyExtracted(Path target, String expectedSha, String backend) throws IOException {
        if (!sha256(Files.readAllBytes(target)).equalsIgnoreCase(expectedSha)) {
            throw new IOException("Extracted " + backend + " library checksum mismatch at " + target);
        }
    }

    private static void makeExecutable(Path target) throws IOException {
        try {
            Set<PosixFilePermission> permissions = EnumSet.copyOf(Files.getPosixFilePermissions(target));
            permissions.add(PosixFilePermission.OWNER_READ);
            permissions.add(PosixFilePermission.OWNER_WRITE);
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(target, permissions);
        } catch (UnsupportedOperationException ignored) {
            // Windows filesystems do not expose POSIX permissions.
        }
    }

    private static byte[] readResource(String resourceName) throws IOException {
        try (InputStream input = NativeLibraryLoader.class.getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new IOException("Native classifier resource is absent: " + resourceName);
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
