/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

/**
 * Shock source used by Monte Carlo return forecasts.
 *
 * @since 0.22.9
 */
public enum ShockModel {

    /** Bootstrap raw historical returns from the lookback window. */
    HISTORICAL_BOOTSTRAP,

    /** Bootstrap standardized historical residuals and scale by current state. */
    STANDARDIZED_EMPIRICAL,

    /** Draw standard normal shocks and scale by current state. */
    NORMAL
}
