/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.state;

import java.util.Objects;

import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.num.Num;

/**
 * Standard return forecast state backed by validated {@link ReturnMoments}.
 *
 * @param moments common return moments
 * @since 0.22.9
 */
public record ReturnForecastState(ReturnMoments moments) implements ReturnMomentState {

    /** Creates a return forecast state. */
    public ReturnForecastState {
        moments = Objects.requireNonNull(moments, "moments must not be null");
    }

    /**
     * Creates stable return state without accepting redundant volatility.
     *
     * @return stable return state
     * @since 0.23.1
     */
    public static ReturnForecastState stable(int index, int observationCount, ReturnRepresentation representation,
            Num mean, Num drift, Num variance) {
        return new ReturnForecastState(
                ReturnMoments.stable(index, observationCount, representation, mean, drift, variance));
    }

    /**
     * Creates unstable return state.
     *
     * @return unstable return state
     * @since 0.23.1
     */
    public static ReturnForecastState unstable(int index, int observationCount, ReturnRepresentation representation) {
        return new ReturnForecastState(ReturnMoments.unstable(index, observationCount, representation));
    }
}
