/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.ta4j.core.acceleration.AccelerationRuntime.Backend;

/**
 * Optional Windows or Linux x86_64 CUDA provider factory.
 *
 * <p>
 * Native loading remains lazy and occurs only when an eligible CUDA operation
 * is probed. The default JVM-only artifact therefore has no CUDA runtime
 * dependency.
 *
 * @since 0.23.1
 */
final class CudaAccelerationProviderFactory {

    /**
     * Absolute developer-library override, evaluated before classifier resources.
     */
    static final String LIBRARY_PROPERTY = CudaNativeLibrary.LIBRARY_PROPERTY;

    private static final Map<String, ForecastAccelerationProvider> PROBE_CACHE = new ConcurrentHashMap<>();

    private final Supplier<CudaNativeLibrary.LoadResult> libraryLoader;
    private final CudaNativeBridge nativeBridge;
    private final boolean cacheProbe;

    /**
     * Creates the production CUDA provider factory.
     *
     * @since 0.23.1
     */
    CudaAccelerationProviderFactory() {
        this(CudaNativeLibrary::load, new JniCudaNativeBridge(), true);
    }

    CudaAccelerationProviderFactory(Supplier<CudaNativeLibrary.LoadResult> libraryLoader, CudaNativeBridge nativeBridge,
            boolean cacheProbe) {
        this.libraryLoader = libraryLoader;
        this.nativeBridge = nativeBridge;
        this.cacheProbe = cacheProbe;
    }

    String providerId() {
        return "cuda";
    }

    ForecastAccelerationProvider probe() {
        if (!cacheProbe) {
            return createProvider();
        }
        String configuredLibrary = System.getProperty(LIBRARY_PROPERTY, "<classifier>");
        return PROBE_CACHE.computeIfAbsent(configuredLibrary, ignored -> createProvider());
    }

    static void clearProbeCacheForTests() {
        PROBE_CACHE.clear();
    }

    private ForecastAccelerationProvider createProvider() {
        CudaNativeLibrary.LoadResult load = libraryLoader.get();
        if (!load.loaded()) {
            return unavailable(load.detail());
        }
        CudaProbeResult probe = nativeBridge.probe();
        if (!probe.available()) {
            return unavailable("CUDA native self-test failed: " + probe.detail());
        }
        if (probe.computeMajor() != 12 || probe.computeMinor() != 0) {
            return unavailable("CUDA classifier is qualified only for compute capability 12.0; found %d.%d"
                    .formatted(probe.computeMajor(), probe.computeMinor()));
        }
        Capability capability = new Capability(providerId(), Backend.CUDA, true, true, probe.deviceName(), "");
        return new CudaAccelerationProvider(capability, nativeBridge, probe);
    }

    private ForecastAccelerationProvider unavailable(String reason) {
        Capability capability = new Capability(providerId(), Backend.CUDA, false, false, "", reason);
        return new UnavailableForecastProvider(capability);
    }
}
