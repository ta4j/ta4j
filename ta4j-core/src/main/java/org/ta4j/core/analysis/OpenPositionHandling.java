/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

/**
 * Controls how the last open position is handled in analysis indicators.
 *
 * @since 0.22.2
 */
public enum OpenPositionHandling {

    /**
     * Include the last open position and mark it to market at the final index.
     *
     * @since 0.22.2
     */
    MARK_TO_MARKET,

    /**
     * Ignore the last open position.
     *
     * @since 0.22.2
     */
    IGNORE
}
