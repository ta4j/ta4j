/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.state;

import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.ReturnIndicator;

/**
 * Provides typed hidden state derived from a return stream.
 *
 * <p>
 * Implement this interface when a state estimator is driven by a
 * {@link ReturnIndicator}. Return-based projections can then infer the source
 * stream and its {@link ReturnRepresentation} without requiring callers to pass
 * the same indicator twice.
 *
 * @param <S> return-derived forecast state type
 * @since 0.22.9
 */
public interface ReturnForecastStateIndicator<S extends ReturnMomentState> extends ForecastStateIndicator<S> {

    /**
     * Returns the source return indicator used to build this state.
     *
     * @return return indicator
     * @since 0.22.9
     */
    ReturnIndicator getReturnIndicator();

    /**
     * Returns the representation used by the source return stream.
     *
     * @return return representation
     * @since 0.22.9
     */
    default ReturnRepresentation getReturnRepresentation() {
        return getReturnIndicator().getReturnRepresentation();
    }
}
