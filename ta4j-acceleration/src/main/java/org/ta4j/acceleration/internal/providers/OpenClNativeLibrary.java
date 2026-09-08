/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

import java.nio.file.Path;
import java.util.Locale;

final class OpenClNativeLibrary {

    static final String LIBRARY_PROPERTY = "ta4j.acceleration.opencl.library";

    private OpenClNativeLibrary() {
    }

    /**
     * Answers whether a packaged OpenCL library exists for this platform without
     * extracting or loading native code. Assessment calls this so library presence
     * never initializes the native lane.
     */
    static boolean packagedResourcePresent() {
        String resource = platformResource();
        return resource != null && OpenClNativeLibrary.class.getResource(resource) != null;
    }

    static LoadResult load() {
        String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (!supportedArchitecture(architecture)) {
            return LoadResult.failure("OpenCL provider requires Linux x86_64 or aarch64, found " + architecture);
        }
        String resource = platformResource();
        if (resource == null) {
            return LoadResult.failure("OpenCL provider requires Linux");
        }
        int lastSlash = resource.lastIndexOf('/') + 1;
        NativeLibraryLoader.LoadResult loaded = NativeLibraryLoader.load("opencl", LIBRARY_PROPERTY,
                resource.substring(0, lastSlash), resource.substring(lastSlash), OpenClNativeBridge.ABI_VERSION);
        return new LoadResult(loaded.loaded(), loaded.path(), loaded.detail());
    }

    private static String platformResource() {
        String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!operatingSystem.contains("linux")) {
            return null;
        }
        String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (architecture.equals("amd64") || architecture.equals("x86_64")) {
            return "/META-INF/native/linux-x86_64/libta4j-opencl-accelerator.so";
        }
        if (architecture.equals("aarch64") || architecture.equals("arm64")) {
            return "/META-INF/native/linux-aarch64/libta4j-opencl-accelerator.so";
        }
        return null;
    }

    private static boolean supportedArchitecture(String architecture) {
        return architecture.equals("amd64") || architecture.equals("x86_64") || architecture.equals("aarch64")
                || architecture.equals("arm64");
    }

    record LoadResult(boolean loaded, Path path, String detail) {

        private static LoadResult failure(String detail) {
            return new LoadResult(false, null, detail);
        }
    }
}
