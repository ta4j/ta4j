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

    /**
     * @return validated return moments
     * @since 0.23.1
     */
    ReturnMoments moments();

    /**
     * {@inheritDoc}
     *
     * @since 0.23.1
     */
    @Override
    default int index() {
        return moments().index();
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.23.1
     */
    @Override
    default boolean isStable() {
        return moments().isStable();
    }

    /**
     * @return observations incorporated by the estimator
     * @since 0.23.1
     */
    default int observationCount() {
        return moments().observationCount();
    }

    /**
     * @return return representation
     * @since 0.23.1
     */
    default ReturnRepresentation representation() {
        return moments().representation();
    }

    /**
     * @return mean return
     * @since 0.23.1
     */
    default Num mean() {
        return moments().mean();
    }

    /**
     * @return forward drift assumption
     * @since 0.23.1
     */
    default Num drift() {
        return moments().drift();
    }

    /**
     * @return canonical return variance
     * @since 0.23.1
     */
    default Num variance() {
        return moments().variance();
    }

    /**
     * @return volatility derived from variance
     * @since 0.23.1
     */
    default Num volatility() {
        return moments().volatility();
    }
}
