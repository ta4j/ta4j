/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.zigzag;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.AbstractRecentSwingIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Recent ZigZag Swing High Indicator.
 * <p>
 * Returns the price value of the most recently confirmed ZigZag swing high at
 * each bar. A swing high is confirmed when price reverses downward by at least
 * the reversal threshold after reaching a new high during an up-trend.
 * <p>
 * This indicator is useful for:
 * <ul>
 * <li>Tracking the most recent swing high price level</li>
 * <li>Identifying potential resistance levels</li>
 * <li>Calculating distances from current price to the last swing high</li>
 * <li>Building trend-following strategies based on swing highs</li>
 * </ul>
 * <p>
 * The indicator returns {@link NaN} if no swing high has been confirmed yet at
 * the given index. Use {@link ZigZagPivotHighIndicator} to detect when a new
 * swing high is confirmed in real-time.
 * <p>
 * The price indicator passed to the constructor should typically match the
 * price indicator used in the underlying {@link ZigZagStateIndicator} (e.g.,
 * both using {@code HighPriceIndicator} or both using
 * {@code ClosePriceIndicator}).
 *
 * @see ZigZagStateIndicator
 * @see ZigZagPivotHighIndicator
 * @see RecentZigZagSwingLowIndicator
 * @see <a href=
 *      "https://www.investopedia.com/terms/s/swinghigh.asp">Investopedia: Swing
 *      High</a>
 * @since 0.20
 */
public class RecentZigZagSwingHighIndicator extends AbstractRecentSwingIndicator {

    private final ZigZagStateIndicator stateIndicator;
    private final Indicator<Num> price; // same price as used in stateIndicator

    /**
     * Constructs a RecentZigZagSwingHighIndicator.
     *
     * @param stateIndicator the ZigZagStateIndicator that tracks the ZigZag pattern
     *                       state
     * @param price          the price indicator to use for retrieving swing high
     *                       values. Should typically match the price indicator used
     *                       in the stateIndicator (e.g., both using
     *                       {@code HighPriceIndicator} or both using
     *                       {@code ClosePriceIndicator})
     */
    public RecentZigZagSwingHighIndicator(ZigZagStateIndicator stateIndicator, Indicator<Num> price) {
        super(price, 0);
        this.stateIndicator = stateIndicator;
        this.price = price;
    }

    public RecentZigZagSwingHighIndicator(BarSeries series) {
        this(new ZigZagStateIndicator(new HighPriceIndicator(series), new ATRIndicator(series, 14)),
                new HighPriceIndicator(series));
    }

    /**
     * Returns the index of the most recent confirmed swing high as of the given
     * index.
     *
     * @param index the bar index to evaluate
     * @return the index of the most recent confirmed swing high, or {@code -1} if
     *         no swing high has been confirmed yet
     */
    public int getLatestSwingHighIndex(int index) {
        return getLatestSwingIndex(index);
    }

    @Override
    public Indicator<Num> getPriceIndicator() {
        return price;
    }

    @Override
    protected int detectLatestSwingIndex(int index) {
        final ZigZagState state = stateIndicator.getValue(index);
        return state.getLastHighIndex();
    }
}
