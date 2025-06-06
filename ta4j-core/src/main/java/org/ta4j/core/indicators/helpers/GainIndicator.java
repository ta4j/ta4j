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
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Gain indicator.
 *
 * <p>
 * Returns the difference of the indicator value of a bar and its previous bar
 * if the indicator value of the current bar is greater than the indicator value
 * of the previous bar (otherwise, {@link Num#zero()} is returned).
 */
public class GainIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;

    /**
     * Constructor.
     *
     * @param indicator the {@link Indicator}
     */
    public GainIndicator(Indicator<Num> indicator) {
        super(indicator);
        this.indicator = indicator;
    }

    @Override
    protected Num calculate(int index) {
        if (index == 0) {
            return getBarSeries().numFactory().zero();
        }
        Num actualValue = indicator.getValue(index);
        Num previousValue = indicator.getValue(index - 1);
        return actualValue.isGreaterThan(previousValue) ? actualValue.minus(previousValue)
                : getBarSeries().numFactory().zero();
    }

    /** @return {@code 1} */
    @Override
    public int getCountOfUnstableBars() {
        return 1;
    }
}
