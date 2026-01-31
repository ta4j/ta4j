/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.zigzag;

import org.ta4j.core.num.Num;

/**
 * Immutable state object representing the current state of a ZigZag pattern at
 * a given bar index.
 * <p>
 * This class encapsulates all the information needed to track the ZigZag
 * pattern's progress, including the most recently confirmed swing high and low,
 * the current trend direction, and the current extreme point being tracked.
 * <p>
 * The state is updated by {@link ZigZagStateIndicator} as it processes each bar
 * in the series, tracking price movements and confirming swing points when
 * reversals exceed the specified threshold.
 *
 * @see ZigZagStateIndicator
 * @since 0.20
 */
public final class ZigZagState {

    private final int lastHighIndex;
    private final Num lastHighPrice;

    private final int lastLowIndex;
    private final Num lastLowPrice;

    private final ZigZagTrend trend;
    private final int lastExtremeIndex;
    private final Num lastExtremePrice;

    /**
     * Constructs a new ZigZagState.
     *
     * @param lastHighIndex    the bar index of the most recently confirmed swing
     *                         high, or {@code -1} if no swing high has been
     *                         confirmed yet
     * @param lastHighPrice    the price value at the most recently confirmed swing
     *                         high, or {@code null} if no swing high has been
     *                         confirmed yet
     * @param lastLowIndex     the bar index of the most recently confirmed swing
     *                         low, or {@code -1} if no swing low has been confirmed
     *                         yet
     * @param lastLowPrice     the price value at the most recently confirmed swing
     *                         low, or {@code null} if no swing low has been
     *                         confirmed yet
     * @param trend            the current trend direction being tracked
     * @param lastExtremeIndex the bar index of the current extreme point (the
     *                         highest point in an up-trend or lowest point in a
     *                         down-trend) that may become a confirmed swing point
     * @param lastExtremePrice the price value at the current extreme point
     */
    public ZigZagState(int lastHighIndex, Num lastHighPrice, int lastLowIndex, Num lastLowPrice, ZigZagTrend trend,
            int lastExtremeIndex, Num lastExtremePrice) {
        this.lastHighIndex = lastHighIndex;
        this.lastHighPrice = lastHighPrice;
        this.lastLowIndex = lastLowIndex;
        this.lastLowPrice = lastLowPrice;
        this.trend = trend;
        this.lastExtremeIndex = lastExtremeIndex;
        this.lastExtremePrice = lastExtremePrice;
    }

    /**
     * Returns the bar index of the most recently confirmed swing high.
     *
     * @return the bar index of the most recently confirmed swing high, or
     *         {@code -1} if no swing high has been confirmed yet
     */
    public int getLastHighIndex() {
        return lastHighIndex;
    }

    /**
     * Returns the price value at the most recently confirmed swing high.
     *
     * @return the price value at the most recently confirmed swing high, or
     *         {@code null} if no swing high has been confirmed yet
     */
    public Num getLastHighPrice() {
        return lastHighPrice;
    }

    /**
     * Returns the bar index of the most recently confirmed swing low.
     *
     * @return the bar index of the most recently confirmed swing low, or {@code -1}
     *         if no swing low has been confirmed yet
     */
    public int getLastLowIndex() {
        return lastLowIndex;
    }

    /**
     * Returns the price value at the most recently confirmed swing low.
     *
     * @return the price value at the most recently confirmed swing low, or
     *         {@code null} if no swing low has been confirmed yet
     */
    public Num getLastLowPrice() {
        return lastLowPrice;
    }

    /**
     * Returns the current trend direction being tracked by the ZigZag algorithm.
     *
     * @return the current trend direction (UP, DOWN, or UNDEFINED)
     */
    public ZigZagTrend getTrend() {
        return trend;
    }

    /**
     * Returns the bar index of the current extreme point being tracked.
     * <p>
     * The extreme point is the highest point in an up-trend or the lowest point in
     * a down-trend. This point may become a confirmed swing high or low if price
     * reverses by at least the reversal threshold.
     *
     * @return the bar index of the current extreme point
     */
    public int getLastExtremeIndex() {
        return lastExtremeIndex;
    }

    /**
     * Returns the price value at the current extreme point being tracked.
     * <p>
     * The extreme point is the highest point in an up-trend or the lowest point in
     * a down-trend. This point may become a confirmed swing high or low if price
     * reverses by at least the reversal threshold.
     *
     * @return the price value at the current extreme point
     */
    public Num getLastExtremePrice() {
        return lastExtremePrice;
    }
}
