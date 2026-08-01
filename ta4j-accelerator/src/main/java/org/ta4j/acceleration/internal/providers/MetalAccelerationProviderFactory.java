/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import org.ta4j.acceleration.internal.adapters.ForecastBatchAdapter;
import org.ta4j.acceleration.spi.IndicatorAccelerationProvider;
import org.ta4j.acceleration.spi.IndicatorAccelerationProviderFactory;
import org.ta4j.acceleration.spi.ProviderCapability;
import org.ta4j.core.acceleration.AccelerationMode;

/**
 * Lazy Metal provider probe.
 */
public final class MetalAccelerationProviderFactory implements IndicatorAccelerationProviderFactory {

    /** Optional native library path property. */
    public static final String LIBRARY_PROPERTY = "ta4j.acceleration.metal.library";

    @Override
    public String providerId() {
        return "metal";
    }

    @Override
    public AccelerationMode mode() {
        return AccelerationMode.METAL;
    }

    @Override
    public IndicatorAccelerationProvider probe(Collection<String> operationIds) {
        String osName = System.getProperty("os.name", "").toLowerCase();
        String osArch = System.getProperty("os.arch", "").toLowerCase();
        if (!osName.contains("mac") || !(osArch.contains("aarch64") || osArch.contains("arm64"))) {
            return unavailable("Metal provider requires macOS arm64");
        }
        if (!operationIds.contains(ForecastBatchAdapter.OPERATION_ID)) {
            return unavailable("Metal provider has no matching requested operation");
        }
        String configuredLibrary = System.getProperty(LIBRARY_PROPERTY, "");
        if (configuredLibrary.isBlank()) {
            return unavailable("Metal native library path is not configured; set " + LIBRARY_PROPERTY);
        }
        Path library;
        try {
            library = Path.of(configuredLibrary);
        } catch (RuntimeException exception) {
            return unavailable("Metal native library path is invalid: " + exception.getMessage());
        }
        if (!library.isAbsolute()) {
            return unavailable("Metal native library path must be absolute: " + library);
        }
        if (!Files.isRegularFile(library)) {
            return unavailable("Metal native library not present at " + library);
        }
        try {
            System.load(library.normalize().toString());
            if (!nativeSelfTest()) {
                return unavailable("Metal native self-test failed for " + library);
            }
            String deviceName = nativeDeviceName();
            ProviderCapability capability = new ProviderCapability(providerId(), mode(), false, true, "",
                    List.of(ForecastBatchAdapter.OPERATION_ID),
                    "NOT_IMPLEMENTED: Metal probe/self-test passed on %s, but forecast device execution is not enabled"
                            .formatted(deviceName.isBlank() ? "unnamed device" : deviceName));
            return new CapabilityOnlyProvider(capability);
        } catch (LinkageError | RuntimeException exception) {
            return unavailable("Metal native probe failed: " + exception.getMessage());
        }
    }

    private IndicatorAccelerationProvider unavailable(String reason) {
        ProviderCapability capability = new ProviderCapability(providerId(), mode(), false, false, "",
                List.of(ForecastBatchAdapter.OPERATION_ID), reason);
        return new CapabilityOnlyProvider(capability);
    }

    private static native String nativeDeviceName();

    private static native boolean nativeSelfTest();
}
