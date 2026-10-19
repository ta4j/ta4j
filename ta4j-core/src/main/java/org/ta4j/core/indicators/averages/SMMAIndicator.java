/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.NaN;
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
        while (true) {
            final int beginIndex = getBarSeries().getBeginIndex();
            if (beginIndex < 0) {
                // Empty series: there is no bar to seed from or recurse to.
                return NaN.NaN;
            }
            if (index < beginIndex) {
                // A removed index mapped here by CachedIndicator: serve the first
                // available bar's value (the retained recursion, or its seed).
                // calculate(beginIndex) reads no predecessor, so this cannot recurse.
                return getValue(beginIndex);
            }
            // Seed at the first retained bar: for a full series this is the first
            // data point. On a bounded series retained values survive head
            // advances, so this seed is only reached when no cached predecessor
            // exists (for example a fresh indicator).
            Num value = index == beginIndex ? indicator.getValue(index) : continueChain(index);
            if (getBarSeries().getBeginIndex() == beginIndex) {
                return value;
            }
            // The head advanced while this value was computed, so its predecessor
            // or source may have been read from bars that are now removed. Retry
            // against the new head.
        }
    }

    private Num continueChain(int index) {
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

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }

}
