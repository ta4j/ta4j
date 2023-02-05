/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2022 Ta4j Organization & respective
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
package org.ta4j.core.indicators.supertrend;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

public class SuperTrendIndicator extends RecursiveCachedIndicator<Num> {
    private final UpperBandIndicator upperBandIndicator;
    private final LowerBandIndicator lowerBandIndicator;

    public SuperTrendIndicator(final BarSeries series, int length, final Integer multiplier) {
        super(series);
        ATRIndicator atrIndicator = new ATRIndicator(series, length);
        this.upperBandIndicator = new UpperBandIndicator(series, atrIndicator, multiplier);
        this.lowerBandIndicator = new LowerBandIndicator(series, atrIndicator, multiplier);
    }

    public SuperTrendIndicator(final BarSeries series) {
        this(series, 3, 10);
    }

    @Override
    protected Num calculate(int i) {
        Num value = DoubleNum.valueOf(0);

        if (i == 0)
            return value;
        Bar bar = getBarSeries().getBar(i);
        /*
         * if(bar.getClosePrice().isLessThanOrEqual(this.upperBandIndicator.getValue(i))
         * ) value = this.upperBandIndicator.getValue(i); else value =
         * this.lowerBandIndicator.getValue(i);
         */

        if (this.getValue(i - 1).isEqual(this.upperBandIndicator.getValue(i - 1))
                && bar.getClosePrice().isLessThanOrEqual(this.upperBandIndicator.getValue(i))) {
            value = this.upperBandIndicator.getValue(i);
        }

        if (this.getValue(i - 1).isEqual(this.upperBandIndicator.getValue(i - 1))
                && bar.getClosePrice().isGreaterThan(this.upperBandIndicator.getValue(i))) {
            value = this.lowerBandIndicator.getValue(i);
        } else if (this.getValue(i - 1).isEqual(this.lowerBandIndicator.getValue(i - 1))
                && bar.getClosePrice().isGreaterThanOrEqual(this.lowerBandIndicator.getValue(i))) {
            value = this.lowerBandIndicator.getValue(i);
        } else if (this.getValue(i - 1).isEqual(this.lowerBandIndicator.getValue(i - 1))
                && bar.getClosePrice().isLessThan(this.upperBandIndicator.getValue(i))) {
            value = this.upperBandIndicator.getValue(i);
        }

        return value;
    }

    public String getSignal(int index) {
        if (getBarSeries().getBar(index).getClosePrice().isLessThanOrEqual(this.getValue(index))) {
            return "Sell";
        }
        return "Buy";
    }
}
