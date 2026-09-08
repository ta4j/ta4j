/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.state;

import org.ta4j.core.Indicator;

/**
 * Indicator that returns hidden state used by forecast projection indicators.
 *
 * @param <S> forecast state type
 * @since 0.22.9
 */
public interface ForecastStateIndicator<S extends ForecastState> extends Indicator<S> {

    /**
     * Reports whether this state restarts its estimation after a head advance, so
     * retained-window values can change and downstream cached forecasts must be
     * discarded.
     *
     * <p>
     * A state that rebuilds its posterior from the retained window after bars are
     * evicted (for example, an online change-point model) returns {@code true} here
     * so dependent projection indicators drop their full caches instead of serving
     * forecasts computed from the pre-advance state.
     *
     * @return {@code true} when the state restarts after a head advance
     * @since 0.24.2
     */
    default boolean restartsAfterHeadAdvance() {
        return false;
    }
}
