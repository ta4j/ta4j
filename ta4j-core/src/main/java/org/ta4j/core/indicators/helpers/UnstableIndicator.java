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
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Indicator that returns {@link NaN#NaN NaN} in unstable bars.
 */
public class UnstableIndicator extends CachedIndicator<Num> {

    private final int unstableBars;
    private final Indicator<Num> indicator;

    /**
     * Constructor.
     *
     * @param indicator    the indicator
     * @param unstableBars the number of first bars of the barSeries to be unstable
     */
    public UnstableIndicator(Indicator<Num> indicator, int unstableBars) {
        super(indicator);
        this.indicator = indicator;
        this.unstableBars = unstableBars;
    }

    @Override
    protected Num calculate(int index) {
        if (index < unstableBars) {
            return NaN.NaN;
        }
        return indicator.getValue(index);
    }

    /** @return {@link #unstableBars} */
    @Override
    public int getUnstableBars() {
        return unstableBars;
    }
}
