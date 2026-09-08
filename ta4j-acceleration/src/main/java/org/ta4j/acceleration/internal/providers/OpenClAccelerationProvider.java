/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

import java.nio.file.Files;
import java.nio.file.Path;

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
    private final OpenClNativeBridge nativeBridge = new JniOpenClNativeBridge();

    public OpenClAccelerationProvider() {
        super(Backend.OPENCL, "opencl", MAX_MEMORY_PROPERTY, DEFAULT_MAX_MEMORY_BYTES, false, true);
    }

    @Override
    boolean libraryPresent() {
        String configured = System.getProperty(OpenClNativeLibrary.LIBRARY_PROPERTY, "").trim();
        return (!configured.isEmpty() && Files.exists(Path.of(configured)))
                || OpenClNativeLibrary.packagedResourcePresent();
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
            OpenClNativeLibrary.LoadResult load = OpenClNativeLibrary.load();
            if (!load.loaded()) {
                throw new NativeProviderException("opencl", load.detail());
            }
            OpenClProbeResult probe = nativeBridge.probe();
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
