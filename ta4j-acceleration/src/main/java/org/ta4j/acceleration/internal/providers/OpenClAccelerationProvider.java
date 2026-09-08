/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

import org.ta4j.core.acceleration.AccelerationRuntime.Backend;

/**
 * Khronos OpenCL provider for {@code MONTE_CARLO_SHOCK_PATHS_V1}.
 *
 * <p>
 * The native lane returns row-major per-sample terminal prices and is loaded
 * lazily after assessment selects this provider.
 *
 * @since 0.25.1
 */
public final class OpenClAccelerationProvider extends ShockPathKernelProvider {

    static final String MAX_MEMORY_PROPERTY = "ta4j.acceleration.opencl.maxBytes";
    private static final long DEFAULT_MAX_MEMORY_BYTES = 512L * 1024L * 1024L;
    private final Object kernelLock = new Object();
    private volatile SampleKernel kernel;
    private final Supplier<OpenClNativeLibrary.LoadResult> libraryLoader;
    private final OpenClNativeBridge nativeBridge;

    public OpenClAccelerationProvider() {
        this(OpenClNativeLibrary::load, new JniOpenClNativeBridge());
    }

    OpenClAccelerationProvider(Supplier<OpenClNativeLibrary.LoadResult> libraryLoader,
            OpenClNativeBridge nativeBridge) {
        super(Backend.OPENCL, "opencl", MAX_MEMORY_PROPERTY, DEFAULT_MAX_MEMORY_BYTES, false, true);
        this.libraryLoader = Objects.requireNonNull(libraryLoader, "libraryLoader must not be null");
        this.nativeBridge = Objects.requireNonNull(nativeBridge, "nativeBridge must not be null");
    }

    @Override
    boolean libraryPresent() {
        String configured = System.getProperty(OpenClNativeLibrary.LIBRARY_PROPERTY, "").trim();
        if (configured.isEmpty()) {
            return OpenClNativeLibrary.packagedResourcePresent();
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
        return "OpenCL library not found: set -D" + OpenClNativeLibrary.LIBRARY_PROPERTY
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
            OpenClNativeLibrary.LoadResult load = libraryLoader.get();
            if (!load.loaded()) {
                throw new NativeProviderException("opencl", load.detail());
            }
            OpenClProbeResult probe;
            try {
                probe = nativeBridge.probe();
            } catch (LinkageError | RuntimeException exception) {
                throw new NativeProviderException("opencl", exception);
            }
            if (!probe.available()) {
                throw new NativeProviderException("opencl", probe.detail());
            }
            recordProbe(probe.deviceName(), probe.freeMemoryBytes());
            installed = request -> {
                OpenClEvaluationResult result = nativeBridge.evaluate(request);
                double[] values = result.terminalPrices();
                float[] terminalPrices = new float[values.length];
                for (int i = 0; i < values.length; i++) {
                    terminalPrices[i] = (float) values[i];
                }
                return new SampleKernel.SampleResult(terminalPrices, result.totalMicros());
            };
            kernel = installed;
            return installed;
        }
    }
}
