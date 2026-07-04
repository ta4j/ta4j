/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import org.ta4j.core.num.Num;

/**
 * Reduces a numeric forecast distribution to one point forecast.
 *
 * @since 0.22.9
 */
@FunctionalInterface
public interface ForecastReducer {

    /**
     * Reduces a forecast distribution.
     *
     * @param distribution forecast distribution
     * @return reduced point forecast
     * @since 0.22.9
     */
    Num reduce(ForecastDistribution<Num> distribution);
}
