/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

import java.nio.file.Path;
import java.util.Locale;

final class CudaNativeLibrary {

    static final String LIBRARY_PROPERTY = "ta4j.acceleration.cuda.library";

    private CudaNativeLibrary() {
    }

    /**
     * Answers whether a packaged CUDA library exists for this platform without
     * extracting or loading native code. Assessment calls this so library presence
     * never initializes the native lane.
     */
    static boolean packagedResourcePresent() {
        String resource = platformResource();
        return resource != null && CudaNativeLibrary.class.getResource(resource) != null;
    }

    static LoadResult load() {
        String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (!(architecture.equals("amd64") || architecture.equals("x86_64"))) {
            return LoadResult.failure("CUDA provider requires x86_64, found " + architecture);
        }
        String resource = platformResource();
        if (resource == null) {
            return LoadResult.failure("CUDA provider requires Windows or Linux x86_64");
        }
        int lastSlash = resource.lastIndexOf('/') + 1;
        NativeLibraryLoader.LoadResult loaded = NativeLibraryLoader.load("cuda", LIBRARY_PROPERTY,
                resource.substring(0, lastSlash), resource.substring(lastSlash), CudaNativeBridge.ABI_VERSION);
        return new LoadResult(loaded.loaded(), loaded.path(), loaded.detail());
    }

    private static String platformResource() {
        String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (!(architecture.equals("amd64") || architecture.equals("x86_64"))) {
            return null;
        }
        if (operatingSystem.contains("windows")) {
            return "/META-INF/native/windows-x86_64/ta4j-cuda-accelerator.dll";
        }
        if (operatingSystem.contains("linux")) {
            return "/META-INF/native/linux-x86_64/libta4j-cuda-accelerator.so";
        }
        return null;
    }

    record LoadResult(boolean loaded, Path path, String detail) {

        private static LoadResult failure(String detail) {
            return new LoadResult(false, null, detail);
        }
    }
}
