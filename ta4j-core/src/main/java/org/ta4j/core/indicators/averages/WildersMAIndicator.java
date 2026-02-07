/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.RunningTotalIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Wilder's moving average indicator.
 *
 * The Wilder’s Moving Average indicator (Wilder’s Smoothed Moving Average ) was
 * developed by Welles Wilder and introduced in his 1978 book, “New Concepts in
 * Technical Trading Systems.”
 *
 * The WMA inherently "remembers" past values due to its recursive formula, but
 * the initialization WMA can significantly bias early values. This bias
 * diminishes over time as more data contributes to the calculation. See the
 * test cases for this indicator as it skips the initial data until the
 * calculation stabilizes.
 *
 * @see <a href=
 *      "https://www.tradingview.com/script/wXtQeoOg/#:~:text=Wilder%20did%20not%20use%20the,where%20K%20%3D1%2FN.">
 *      https://www.tradingview.com/script/wXtQeoOg/#:~:text=Wilder%20did%20not%20use%20the,where%20K%20%3D1%2FN.</a>
 */
public class WildersMAIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;
    private final transient RunningTotalIndicator sumPriceIndicator;

    /**
     * Constructor.
     *
     * @param indicator an indicator
     * @param barCount  the Wilder Moving Average time frame
     */
    public WildersMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
        this.sumPriceIndicator = new RunningTotalIndicator(indicator, barCount);
    }

    @Override
    protected Num calculate(int index) {
        NumFactory numFactory = getBarSeries().numFactory();
        Num one = numFactory.one();

        Num numBars = numFactory.numOf(barCount);
        Num k = one.dividedBy(numBars);
        Num prevWMA = indicator.getValue(0);

        // Simulate extended historical data for initialization
        if (index < barCount) {
            // Pretend there are extra `barCount` points before the first real point
            // Use the first actual value as a proxy for the "missing" earlier data
            Num simulatedValue = indicator.getValue(0);

            // Initialize with a simulated Simple Moving Average (SMA)
            Num sum = sumPriceIndicator.getValue(index);

            // Return average of available points for initialization
            Num preResult = sum.plus(simulatedValue.multipliedBy(numFactory.numOf(barCount - index - 1)))
                    .dividedBy(numBars);

            prevWMA = preResult;
            return preResult;
        }

        // For the first value, initialize with the first input value

        if (index > 0) {
            prevWMA = getValue(index - 1);
        }
        Num currentPrice = indicator.getValue(index);
        return currentPrice.multipliedBy(k).plus(prevWMA.multipliedBy(numFactory.one().minus(k)));
    }

    @Override
    public int getCountOfUnstableBars() {
        return indicator.getCountOfUnstableBars() + (barCount * 2);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
