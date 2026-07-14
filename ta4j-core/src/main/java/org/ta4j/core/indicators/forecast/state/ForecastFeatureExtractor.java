/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.state;

/**
 * Extracts primitive model features from a stable forecast state.
 *
 * <p>
 * Feature extraction is the explicit boundary where ta4j {@code Num} values
 * become primitive doubles for distance, regression, or linear-algebra APIs.
 * Implementations should return a new array for every invocation. Consumers
 * must treat non-finite or inconsistent feature vectors as unusable.
 *
 * @param <S> forecast state type
 * @since 0.23.1
 */
@FunctionalInterface
public interface ForecastFeatureExtractor<S extends ForecastState> {

    /**
     * Returns the features representing {@code state}.
     *
     * @param state stable forecast state
     * @return newly allocated feature vector
     * @since 0.23.1
     */
    double[] features(S state);
}
