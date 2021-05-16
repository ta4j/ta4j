/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.helpers.TRIndicator;
import org.ta4j.core.num.Num;

/**
 * Average true range indicator.
 */
public class ATRIndicator extends AbstractIndicator<Num> {

    private final TRIndicator trIndicator;
    private final MMAIndicator averageTrueRangeIndicator;

    public ATRIndicator(BarSeries series, int barCount) {
        this(new TRIndicator(series), barCount);
    }

    public ATRIndicator(TRIndicator tr, int barCount) {
        super(tr.getBarSeries());
        this.trIndicator = tr;
        this.averageTrueRangeIndicator = new MMAIndicator(new TRIndicator(tr.getBarSeries()), barCount);
    }

    @Override
    public Num getValue(int index) {
        return averageTrueRangeIndicator.getValue(index);
    }

    public TRIndicator getTRIndicator() {
        return trIndicator;
    }

    public int getBarCount() {
        return averageTrueRangeIndicator.getBarCount();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + getBarCount();
    }
}
