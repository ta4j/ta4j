/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import java.nio.file.Files;
import java.nio.file.Path;

import org.ta4j.core.acceleration.AccelerationRuntime.Backend;
import org.ta4j.core.acceleration.AccelerationRuntime.KernelRequest;

/**
 * Khronos OpenCL provider for {@code MONTE_CARLO_SHOCK_PATHS_V1}.
 *
 * <p>
 * The OpenCL native lane returns reduced forecast rows, not per-sample terminal
 * prices, so it cannot serve the versioned sample-output contract and always
 * declines it with a diagnostic that says so. The provider stays installed so
 * the fallback chain reports its presence, and so future operations with a
 * reduced-row contract can reuse its library presence and probe machinery. The
 * public constructor exists solely for {@link java.util.ServiceLoader} and
 * performs no probe or native loading.
 *
 * @since 0.24.2
 */
public final class OpenClAccelerationProvider extends ShockPathKernelProvider {

    static final String MAX_MEMORY_PROPERTY = "ta4j.acceleration.opencl.maxBytes";

    private static final long DEFAULT_MAX_MEMORY_BYTES = 512L * 1024L * 1024L;

    /**
     * Creates a lazy OpenCL provider.
     */
    public OpenClAccelerationProvider() {
        super(Backend.OPENCL, "opencl", MAX_MEMORY_PROPERTY, DEFAULT_MAX_MEMORY_BYTES, false, false);
    }

    @Override
    boolean libraryPresent() {
        String configured = System.getProperty(OpenClNativeLibrary.LIBRARY_PROPERTY, "").trim();
        if (!configured.isEmpty() && Files.exists(Path.of(configured))) {
            return true;
        }
        return OpenClNativeLibrary.packagedResourcePresent();
    }

    @Override
    String libraryDetail() {
        return "OpenCL library not found: set -D" + OpenClNativeLibrary.LIBRARY_PROPERTY
                + "=<path> or ship the platform classifier";
    }

    @Override
    String accuracyDetail(KernelRequest request, boolean exact) {
        return "opencl declines MONTE_CARLO_SHOCK_PATHS_V1: its native lane returns reduced forecast rows, "
                + "not per-sample terminal prices";
    }

    @Override
    SampleKernel ensureKernel() {
        throw new NativeProviderException(
                "opencl serves no MONTE_CARLO_SHOCK_PATHS_V1 sample kernel; assessment always declines");
    }
}
