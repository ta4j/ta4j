/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

/**
 * Volatility behavior inside simulated forecast paths.
 *
 * @since 0.22.9
 */
public enum VolatilityUpdateMode {

    /** Keep the volatility estimate fixed throughout each simulated path. */
    CONSTANT,

    /** Update path volatility with an EWMA step after each simulated return. */
    EWMA
}
