/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.projection;

import org.ta4j.core.criteria.ReturnRepresentation;

/**
 * Projects a return-state indicator into a forward return forecast.
 *
 * @since 0.22.9
 */
public interface ReturnForecastProjectionIndicator extends ForecastProjectionIndicator {

    /**
     * Returns the representation used by the projected cumulative returns.
     *
     * @return return representation
     * @since 0.22.9
     */
    ReturnRepresentation getReturnRepresentation();
}
