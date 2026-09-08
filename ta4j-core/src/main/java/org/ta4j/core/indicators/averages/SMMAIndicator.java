/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Smoothed Moving Average (SMMA) Indicator.
 *
 * Smoothed Moving Average (SMMA) is a type of moving average that applies
 * exponential smoothing over a longer period. It is designed to emphasize the
 * overall trend by minimizing the impact of short-term fluctuations. Unlike the
 * Exponential Moving Average (EMA), which assigns more weight to recent prices,
 * the SMMA evenly distributes the influence of older data while still applying
 * some smoothing.
 *
 */
public class SMMAIndicator extends RecursiveCachedIndicator<Num> {

    private final int barCount;
    private final Indicator<Num> indicator;

    /**
     * Constructor.
     *
     * @param indicator an indicator
     * @param barCount  the Simple Moving Average time frame
     */
    public SMMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);

        this.barCount = barCount;
        this.indicator = indicator;
    }

    @Override
    protected Num calculate(int index) {

        if (index <= getBarSeries().getBeginIndex()) {
            // Seed at the first retained bar: for a full series this is the first
            // data point; for a bounded series it re-anchors the recursion after
            // a head advance evicted the previous seed.
            return indicator.getValue(index);
        }

        // Previous SMMA value
        Num previousSMMA = getValue(index - 1);

        // Current price
        Num currentPrice = indicator.getValue(index);

        var numFactory = indicator.getBarSeries().numFactory();

        // SMMA formula
        return previousSMMA.multipliedBy(numFactory.numOf(barCount - 1))
                .plus(currentPrice)
                .dividedBy(numFactory.numOf(barCount));
    }

    @Override
    public int getCountOfUnstableBars() {
        return indicator.getCountOfUnstableBars() + barCount;
    }

    /**
     * The SMMA recursion seeds at the first retained bar (see
     * {@link #calculate(int)}), so after a head advance every cached value embeds
     * the pre-advance chain. Discard the whole cache so the retained head
     * re-anchors the recurrence from the first retained bar instead of serving
     * stale values.
     *
     * @return {@code true}
     */
    @Override
    protected boolean requiresFullCacheInvalidationAfterHeadAdvance() {
        return true;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }

}
