/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

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
        String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (!(architecture.equals("amd64") || architecture.equals("x86_64"))) {
            return false;
        }
        if (operatingSystem.contains("windows")) {
            return CudaNativeLibrary.class
                    .getResource("/META-INF/native/windows-x86_64/ta4j-cuda-accelerator.dll") != null;
        }
        if (operatingSystem.contains("linux")) {
            return CudaNativeLibrary.class
                    .getResource("/META-INF/native/linux-x86_64/libta4j-cuda-accelerator.so") != null;
        }
        return false;
    }

    static LoadResult load() {
        String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (!(architecture.equals("amd64") || architecture.equals("x86_64"))) {
            return LoadResult.failure("CUDA provider requires x86_64, found " + architecture);
        }
        String resourceDirectory;
        String libraryName;
        if (operatingSystem.contains("windows")) {
            resourceDirectory = "/META-INF/native/windows-x86_64/";
            libraryName = "ta4j-cuda-accelerator.dll";
        } else if (operatingSystem.contains("linux")) {
            resourceDirectory = "/META-INF/native/linux-x86_64/";
            libraryName = "libta4j-cuda-accelerator.so";
        } else {
            return LoadResult.failure("CUDA provider requires Windows or Linux x86_64");
        }
        NativeLibraryLoader.LoadResult loaded = NativeLibraryLoader.load("cuda", LIBRARY_PROPERTY, resourceDirectory,
                libraryName, CudaNativeBridge.ABI_VERSION);
        return new LoadResult(loaded.loaded(), loaded.path(), loaded.detail());
    }

    record LoadResult(boolean loaded, Path path, String detail) {

        private static LoadResult failure(String detail) {
            return new LoadResult(false, null, detail);
        }
    }
}
