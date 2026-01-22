/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
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
public class SMMAIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final Indicator<Num> indicator;

    /**
     * Constructor.
     *
     * @param indicator an indicator
     * @param barCount  the Simple Moving Average time frame
     */
    public SMMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator.getBarSeries());
        this.barCount = barCount;
        this.indicator = indicator;
    }

    @Override
    protected Num calculate(int index) {

        if (index == 0) {
            // The first SMMA value is the first data point
            return indicator.getValue(0);
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
        return barCount;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
