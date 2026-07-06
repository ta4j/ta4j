/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import org.ta4j.core.Indicator;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.num.Num;

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

    /**
     * Converts this return projection to a price projection.
     *
     * @param priceIndicator source price indicator read at the decision index
     * @return price forecast projection indicator
     * @since 0.22.9
     */
    default ForecastProjectionIndicator toPriceForecast(Indicator<Num> priceIndicator) {
        return new LogReturnToPriceForecastIndicator(priceIndicator, this);
    }
}
