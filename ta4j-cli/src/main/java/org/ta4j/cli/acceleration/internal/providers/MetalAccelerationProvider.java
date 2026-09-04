/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

import org.ta4j.core.acceleration.AccelerationRuntime.Backend;

/**
 * Apple Metal sample-output provider for {@code MONTE_CARLO_SHOCK_PATHS_V1}.
 *
 * <p>
 * The FP32 Metal kernel cannot reproduce float64 scalar results bit for bit, so
 * exact requests stay scalar with a diagnostic that says so. Approximate
 * requests, which the core planner emits only when
 * {@code -Dta4j.acceleration.approximateTolerance=<value>} is set, engage only
 * on qualified device families above the crossover floor. The public constructor
 * exists solely for {@link java.util.ServiceLoader} and performs no probe or
 * native loading.
 *
 * @since 0.24.2
 */
public final class MetalAccelerationProvider extends ShockPathKernelProvider {

    static final String MAX_MEMORY_PROPERTY = "ta4j.acceleration.metal.maxBytes";

    private static final long DEFAULT_MAX_MEMORY_BYTES = 512L * 1024L * 1024L;

    private final Supplier<MetalNativeLibrary.LoadResult> libraryLoader;
    private final MetalNativeBridge nativeBridge;
    private final Object kernelLock = new Object();
    private volatile SampleKernel kernel;

    /**
     * Creates a lazy Metal provider.
     */
    public MetalAccelerationProvider() {
        this(MetalNativeLibrary::load, new JniMetalNativeBridge());
    }

    MetalAccelerationProvider(Supplier<MetalNativeLibrary.LoadResult> libraryLoader, MetalNativeBridge nativeBridge) {
        super(Backend.METAL, "metal", MAX_MEMORY_PROPERTY, DEFAULT_MAX_MEMORY_BYTES, false, true);
        this.libraryLoader = Objects.requireNonNull(libraryLoader, "libraryLoader must not be null");
        this.nativeBridge = Objects.requireNonNull(nativeBridge, "nativeBridge must not be null");
    }

    @Override
    boolean libraryPresent() {
        String configured = System.getProperty(MetalNativeLibrary.LIBRARY_PROPERTY, "").trim();
        if (!configured.isEmpty() && Files.exists(Path.of(configured))) {
            return true;
        }
        return MetalNativeLibrary.packagedResourcePresent();
    }

    @Override
    String libraryDetail() {
        return "Metal library not found: set -D" + MetalNativeLibrary.LIBRARY_PROPERTY
                + "=<path> or ship the macos-aarch64 classifier";
    }

    @Override
    String deviceFamily() {
        String configured = System.getProperty(ShockPathQualification.familyProperty(Backend.METAL), "")
                .trim()
                .toLowerCase(Locale.ROOT);
        if (!configured.isEmpty()) {
            return configured;
        }
        String probed = probedDevice();
        if (probed != null) {
            return normalizeFamily(probed);
        }
        return "generic";
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
            MetalNativeLibrary.LoadResult load = libraryLoader.get();
            if (!load.loaded()) {
                throw new NativeProviderException("metal", load.detail());
            }
            MetalProbeResult probe;
            try {
                probe = nativeBridge.probe();
            } catch (LinkageError | RuntimeException exception) {
                throw new NativeProviderException("metal", exception);
            }
            if (!probe.available()) {
                throw new NativeProviderException("metal", probe.detail());
            }
            recordProbe(probe.deviceName(), Math.max(1L, probe.recommendedMaxWorkingSetBytes() / 2L));
            installed = request -> {
                MetalEvaluationResult result = nativeBridge.evaluate(request);
                return new SampleKernel.SampleResult(result.terminalPrices(), result.totalMicros());
            };
            kernel = installed;
            return installed;
        }
    }

    private static String normalizeFamily(String deviceName) {
        String normalized = deviceName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (normalized.contains("m5max")) {
            return "m5max";
        }
        return normalized;
    }
}
