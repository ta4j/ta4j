/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;

/**
 * Utility methods for validating indicator inputs in the volume package.
 *
 * @since 0.22.2
 */
final class IndicatorSeriesUtils {

    /**
     * Creates a new IndicatorSeriesUtils instance.
     */
    private IndicatorSeriesUtils() {
    }

    /**
     * Ensures both indicators are backed by the same bar series.
     *
     * @param firstIndicator  first indicator to validate
     * @param secondIndicator second indicator to validate
     * @return shared bar series instance
     * @throws IllegalArgumentException if the indicators use different series
     * @since 0.22.2
     */
    static BarSeries requireSameSeries(Indicator<?> firstIndicator, Indicator<?> secondIndicator) {
        Objects.requireNonNull(firstIndicator, "firstIndicator must not be null");
        Objects.requireNonNull(secondIndicator, "secondIndicator must not be null");
        BarSeries firstSeries = Objects.requireNonNull(firstIndicator.getBarSeries(),
                "firstIndicator must reference a bar series");
        BarSeries secondSeries = Objects.requireNonNull(secondIndicator.getBarSeries(),
                "secondIndicator must reference a bar series");
        if (!Objects.equals(firstSeries, secondSeries)) {
            throw new IllegalArgumentException("Indicators must share the same bar series");
        }
        return firstSeries;
    }
}
