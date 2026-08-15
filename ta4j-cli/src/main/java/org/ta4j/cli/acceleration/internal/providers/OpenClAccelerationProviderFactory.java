/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.ta4j.core.internal.acceleration.AccelerationRuntime.Backend;

/**
 * Optional Linux x86_64 or aarch64 OpenCL provider factory.
 *
 * <p>
 * Native loading remains lazy and occurs only when an eligible OpenCL operation
 * is probed. The default JVM-only artifact therefore has no OpenCL runtime
 * dependency.
 *
 * @since 0.23.1
 */
final class OpenClAccelerationProviderFactory {

    /**
     * Absolute developer-library override, evaluated before classifier resources.
     */
    static final String LIBRARY_PROPERTY = OpenClNativeLibrary.LIBRARY_PROPERTY;

    private static final Map<String, ForecastAccelerationProvider> PROBE_CACHE = new ConcurrentHashMap<>();

    private final Supplier<OpenClNativeLibrary.LoadResult> libraryLoader;
    private final OpenClNativeBridge nativeBridge;
    private final boolean cacheProbe;

    /**
     * Creates the production OpenCL provider factory.
     *
     * @since 0.23.1
     */
    OpenClAccelerationProviderFactory() {
        this(OpenClNativeLibrary::load, new JniOpenClNativeBridge(), true);
    }

    OpenClAccelerationProviderFactory(Supplier<OpenClNativeLibrary.LoadResult> libraryLoader,
            OpenClNativeBridge nativeBridge, boolean cacheProbe) {
        this.libraryLoader = libraryLoader;
        this.nativeBridge = nativeBridge;
        this.cacheProbe = cacheProbe;
    }

    String providerId() {
        return "opencl";
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
        OpenClNativeLibrary.LoadResult load = libraryLoader.get();
        if (!load.loaded()) {
            return unavailable(load.detail());
        }
        OpenClProbeResult probe;
        try {
            probe = nativeBridge.probe();
        } catch (LinkageError | RuntimeException exception) {
            return unavailable("OpenCL native self-test failed: " + exception.getClass().getSimpleName() + ": "
                    + (exception.getMessage() == null ? "no detail" : exception.getMessage()));
        }
        if (probe == null) {
            return unavailable("OpenCL native self-test failed: provider returned no result");
        }
        if (!probe.available()) {
            return unavailable("OpenCL native self-test failed: " + probe.detail());
        }
        Capability capability = new Capability(providerId(), Backend.OPENCL, true, true, probe.deviceName(), "");
        return new OpenClAccelerationProvider(capability, nativeBridge, probe);
    }

    private ForecastAccelerationProvider unavailable(String reason) {
        Capability capability = new Capability(providerId(), Backend.OPENCL, false, false, "", reason);
        return new UnavailableForecastProvider(capability);
    }
}
