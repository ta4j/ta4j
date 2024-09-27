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
package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Chande's Range Action Verification Index (RAVI) indicator.
 *
 * <p>
 * To preserve trend direction, default calculation does not use absolute value.
 */
public class RAVIIndicator extends CachedIndicator<Num> {

    private final SMAIndicator shortSma;
    private final SMAIndicator longSma;

    /**
     * Constructor.
     *
     * @param price            the price
     * @param shortSmaBarCount the time frame for the short SMA (usually 7)
     * @param longSmaBarCount  the time frame for the long SMA (usually 65)
     */
    public RAVIIndicator(Indicator<Num> price, int shortSmaBarCount, int longSmaBarCount) {
        super(price);
        this.shortSma = new SMAIndicator(price, shortSmaBarCount);
        this.longSma = new SMAIndicator(price, longSmaBarCount);
    }

    @Override
    protected Num calculate(int index) {
        Num shortMA = shortSma.getValue(index);
        Num longMA = longSma.getValue(index);
        return shortMA.minus(longMA).dividedBy(longMA).multipliedBy(getBarSeries().numFactory().hundred());
    }

    @Override
    public int getUnstableBars() {
        return 0;
    }
}
