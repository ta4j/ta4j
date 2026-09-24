/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import org.ta4j.core.acceleration.AccelerationRuntime.Backend;

/**
 * NVIDIA CUDA provider for {@code MONTE_CARLO_SHOCK_PATHS_V1}.
 *
 * <p>
 * The FP64 native lane returns row-major per-sample cumulative log-returns.
 * Assessment remains lazy; loading and probing happen only when the provider is
 * selected.
 *
 * @since 0.25.1
 */
public final class CudaAccelerationProvider extends ShockPathKernelProvider {

    static final String MAX_MEMORY_PROPERTY = "ta4j.acceleration.cuda.maxBytes";
    private static final long DEFAULT_MAX_MEMORY_BYTES = 512L * 1024L * 1024L;
    private final Object kernelLock = new Object();
    private volatile SampleKernel kernel;

    public CudaAccelerationProvider() {
        super(Backend.CUDA, "cuda", MAX_MEMORY_PROPERTY, DEFAULT_MAX_MEMORY_BYTES, false, true,
                ShockPathErrorBound.Precision.FP64, ShockPathQualification.QUALIFIED);
    }

    @Override
    boolean libraryPresent() {
        String configured = System.getProperty(CudaNativeLibrary.LIBRARY_PROPERTY, "").trim();
        if (configured.isEmpty()) {
            return CudaNativeLibrary.packagedResourcePresent();
        }
        try {
            Path path = Path.of(configured);
            return path.isAbsolute() && Files.isRegularFile(path);
        } catch (InvalidPathException exception) {
            return false;
        }
    }

    @Override
    String libraryDetail() {
        return "CUDA library not found: set -D" + CudaNativeLibrary.LIBRARY_PROPERTY
                + "=<path> or ship the platform classifier";
    }

    @Override
    SampleKernel ensureKernel() {
        SampleKernel installed = kernel;
        if (installed != null) {
            return installed;
        }
        synchronized (kernelLock) {
            installed = kernel;
            if (installed != null) {
                return installed;
            }
            CudaNativeLibrary.LoadResult load = CudaNativeLibrary.load();
            if (!load.loaded()) {
                throw new NativeProviderException("cuda", load.detail());
            }
            CudaNativeBridge nativeBridge = new JniCudaNativeBridge();
            CudaProbeResult probe = nativeBridge.probe();
            if (!probe.available()) {
                throw new NativeProviderException("cuda", probe.detail());
            }
            recordProbe(probe.deviceName(), probe.freeMemoryBytes());
            installed = sampleKernel(nativeBridge);
            kernel = installed;
            return installed;
        }
    }

    /**
     * Adapts the CUDA bridge; its FP64 log-returns pass through unchanged, so no
     * representable value is narrowed, flushed to zero or overflowed.
     */
    static SampleKernel sampleKernel(CudaNativeBridge nativeBridge) {
        return request -> {
            CudaEvaluationResult result = nativeBridge.evaluate(request);
            return new SampleKernel.SampleResult(result.logReturns(), result.totalMicros());
        };
    }
}
