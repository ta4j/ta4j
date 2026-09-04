/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;

/**
 * Cached base for candlestick pattern indicators whose evaluation is only
 * defined once the preceding baseline window is addressable.
 *
 * <p>
 * Pattern indicators delegate threshold evaluation to
 * {@link CandleThresholdSupport}, which requires the {@code averagePeriod}
 * candles immediately preceding {@code index} to be available.
 * {@link CachedIndicator} retains results by index even when bars are removed
 * from the head of a bounded series, so a {@code true} computed while
 * {@code index} still had its full window can survive the window sliding past
 * it: the index stays addressable, but a fresh calculation would answer
 * {@code false} because the baseline window is no longer complete. Reads of
 * addressable indexes that fail the support's validity gate therefore return
 * {@code false} without consulting the cache. Reads outside the retained range
 * keep the inherited cached-indicator behavior.
 *
 * @since 0.24.2
 */
abstract class CandlePatternIndicator extends CachedIndicator<Boolean> {

    /** Shared causal threshold evaluation against the preceding window. */
    protected final transient CandleThresholdSupport thresholds;

    /**
     * Constructor.
     *
     * @param series     the bar series
     * @param thresholds the threshold support shared with other pattern indicators
     *                   over the same series and period
     */
    CandlePatternIndicator(final BarSeries series, final CandleThresholdSupport thresholds) {
        super(series);
        this.thresholds = thresholds;
    }

    @Override
    public Boolean getValue(final int index) {
        final BarSeries series = getBarSeries();
        if (series != null && index >= series.getBeginIndex() && index <= series.getEndIndex()
                && !thresholds.isValid(latestBaselineIndex(index))) {
            return false;
        }
        return super.getValue(index);
    }

    /**
     * The most recent candle whose full preceding baseline window must be available
     * for the pattern ending at {@code index} to be evaluable. Single-candle
     * patterns need {@code index} itself; two-candle patterns override this to
     * {@code index - 1} because their earlier candle must also carry a complete
     * baseline.
     *
     * @param index the pattern index
     * @return the index whose threshold validity gates the pattern at {@code index}
     */
    int latestBaselineIndex(final int index) {
        return index;
    }
}
