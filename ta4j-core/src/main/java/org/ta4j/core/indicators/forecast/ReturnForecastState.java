/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.Objects;

import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Rolling return state used by forecast indicators.
 *
 * @param index            state index
 * @param observationCount number of returns incorporated into the state
 * @param defined          whether the state is defined
 * @param mean             rolling mean return estimate
 * @param drift            drift used by forecasts
 * @param variance         rolling return variance estimate
 * @param volatility       square root of {@code variance}
 * @since 0.22.9
 */
public record ReturnForecastState(int index, int observationCount, boolean defined, Num mean, Num drift, Num variance,
        Num volatility) {

    /**
     * Creates a return forecast state.
     *
     * @since 0.22.9
     */
    public ReturnForecastState {
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
        if (observationCount < 0) {
            throw new IllegalArgumentException("observationCount must be >= 0");
        }
        if (defined && observationCount == 0) {
            throw new IllegalArgumentException("defined states must include at least one observation");
        }
        if (!defined && observationCount != 0) {
            throw new IllegalArgumentException("undefined states must have zero observations");
        }
        mean = Objects.requireNonNull(mean, "mean must not be null");
        drift = Objects.requireNonNull(drift, "drift must not be null");
        variance = Objects.requireNonNull(variance, "variance must not be null");
        volatility = Objects.requireNonNull(volatility, "volatility must not be null");
    }

    /**
     * Creates an undefined state.
     *
     * @param index state index
     * @return undefined state
     * @since 0.22.9
     */
    public static ReturnForecastState undefined(int index) {
        return new ReturnForecastState(index, 0, false, NaN.NaN, NaN.NaN, NaN.NaN, NaN.NaN);
    }
}
