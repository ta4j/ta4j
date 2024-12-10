/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
    private final RunningTotalIndicator sumPriceIndicator;

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
        return barCount * 2;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
