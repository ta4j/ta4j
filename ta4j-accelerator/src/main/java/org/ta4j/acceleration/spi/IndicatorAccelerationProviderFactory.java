/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.spi;

import java.util.Collection;

import org.ta4j.core.acceleration.AccelerationMode;

/**
 * Lazy provider factory discovered through {@link java.util.ServiceLoader}.
 *
 * <p>
 * Implementations must not load native libraries in constructors. Probing
 * should happen only after an eligible adapter operation and provider mode are
 * known.
 *
 * @since 0.23.1
 */
public interface IndicatorAccelerationProviderFactory {

    /**
     * @return stable provider identifier
     * @since 0.23.1
     */
    String providerId();

    /**
     * @return backend mode provided by this factory
     * @since 0.23.1
     */
    AccelerationMode mode();

    /**
     * Probes the provider for relevant operations.
     *
     * @param operationIds operation IDs needed by the current request
     * @return provider
     * @since 0.23.1
     */
    IndicatorAccelerationProvider probe(Collection<String> operationIds);
}
