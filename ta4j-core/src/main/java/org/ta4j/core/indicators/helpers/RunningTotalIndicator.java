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
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Running Total aka Cumulative Sum indicator
 *
 * @see <a href=
 *      "https://en.wikipedia.org/wiki/Running_total">https://en.wikipedia.org/wiki/Running_total</a>
 */
public class RunningTotalIndicator extends CachedIndicator<Num> {
    private final Indicator<Num> indicator;
    private final int barCount;
    private Num previousSum;

    // serial access detection
    private int previousIndex = -1;

    public RunningTotalIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
        this.previousSum = indicator.getBarSeries().numFactory().zero();
    }

    @Override
    protected Num calculate(int index) {
        // serial access can benefit from previous partial sums
        // which saves a lot of CPU work for very long barCounts
        if (previousIndex != -1 && previousIndex == index - 1) {
            return fastPath(index);
        }

        return slowPath(index);
    }

    private Num fastPath(final int index) {
        var newSum = partialSum(index);
        updatePartialSum(index, newSum);
        return newSum;
    }

    private Num slowPath(final int index) {
        Num sum = getBarSeries().numFactory().zero();
        for (int i = Math.max(0, index - barCount + 1); i <= index; i++) {
            sum = sum.plus(indicator.getValue(i));
        }

        updatePartialSum(index, sum);
        return sum;
    }

    private void updatePartialSum(final int index, final Num sum) {
        previousIndex = index;
        previousSum = sum;
    }

    private Num partialSum(int index) {
        var sum = this.previousSum.plus(indicator.getValue(index));

        if (index >= barCount) {
            return sum.minus(indicator.getValue(index - barCount));
        }

        return sum;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }

    @Override
    public int getUnstableBars() {
        return barCount;
    }
}
