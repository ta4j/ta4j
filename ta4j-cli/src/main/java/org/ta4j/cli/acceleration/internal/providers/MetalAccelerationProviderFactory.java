/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.ta4j.core.acceleration.AccelerationRuntime.Backend;

final class MetalAccelerationProviderFactory {

    static final String LIBRARY_PROPERTY = MetalNativeLibrary.LIBRARY_PROPERTY;

    private static final Map<String, ForecastAccelerationProvider> PROBE_CACHE = new ConcurrentHashMap<>();

    private final Supplier<MetalNativeLibrary.LoadResult> libraryLoader;
    private final MetalNativeBridge nativeBridge;
    private final boolean cacheProbe;

    MetalAccelerationProviderFactory() {
        this(MetalNativeLibrary::load, new JniMetalNativeBridge(), true);
    }

    MetalAccelerationProviderFactory(Supplier<MetalNativeLibrary.LoadResult> libraryLoader,
            MetalNativeBridge nativeBridge, boolean cacheProbe) {
        this.libraryLoader = libraryLoader;
        this.nativeBridge = nativeBridge;
        this.cacheProbe = cacheProbe;
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
        MetalNativeLibrary.LoadResult load = libraryLoader.get();
        if (!load.loaded()) {
            return unavailable(load.detail());
        }
        MetalProbeResult probe;
        try {
            probe = nativeBridge.probe();
        } catch (LinkageError | RuntimeException exception) {
            return unavailable("Metal native self-test failed: " + exception.getClass().getSimpleName() + ": "
                    + (exception.getMessage() == null ? "no detail" : exception.getMessage()));
        }
        if (!probe.available()) {
            return unavailable("Metal native self-test failed: " + probe.detail());
        }
        if (!Boolean.getBoolean(MetalAccelerationProvider.APPROXIMATE_PROPERTY)) {
            Capability capability = new Capability("metal", Backend.METAL, false, true, probe.deviceName(),
                    "approximate fp32 results require opt-in via -D" + MetalAccelerationProvider.APPROXIMATE_PROPERTY
                            + "=true");
            return new MetalAccelerationProvider(capability, nativeBridge, probe, false);
        }
        Capability capability = new Capability("metal", Backend.METAL, true, true, probe.deviceName(), "");
        return new MetalAccelerationProvider(capability, nativeBridge, probe, true);
    }

    private ForecastAccelerationProvider unavailable(String reason) {
        Capability capability = new Capability("metal", Backend.METAL, false, false, "", reason);
        return new UnavailableForecastProvider(capability);
    }
}
