/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.ReturnIndicator;

/**
 * Provides hidden state derived from a return stream.
 *
 * @since 0.22.9
 */
public interface ReturnForecastStateIndicator extends ForecastStateIndicator<ReturnForecastState> {

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
