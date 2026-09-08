/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeLibraryLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void createsOwnerPrivateExtractionDirectories() throws Exception {
        assumeTrue(tempDir.getFileSystem().supportedFileAttributeViews().contains("posix"));
        Path privateRoot = tempDir.resolve(".ta4j");
        Path destination = privateRoot.resolve("native/metal-abi-1/version/hash");

        NativeLibraryLoader.createPrivateDirectories(privateRoot, destination);

        assertThat(Files.getPosixFilePermissions(privateRoot)).containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
        assertThat(Files.getPosixFilePermissions(destination)).containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
    }

    @Test
    void rejectsExtractionThroughDirectoryWritableByOtherUsers() throws Exception {
        assumeTrue(tempDir.getFileSystem().supportedFileAttributeViews().contains("posix"));
        Path privateRoot = tempDir.resolve(".ta4j");
        Files.createDirectory(privateRoot);
        Files.setPosixFilePermissions(privateRoot, Set.of(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_WRITE));

        assertThatIOException().isThrownBy(() -> NativeLibraryLoader.createPrivateDirectories(privateRoot,
                privateRoot.resolve("native/metal-abi-1"))).withMessageContaining("writable by other users");
    }

    @Test
    void rejectsSymbolicLinkBeforeChangingDestinationPermissions() throws Exception {
        assumeTrue(tempDir.getFileSystem().supportedFileAttributeViews().contains("posix"));
        Path destination = tempDir.resolve("destination.dylib");
        Set<PosixFilePermission> originalPermissions = Set.of(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE);
        Files.writeString(destination, "native");
        Files.setPosixFilePermissions(destination, originalPermissions);
        Path symbolicLink = Files.createSymbolicLink(tempDir.resolve("ta4j.dylib"), destination);

        assertThatIOException()
                .isThrownBy(() -> NativeLibraryLoader.finalizeExtractedLibrary(symbolicLink, "unused", "metal"))
                .withMessageContaining("not a regular file");
        assertThat(Files.getPosixFilePermissions(destination)).containsExactlyInAnyOrderElementsOf(originalPermissions);
    }

    @Test
    void rejectsExtractedLibraryWithChecksumMismatch() throws Exception {
        Path target = tempDir.resolve("checksum-mismatch.dylib");
        Files.writeString(target, "native");

        assertThatIOException()
                .isThrownBy(() -> NativeLibraryLoader.finalizeExtractedLibrary(target, "0".repeat(64), "metal"))
                .withMessageContaining("checksum mismatch");
    }

    @Test
    void finalizesExtractedLibraryWithMatchingChecksum() throws Exception {
        Path target = tempDir.resolve("checksum-match.dylib");
        byte[] contents = "native".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        Files.write(target, contents);
        String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contents));

        NativeLibraryLoader.finalizeExtractedLibrary(target, checksum, "metal");

        if (target.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            assertThat(Files.getPosixFilePermissions(target)).contains(PosixFilePermission.OWNER_EXECUTE);
        }
    }
}
