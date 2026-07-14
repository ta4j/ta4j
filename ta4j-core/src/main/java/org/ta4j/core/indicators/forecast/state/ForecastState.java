/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.state;

import org.ta4j.core.num.Num;

/**
 * Common state summary consumed by forecast projection indicators.
 *
 * <p>
 * The common fields describe an estimator's best return-domain summary at one
 * index. Concrete state records may expose additional model-specific values,
 * but generic consumers can use this contract without depending on the
 * estimator implementation. A stable state has at least one observation and
 * finite common values; an unstable state has no observations and uses
 * {@code NaN.NaN} for values that are not yet defined.
 *
 * @since 0.23.1
 */
public interface ForecastState {

    /**
     * Returns the source index represented by this state.
     *
     * @return source index
     * @since 0.23.1
     */
    int index();

    /**
     * Returns the number of observations represented by this state.
     *
     * @return observation count, or zero when unstable
     * @since 0.23.1
     */
    int observationCount();

    /**
     * Returns whether the state is ready for forecast use.
     *
     * @return {@code true} when stable
     * @since 0.23.1
     */
    boolean isStable();

    /**
     * Returns the estimator's current mean-return summary.
     *
     * @return mean return, or {@code NaN.NaN} when unstable
     * @since 0.23.1
     */
    Num mean();

    /**
     * Returns the estimator's drift assumption for forward projections.
     *
     * @return drift, or {@code NaN.NaN} when unstable
     * @since 0.23.1
     */
    Num drift();

    /**
     * Returns the estimator's current return-variance summary.
     *
     * @return non-negative variance, or {@code NaN.NaN} when unstable
     * @since 0.23.1
     */
    Num variance();

    /**
     * Returns the estimator's current return-volatility summary.
     *
     * @return non-negative volatility, or {@code NaN.NaN} when unstable
     * @since 0.23.1
     */
    Num volatility();
}
