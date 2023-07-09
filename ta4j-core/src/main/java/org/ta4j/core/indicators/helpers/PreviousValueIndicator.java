/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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
 * Returns the previous (n-th) value of an indicator.
 */
public class PreviousValueIndicator extends CachedIndicator<Num> {

    private final int n;
    private final Indicator<Num> indicator;

    /**
     * Constructor.
     *
     * @param indicator the indicator from which to calculate the previous value
     */
    public PreviousValueIndicator(Indicator<Num> indicator) {
        this(indicator, 1);
    }

    /**
     * Constructor.
     *
     * @param indicator the indicator from which to calculate the previous value
     * @param n         parameter defines the previous n-th value
     */
    public PreviousValueIndicator(Indicator<Num> indicator, int n) {
        super(indicator);
        if (n < 1) {
            throw new IllegalArgumentException("n must be positive number, but was: " + n);
        }
        this.n = n;
        this.indicator = indicator;
    }

    @Override
    protected Num calculate(int index) {
        int previousValue = Math.max(0, (index - n));
        return this.indicator.getValue(previousValue);
    }

    @Override
    public int getUnstableBars() {
        return 0;
    }

    @Override
    public String toString() {
        final String nInfo = n == 1 ? "" : "(" + n + ")";
        return getClass().getSimpleName() + nInfo + "[" + this.indicator + "]";
    }
}
