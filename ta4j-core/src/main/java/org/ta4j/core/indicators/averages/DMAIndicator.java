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
import org.ta4j.core.num.Num;

/**
 * Displaced Moving Average (DMA) indicator.
 *
 * Displaced Moving Average (DMA) is a variation of traditional moving averages,
 * where the average is shifted forward or backward in time. This displacement
 * allows traders to anticipate future price movements or analyze historical
 * trends more effectively. DMA is not a standalone moving average but rather a
 * way of adjusting existing ones (such as SMA or EMA) by a certain number of
 * periods.
 *
 */
public class DMAIndicator extends SMAIndicator {

    private final int barCount;
    private final int displacement;

    /**
     * Constructor.
     *
     * @param indicator    an indicator
     * @param barCount     the Simple Moving Average time frame
     * @param displacement the Simple Moving Average displacement, positive or
     *                     negative
     */
    public DMAIndicator(Indicator<Num> indicator, int barCount, int displacement) {
        super(indicator, barCount);
        this.barCount = barCount;
        this.displacement = displacement;
    }

    @Override
    protected Num calculate(int index) {

        int displacedIndex = index - displacement;
        if (displacedIndex < 0) {
            return super.calculate(0);
        }
        if (displacedIndex >= getBarSeries().getEndIndex()) {
            return super.calculate(getBarSeries().getEndIndex() - 1);
        }

        return super.calculate(displacedIndex);
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
