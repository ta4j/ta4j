/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.ta4j.acceleration.internal.adapters.ForecastBatchAdapter;
import org.ta4j.acceleration.spi.IndicatorAccelerationProvider;
import org.ta4j.acceleration.spi.IndicatorAccelerationProviderFactory;
import org.ta4j.acceleration.spi.ProviderCapability;
import org.ta4j.core.acceleration.AccelerationMode;

/**
 * Optional Windows x86_64 CUDA provider factory.
 *
 * <p>
 * Native loading remains lazy and occurs only when an eligible CUDA operation
 * is probed. The default JVM-only artifact therefore has no CUDA runtime
 * dependency.
 *
 * @since 0.23.1
 */
public final class CudaAccelerationProviderFactory implements IndicatorAccelerationProviderFactory {

    /**
     * Absolute developer-library override, evaluated before classifier resources.
     */
    public static final String LIBRARY_PROPERTY = CudaNativeLibrary.LIBRARY_PROPERTY;

    private static final Map<String, IndicatorAccelerationProvider> PROBE_CACHE = new ConcurrentHashMap<>();

    private final Supplier<CudaNativeLibrary.LoadResult> libraryLoader;
    private final CudaNativeBridge nativeBridge;
    private final boolean cacheProbe;

    /**
     * Creates the production CUDA provider factory.
     *
     * @since 0.23.1
     */
    public CudaAccelerationProviderFactory() {
        this(CudaNativeLibrary::load, new JniCudaNativeBridge(), true);
    }

    CudaAccelerationProviderFactory(Supplier<CudaNativeLibrary.LoadResult> libraryLoader, CudaNativeBridge nativeBridge,
            boolean cacheProbe) {
        this.libraryLoader = libraryLoader;
        this.nativeBridge = nativeBridge;
        this.cacheProbe = cacheProbe;
    }

    @Override
    public String providerId() {
        return "cuda";
    }

    @Override
    public AccelerationMode mode() {
        return AccelerationMode.CUDA;
    }

    @Override
    public IndicatorAccelerationProvider probe(Collection<String> operationIds) {
        if (!operationIds.contains(ForecastBatchAdapter.OPERATION_ID)) {
            return unavailable("CUDA provider was probed without a supported operation");
        }
        if (!cacheProbe) {
            return createProvider();
        }
        String configuredLibrary = System.getProperty(LIBRARY_PROPERTY, "<classifier>");
        return PROBE_CACHE.computeIfAbsent(configuredLibrary, ignored -> createProvider());
    }

    static void clearProbeCacheForTests() {
        PROBE_CACHE.clear();
    }

    private IndicatorAccelerationProvider createProvider() {
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
        ProviderCapability capability = new ProviderCapability(providerId(), mode(), true, true, probe.deviceName(),
                List.of(ForecastBatchAdapter.OPERATION_ID), "");
        return new CudaAccelerationProvider(capability, nativeBridge, probe);
    }

    private IndicatorAccelerationProvider unavailable(String reason) {
        ProviderCapability capability = new ProviderCapability(providerId(), mode(), false, false, "",
                List.of(ForecastBatchAdapter.OPERATION_ID), reason);
        return new CapabilityOnlyProvider(capability);
    }
}
