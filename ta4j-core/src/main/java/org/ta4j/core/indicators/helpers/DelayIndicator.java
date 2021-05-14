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
package org.ta4j.core.indicators.helpers;

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Delay indicator.
 *
 * Indicator, which returns as result the value of the given indicator delayed.
 * If the delay is positive, the value from the past is returned (if available)
 * If the delay is negative, the value in future is returned (if available) If
 * the delay moves outside of the available bars in the series, NaN is returned.
 */
public class DelayIndicator extends CachedIndicator<Num> {
    private final int delay;
    private final Indicator<Num> indicator;

    public DelayIndicator(Indicator<Num> indicator, int delay) {
        super(indicator);
        this.indicator = indicator;
        this.delay = delay;
    }

    @Override
    protected Num calculate(int index) {
        int delayedIndex = index - delay;
        if (delayedIndex >= getBarSeries().getBeginIndex() && delayedIndex <= getBarSeries().getEndIndex()) {
            return indicator.getValue(delayedIndex);
        }
        return NaN;
    }
}
