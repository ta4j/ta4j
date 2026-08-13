/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.projection;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Indicator that returns numeric forecast projection summaries.
 *
 * @since 0.22.9
 */
public interface ForecastProjectionIndicator extends Indicator<Forecast> {

    /**
     * Returns the configured forecast horizon.
     *
     * @return positive horizon in bars
     * @since 0.23.1
     */
    int getHorizon();

    /**
     * Returns a point indicator for the forecast mean.
     *
     * @return mean point forecast indicator
     * @since 0.22.9
     */
    default Indicator<Num> mean() {
        return new ForwardForecastIndicator(this, Forecast::mean);
    }

    /**
     * Returns a point indicator for the forecast median.
     *
     * @return median point forecast indicator
     * @since 0.22.9
     */
    default Indicator<Num> median() {
        return new ForwardForecastIndicator(this, Forecast::median);
    }

    /**
     * Returns a point indicator for the forecast standard deviation.
     *
     * @return standard deviation point forecast indicator
     * @since 0.22.9
     */
    default Indicator<Num> standardDeviation() {
        return new ForwardForecastIndicator(this, Forecast::standardDeviation);
    }

    /**
     * Returns a point indicator for a forecast quantile.
     *
     * <p>
     * The returned indicator emits {@link NaN#NaN} when the forecast is unstable or
     * when the valid probability was not configured on the forecast source.
     *
     * @param probability quantile probability in {@code [0, 1]}
     * @return quantile point forecast indicator
     * @since 0.22.9
     */
    default Indicator<Num> quantile(double probability) {
        validateProbability(probability);
        return new ForwardForecastIndicator(this, forecast -> {
            if (!forecast.hasQuantile(probability)) {
                return NaN.NaN;
            }
            return forecast.quantile(probability);
        });
    }

    private static void validateProbability(double probability) {
        if (Double.isNaN(probability) || probability < 0d || probability > 1d) {
            throw new IllegalArgumentException("probability must be in [0, 1]");
        }
    }
}
