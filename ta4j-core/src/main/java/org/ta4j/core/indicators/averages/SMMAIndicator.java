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
