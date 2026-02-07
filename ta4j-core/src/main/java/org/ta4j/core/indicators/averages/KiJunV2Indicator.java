/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Kihon Moving Average Indicator (KiJunV2).
 *
 * Kihon Moving Average (KiJunV2) is a technical indicator derived from Ichimoku
 * Kinko Hyo, a popular Japanese trading strategy. It calculates the average of
 * the highest high and the lowest low over a specific period. As a
 * midpoint-based moving average, it provides an intuitive way to understand
 * market equilibrium and is particularly useful in identifying trends and
 * potential reversals.
 *
 */
public class KiJunV2Indicator extends CachedIndicator<Num> {

    private final int barCount; // Lookback period
    private final NumFactory numFactory;
    private final HighestValueIndicator highestValue;
    private final LowestValueIndicator lowestValue;

    /**
     * Constructor.
     *
     * @param highPrice the high price indicator
     * @param lowPrice  the low price indicator
     * @param barCount  the Simple Moving Average time frame
     */
    public KiJunV2Indicator(Indicator<Num> highPrice, Indicator<Num> lowPrice, int barCount) {
        super(highPrice.getBarSeries());
        this.barCount = barCount;
        this.numFactory = highPrice.getBarSeries().numFactory();
        this.highestValue = new HighestValueIndicator(new HighPriceIndicator(highPrice.getBarSeries()), barCount);
        this.lowestValue = new LowestValueIndicator(new LowPriceIndicator(lowPrice.getBarSeries()), barCount);
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }

        // Get the highest high and lowest low within the barCount period
        Num highestHigh = highestValue.calculate(index);
        Num lowestLow = lowestValue.calculate(index);

        // Calculate the midpoint
        return highestHigh.plus(lowestLow).dividedBy(numFactory.two());
    }

    @Override
    public int getCountOfUnstableBars() {
        return Math.max(highestValue.getCountOfUnstableBars(), lowestValue.getCountOfUnstableBars());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }

}
