/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.state;

/**
 * Minimal lifecycle contract for hidden forecast state.
 *
 * <p>
 * Model-specific capabilities belong on specialized state interfaces rather
 * than forcing unrelated estimators to manufacture return moments.
 *
 * @since 0.23.1
 */
public interface ForecastState {

    /**
     * @return source index represented by this state
     * @since 0.23.1
     */
    int index();

    /**
     * @return whether the state is ready for forecast use
     * @since 0.23.1
     */
    boolean isStable();
}
