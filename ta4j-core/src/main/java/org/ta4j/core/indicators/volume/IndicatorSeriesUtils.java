/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;

final class IndicatorSeriesUtils {

    private IndicatorSeriesUtils() {
    }

    static BarSeries requireSameSeries(Indicator<?> firstIndicator, Indicator<?> secondIndicator) {
        Objects.requireNonNull(firstIndicator, "firstIndicator must not be null");
        Objects.requireNonNull(secondIndicator, "secondIndicator must not be null");
        BarSeries firstSeries = firstIndicator.getBarSeries();
        if (!Objects.equals(firstSeries, secondIndicator.getBarSeries())) {
            throw new IllegalArgumentException("Indicators must share the same bar series");
        }
        return firstSeries;
    }
}
