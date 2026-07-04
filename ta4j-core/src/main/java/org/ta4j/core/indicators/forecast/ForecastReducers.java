/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.Objects;

import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Common reducers for numeric forecast distributions.
 *
 * @since 0.22.9
 */
public final class ForecastReducers {

    private ForecastReducers() {
    }

    /**
     * Returns a reducer for the distribution mean.
     *
     * @return mean reducer
     * @since 0.22.9
     */
    public static ForecastReducer mean() {
        return distribution -> summaryValue(distribution, ForecastDistribution::mean);
    }

    /**
     * Returns a reducer for the distribution median.
     *
     * @return median reducer
     * @since 0.22.9
     */
    public static ForecastReducer median() {
        return distribution -> summaryValue(distribution, ForecastDistribution::median);
    }

    /**
     * Returns a reducer for the distribution standard deviation.
     *
     * @return standard deviation reducer
     * @since 0.22.9
     */
    public static ForecastReducer standardDeviation() {
        return distribution -> summaryValue(distribution, ForecastDistribution::standardDeviation);
    }

    /**
     * Returns a reducer for a named quantile.
     *
     * @param probability quantile probability in {@code [0, 1]}
     * @return quantile reducer
     * @since 0.22.9
     */
    public static ForecastReducer quantile(double probability) {
        if (Double.isNaN(probability) || probability < 0d || probability > 1d) {
            throw new IllegalArgumentException("probability must be in [0, 1]");
        }
        return distribution -> {
            ForecastDistribution<Num> value = Objects.requireNonNull(distribution, "distribution must not be null");
            if (!value.defined() || !value.quantiles().containsKey(probability)) {
                return NaN.NaN;
            }
            return value.quantile(probability);
        };
    }

    private static Num summaryValue(ForecastDistribution<Num> distribution,
            java.util.function.Function<ForecastDistribution<Num>, Num> accessor) {
        ForecastDistribution<Num> value = Objects.requireNonNull(distribution, "distribution must not be null");
        if (!value.defined()) {
            return NaN.NaN;
        }
        return accessor.apply(value);
    }
}
