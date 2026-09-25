/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;

/**
 * McGinley Moving Average (McGinleyMA) Indicator.
 *
 * The McGinley Moving Average is a technical analysis tool developed by John
 * McGinley to address issues with traditional moving averages such as lag and
 * responsiveness to market volatility. It is designed to adapt dynamically to
 * changing market conditions by incorporating a smoothing factor that adjusts
 * automatically based on the speed of price movement. This makes it less prone
 * to false signals and more reliable for identifying trends in both volatile
 * and stable markets.
 *
 */
public class MCGinleyMAIndicator extends RecursiveCachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;

    /**
     * Constructor.
     *
     * @param indicator an indicator
     * @param barCount  the Simple Moving Average time frame
     */
    public MCGinleyMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);

        this.indicator = indicator;
        this.barCount = barCount;

    }

    @Override
    protected Num calculate(int index) {
        if (index <= getBarSeries().getBeginIndex()) {
            // Seed at the first retained bar: for a full series this is the
            // first data point; for a bounded series it re-anchors the
            // recursion after a head advance evicted the previous seed.
            return indicator.getValue(index);
        }

        // McGinley_t = McGinley_(t-1) + (Price_t - McGinley_(t-1)) / (barCount *
        // (Price_t / McGinley_(t-1))^2)

        // Get the previous McGinley value
        Num previousMcGinley = getValue(index - 1);

        // Current price
        Num currentPrice = indicator.getValue(index);

        // Speed ratio (smoothing factor)
        Num numBars = indicator.getBarSeries().numFactory().numOf(barCount);
        Num speedRatio = numBars.multipliedBy(currentPrice.dividedBy(previousMcGinley).pow(2));

        // McGinley formula
        return previousMcGinley.plus(currentPrice.minus(previousMcGinley).dividedBy(speedRatio));

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
