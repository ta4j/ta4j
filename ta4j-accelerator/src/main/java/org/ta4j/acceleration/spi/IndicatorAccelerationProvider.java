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
     * Predicts the fractional end-to-end improvement for this request after setup,
     * transfer, execution, validation, and materialization costs. A value of
     * {@code 0.10} means ten percent faster than the canonical CPU path.
     *
     * <p>
     * The default is zero so providers without qualified crossover evidence stay on
     * CPU in {@link org.ta4j.core.acceleration.AccelerationMode#AUTO AUTO} and
     * {@link org.ta4j.core.acceleration.AccelerationMode#HYBRID HYBRID} modes.
     * Explicit provider modes may execute a healthy provider without applying the
     * automatic crossover threshold.
     *
     * @param request request
     * @param match   adapter match
     * @param <T>     value type
     * @return finite, non-negative predicted speedup fraction
     * @since 0.23.1
     */
    default <T> double predictedSpeedup(IndicatorBatchRequest<T> request, AdapterMatch<T> match) {
        return 0d;
    }

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
