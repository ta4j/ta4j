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
 * Indicator to replace a given value in another indicator. All other values are
 * unchanged.
 */
public class SubstituteIndicator extends CachedIndicator<Num> {
    private final Indicator<Num> indicator;
    private final Num value;
    private final Num substitute;

    public SubstituteIndicator(Indicator<Num> indicator, Number value, Number substitute) {
        this(indicator, indicator.getBarSeries().numFactory().numOf(value),
                indicator.getBarSeries().numFactory().numOf(substitute));
    }

    public SubstituteIndicator(Indicator<Num> indicator, Num value, Num substitute) {
        super(indicator);
        this.indicator = indicator;
        this.value = value;
        this.substitute = substitute;
    }

    @Override
    protected Num calculate(int index) {
        Num value = indicator.getValue(index);

        if (value.equals(this.value)) {
            return substitute;
        } else {
            return value;
        }
    }

    @Override
    public int getUnstableBars() {
        return indicator.getUnstableBars();
    }
}
