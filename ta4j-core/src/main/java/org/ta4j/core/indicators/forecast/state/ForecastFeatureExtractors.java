/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.state;

import java.util.Objects;

import org.ta4j.core.num.Num;

/**
 * Standard feature extractors for forecast-state comparison and modeling.
 *
 * <p>
 * Each factory returns a stateless extractor and every extraction returns a
 * defensive array. Built-in extractors require stable states whose selected
 * values can be represented as finite primitive doubles.
 *
 * @since 0.22.9
 */
public final class ForecastFeatureExtractors {

    private ForecastFeatureExtractors() {
    }

    /**
     * Extracts mean, drift, variance, and volatility, in that order.
     *
     * @param <S> forecast state type
     * @return four-feature extractor
     * @since 0.22.9
     */
    public static <S extends ForecastState> ForecastFeatureExtractor<S> meanDriftVarianceVolatility() {
        return state -> {
            S value = requireStable(state);
            return new double[] { finiteDouble(value.mean(), "mean"), finiteDouble(value.drift(), "drift"),
                    finiteDouble(value.variance(), "variance"), finiteDouble(value.volatility(), "volatility") };
        };
    }

    /**
     * Extracts drift and volatility, in that order.
     *
     * @param <S> forecast state type
     * @return two-feature extractor
     * @since 0.22.9
     */
    public static <S extends ForecastState> ForecastFeatureExtractor<S> driftVolatility() {
        return state -> {
            S value = requireStable(state);
            return new double[] { finiteDouble(value.drift(), "drift"),
                    finiteDouble(value.volatility(), "volatility") };
        };
    }

    /**
     * Extracts the default return-state features: mean, drift, and volatility.
     *
     * @return return-state feature extractor
     * @since 0.22.9
     */
    public static ForecastFeatureExtractor<ReturnForecastState> returnStateDefaults() {
        return state -> {
            ReturnForecastState value = requireStable(state);
            return new double[] { finiteDouble(value.mean(), "mean"), finiteDouble(value.drift(), "drift"),
                    finiteDouble(value.volatility(), "volatility") };
        };
    }

    private static <S extends ForecastState> S requireStable(S state) {
        S value = Objects.requireNonNull(state, "state must not be null");
        if (!value.isStable()) {
            throw new IllegalArgumentException("state must be stable");
        }
        return value;
    }

    private static double finiteDouble(Num value, String fieldName) {
        Num number = Objects.requireNonNull(value, fieldName + " must not be null");
        double primitive = number.doubleValue();
        if (!Double.isFinite(primitive)) {
            throw new IllegalArgumentException(fieldName + " must be finite as a double");
        }
        return primitive;
    }
}
