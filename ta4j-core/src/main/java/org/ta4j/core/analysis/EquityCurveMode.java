/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

/**
 * Defines how equity curves are computed.
 * <p>
 * {@link #MARK_TO_MARKET} reflects unrealized profit and loss on each bar using
 * intermediate prices, while {@link #REALIZED} updates the curve only when a
 * position is closed, keeping interim bars flat.
 * </p>
 *
 * @since 0.22.2
 */
public enum EquityCurveMode {

    /**
     * Updates the equity curve on every bar, including unrealized gains/losses.
     */
    MARK_TO_MARKET,

    /**
     * Updates the equity curve only when positions close, reflecting realized
     * gains/losses.
     */
    REALIZED
}