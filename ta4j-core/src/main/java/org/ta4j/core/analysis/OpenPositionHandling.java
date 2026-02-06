/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

/**
 * Controls how open positions are treated in analysis calculations.
 *
 * @since 0.22.2
 */
public enum OpenPositionHandling {

    /**
     * Include open positions and mark them to market at the final index.
     *
     * @since 0.22.2
     */
    MARK_TO_MARKET,

    /**
     * Ignore open positions.
     *
     * @since 0.22.2
     */
    IGNORE
}
