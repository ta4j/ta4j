/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.zigzag;

/**
 * Represents the current trend direction in a ZigZag pattern.
 * <p>
 * The ZigZag indicator identifies significant price reversals by filtering out
 * price movements below a specified threshold. This enum tracks the current
 * direction of the trend being tracked by the ZigZag algorithm.
 *
 * @see ZigZagStateIndicator
 * @since 0.20
 */
public enum ZigZagTrend {
    /**
     * The trend is moving upward. The ZigZag is tracking a rising leg and looking
     * for a swing high that will be confirmed when price reverses down by at least
     * the reversal threshold.
     */
    UP,

    /**
     * The trend is moving downward. The ZigZag is tracking a falling leg and
     * looking for a swing low that will be confirmed when price reverses up by at
     * least the reversal threshold.
     */
    DOWN,

    /**
     * The trend direction has not yet been determined. This occurs at the beginning
     * of the bar series before the first reversal threshold is met.
     */
    UNDEFINED
}
