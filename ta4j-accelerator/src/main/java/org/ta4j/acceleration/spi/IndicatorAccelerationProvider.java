/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.spi;

import java.util.Optional;

import org.ta4j.core.acceleration.IndicatorBatchRequest;
import org.ta4j.core.acceleration.IndicatorBatchResult;

/**
 * Optional compute provider for adapter-owned indicator batch operations.
 *
 * @since 0.23.1
 */
public interface IndicatorAccelerationProvider {

    /**
     * @return immutable capability report
     * @since 0.23.1
     */
    ProviderCapability capability();

    /**
     * Attempts provider execution for a supported adapter operation.
     *
     * <p>
     * Providers may return {@link Optional#empty()} when an operation is known but
     * not implemented in the current platform artifact.
     *
     * @param request request
     * @param match   adapter match
     * @param <T>     value type
     * @return provider result, or empty when unavailable/not implemented
     * @since 0.23.1
     */
    default <T> Optional<IndicatorBatchResult<T>> evaluate(IndicatorBatchRequest<T> request, AdapterMatch<T> match) {
        return Optional.empty();
    }
}
