/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.state;

import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.num.Num;

/**
 * Forecast state that exposes one validated return-moment component.
 *
 * <p>
 * Rich estimators can compose {@link ReturnMoments} with model-specific fields
 * without repeating lifecycle, representation, and dispersion validation.
 *
 * @since 0.23.1
 */
public interface ReturnMomentState extends ForecastState {

    /** @return validated return moments */
    ReturnMoments moments();

    @Override
    default int index() {
        return moments().index();
    }

    @Override
    default boolean isStable() {
        return moments().isStable();
    }

    /** @return observations incorporated by the estimator */
    default int observationCount() {
        return moments().observationCount();
    }

    /** @return return representation */
    default ReturnRepresentation representation() {
        return moments().representation();
    }

    /** @return mean return */
    default Num mean() {
        return moments().mean();
    }

    /** @return forward drift assumption */
    default Num drift() {
        return moments().drift();
    }

    /** @return canonical return variance */
    default Num variance() {
        return moments().variance();
    }

    /** @return volatility derived from variance */
    default Num volatility() {
        return moments().volatility();
    }
}
