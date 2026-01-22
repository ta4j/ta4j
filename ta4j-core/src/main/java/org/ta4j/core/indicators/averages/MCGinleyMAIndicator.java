/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
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
public class MCGinleyMAIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;

    /**
     * Constructor.
     *
     * @param indicator an indicator
     * @param barCount  the Simple Moving Average time frame
     */
    public MCGinleyMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator.getBarSeries());
        this.indicator = indicator;
        this.barCount = barCount;

    }

    @Override
    protected Num calculate(int index) {
        if (index == 0) {
            return indicator.getValue(0);
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
        return barCount;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
