/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.acceleration;

import java.util.Objects;

import org.ta4j.core.Indicator;

/**
 * Immutable indicator batch request.
 *
 * @param indicator     indicator to evaluate
 * @param fromInclusive first bar index
 * @param toInclusive   last bar index
 * @param config        acceleration configuration
 * @param <T>           indicator value type
 * @since 0.23.1
 */
public record IndicatorBatchRequest<T>(Indicator<T> indicator, int fromInclusive, int toInclusive,
        AccelerationConfig config) {

    public IndicatorBatchRequest {
        Objects.requireNonNull(indicator, "indicator must not be null");
        Objects.requireNonNull(config, "config must not be null");
        if (fromInclusive > toInclusive) {
            throw new IllegalArgumentException("fromInclusive must be <= toInclusive");
        }
    }
}
