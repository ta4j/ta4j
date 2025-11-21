/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

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

        // Get the highest high and lowest low within the barCount period
        Num highestHigh = highestValue.calculate(index);
        Num lowestLow = lowestValue.calculate(index);

        // Calculate the midpoint
        return highestHigh.plus(lowestLow).dividedBy(numFactory.two());
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
