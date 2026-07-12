/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.state;

import java.util.Objects;

import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Rolling return state used by forecast indicators.
 *
 * @param index            state index
 * @param observationCount number of returns incorporated into the state
 * @param isStable         whether the state is stable and usable
 * @param mean             rolling mean return estimate
 * @param drift            drift used by forecasts
 * @param variance         rolling return variance estimate
 * @param volatility       square root of {@code variance}
 * @since 0.22.9
 */
public record ReturnForecastState(int index, int observationCount, boolean isStable, Num mean, Num drift, Num variance,
        Num volatility) implements ForecastState {

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
        if (isStable && observationCount == 0) {
            throw new IllegalArgumentException("stable states must include at least one observation");
        }
        if (!isStable && observationCount != 0) {
            throw new IllegalArgumentException("unstable states must have zero observations");
        }
        mean = Objects.requireNonNull(mean, "mean must not be null");
        drift = Objects.requireNonNull(drift, "drift must not be null");
        variance = Objects.requireNonNull(variance, "variance must not be null");
        volatility = Objects.requireNonNull(volatility, "volatility must not be null");
        if (isStable) {
            requireFinite(mean, "mean");
            requireFinite(drift, "drift");
            requireNonNegative(variance, "variance");
            requireNonNegative(volatility, "volatility");
        } else if (!mean.isNaN() || !drift.isNaN() || !variance.isNaN() || !volatility.isNaN()) {
            throw new IllegalArgumentException("unstable states must use NaN values");
        }
    }

    /**
     * Creates an unstable state.
     *
     * @param index state index
     * @return unstable state
     * @since 0.22.9
     */
    public static ReturnForecastState unstable(int index) {
        return new ReturnForecastState(index, 0, false, NaN.NaN, NaN.NaN, NaN.NaN, NaN.NaN);
    }

    private static void requireFinite(Num value, String fieldName) {
        if (!Num.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite for stable states");
        }
    }

    private static void requireNonNegative(Num value, String fieldName) {
        requireFinite(value, fieldName);
        if (value.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be >= 0");
        }
    }
}
