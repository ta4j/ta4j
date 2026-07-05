/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.walkforward.PredictionSnapshot;

/**
 * Indicator that returns numeric forecast prediction summaries.
 *
 * @since 0.22.9
 */
public interface ForecastPredictionIndicator extends Indicator<PredictionSnapshot.Forecast<Num>> {

    /**
     * Returns a point indicator for the forecast mean.
     *
     * @return mean point forecast indicator
     * @since 0.22.9
     */
    default Indicator<Num> mean() {
        return new ForwardForecastIndicator(this, PredictionSnapshot.Forecast::mean);
    }

    /**
     * Returns a point indicator for the forecast median.
     *
     * @return median point forecast indicator
     * @since 0.22.9
     */
    default Indicator<Num> median() {
        return new ForwardForecastIndicator(this, PredictionSnapshot.Forecast::median);
    }

    /**
     * Returns a point indicator for the forecast standard deviation.
     *
     * @return standard deviation point forecast indicator
     * @since 0.22.9
     */
    default Indicator<Num> standardDeviation() {
        return new ForwardForecastIndicator(this, PredictionSnapshot.Forecast::standardDeviation);
    }

    /**
     * Returns a point indicator for a forecast quantile.
     *
     * @param probability quantile probability in {@code [0, 1]}
     * @return quantile point forecast indicator
     * @since 0.22.9
     */
    default Indicator<Num> quantile(double probability) {
        validateProbability(probability);
        return new ForwardForecastIndicator(this, forecast -> {
            if (!forecast.quantiles().containsKey(probability)) {
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
