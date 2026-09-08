/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

import java.nio.file.Path;
import java.util.Locale;

final class MetalNativeLibrary {

    static final String LIBRARY_PROPERTY = "ta4j.acceleration.metal.library";

    private MetalNativeLibrary() {
    }

    /**
     * Answers whether a packaged Metal library exists for this platform without
     * extracting or loading native code. Assessment calls this so library presence
     * never initializes the native lane.
     */
    static boolean packagedResourcePresent() {
        String resource = platformResource();
        return resource != null && MetalNativeLibrary.class.getResource(resource) != null;
    }

    static LoadResult load() {
        String resource = platformResource();
        if (resource == null) {
            return LoadResult.failure("Metal provider requires macOS arm64");
        }
        int lastSlash = resource.lastIndexOf('/') + 1;
        NativeLibraryLoader.LoadResult loaded = NativeLibraryLoader.load("metal", LIBRARY_PROPERTY,
                resource.substring(0, lastSlash), resource.substring(lastSlash), MetalNativeBridge.ABI_VERSION);
        return new LoadResult(loaded.loaded(), loaded.path(), loaded.detail());
    }

    private static String platformResource() {
        String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (!operatingSystem.contains("mac") || !(architecture.equals("aarch64") || architecture.equals("arm64"))) {
            return null;
        }
        return "/META-INF/native/macos-aarch64/libta4j-metal-accelerator.dylib";
    }

    record LoadResult(boolean loaded, Path path, String detail) {

        private static LoadResult failure(String detail) {
            return new LoadResult(false, null, detail);
        }
    }
}
