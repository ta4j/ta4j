/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import org.ta4j.core.Indicator;

/**
 * Indicator that returns forecast distributions.
 *
 * @param <T> forecast value type
 * @since 0.22.9
 */
public interface ForecastDistributionIndicator<T> extends Indicator<ForecastDistribution<T>> {
}
