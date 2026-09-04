/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import java.nio.file.Files;
import java.nio.file.Path;

import org.ta4j.core.acceleration.AccelerationRuntime.Backend;
import org.ta4j.core.acceleration.AccelerationRuntime.KernelRequest;

/**
 * NVIDIA CUDA provider for {@code MONTE_CARLO_SHOCK_PATHS_V1}.
 *
 * <p>
 * The CUDA native lane returns reduced forecast rows, not per-sample terminal
 * prices, so it cannot serve the versioned sample-output contract and always
 * declines it with a diagnostic that says so. The provider stays installed so
 * the fallback chain reports its presence, and so future operations with a
 * reduced-row contract can reuse its library presence and probe machinery. The
 * public constructor exists solely for {@link java.util.ServiceLoader} and
 * performs no probe or native loading.
 *
 * @since 0.24.2
 */
public final class CudaAccelerationProvider extends ShockPathKernelProvider {

    static final String MAX_MEMORY_PROPERTY = "ta4j.acceleration.cuda.maxBytes";

    private static final long DEFAULT_MAX_MEMORY_BYTES = 512L * 1024L * 1024L;

    /**
     * Creates a lazy CUDA provider.
     */
    public CudaAccelerationProvider() {
        super(Backend.CUDA, "cuda", MAX_MEMORY_PROPERTY, DEFAULT_MAX_MEMORY_BYTES, false, false);
    }

    @Override
    boolean libraryPresent() {
        String configured = System.getProperty(CudaNativeLibrary.LIBRARY_PROPERTY, "").trim();
        if (!configured.isEmpty() && Files.exists(Path.of(configured))) {
            return true;
        }
        return CudaNativeLibrary.packagedResourcePresent();
    }

    @Override
    String libraryDetail() {
        return "CUDA library not found: set -D" + CudaNativeLibrary.LIBRARY_PROPERTY
                + "=<path> or ship the platform classifier";
    }

    @Override
    String accuracyDetail(KernelRequest request, boolean exact) {
        return "cuda declines MONTE_CARLO_SHOCK_PATHS_V1: its native lane returns reduced forecast rows, "
                + "not per-sample terminal prices";
    }

    @Override
    SampleKernel ensureKernel() {
        throw new NativeProviderException(
                "cuda serves no MONTE_CARLO_SHOCK_PATHS_V1 sample kernel; assessment always declines");
    }
}
