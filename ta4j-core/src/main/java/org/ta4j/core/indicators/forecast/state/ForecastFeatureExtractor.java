/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.state;

/**
 * Extracts a fixed-schema primitive vector from return-moment state.
 *
 * <p>
 * {@link #extractInto(ReturnMomentState, double[], int)} avoids allocation in
 * model loops; {@link #features(ReturnMomentState)} is the convenient operator
 * path. Implementations reject unstable state, representation mismatch, and
 * values that cannot be faithfully represented as finite primitive doubles.
 *
 * @param <S> return-moment state type
 * @since 0.23.1
 */
public interface ForecastFeatureExtractor<S extends ReturnMomentState> {

    /** @return immutable feature schema */
    ForecastFeatureSchema schema();

    /**
     * Extracts into an existing array.
     *
     * @param state  stable state matching {@link #schema()}
     * @param target destination array
     * @param offset first destination index
     */
    void extractInto(S state, double[] target, int offset);

    /**
     * Extracts a newly allocated feature vector.
     *
     * @param state stable matching state
     * @return new fixed-shape vector
     */
    default double[] features(S state) {
        double[] result = new double[schema().dimension()];
        extractInto(state, result, 0);
        return result;
    }
}
