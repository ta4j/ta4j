/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import java.nio.file.Path;
import java.util.Locale;

final class OpenClNativeLibrary {

    static final String LIBRARY_PROPERTY = "ta4j.acceleration.opencl.library";

    private OpenClNativeLibrary() {
    }

    static LoadResult load() {
        String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String resourceDirectory;
        if (architecture.equals("amd64") || architecture.equals("x86_64")) {
            resourceDirectory = "/META-INF/native/linux-x86_64/";
        } else if (architecture.equals("aarch64") || architecture.equals("arm64")) {
            resourceDirectory = "/META-INF/native/linux-aarch64/";
        } else {
            return LoadResult.failure("OpenCL provider requires Linux x86_64 or aarch64, found " + architecture);
        }
        if (!operatingSystem.contains("linux")) {
            return LoadResult.failure("OpenCL provider requires Linux");
        }
        NativeLibraryLoader.LoadResult loaded = NativeLibraryLoader.load("opencl", LIBRARY_PROPERTY, resourceDirectory,
                "libta4j-opencl-accelerator.so", OpenClNativeBridge.ABI_VERSION);
        return new LoadResult(loaded.loaded(), loaded.path(), loaded.detail());
    }

    record LoadResult(boolean loaded, Path path, String detail) {

        private static LoadResult failure(String detail) {
            return new LoadResult(false, null, detail);
        }
    }
}
