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

import java.util.function.Function;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Num indicator.
 * 
 * <p>
 * Returns a {@link Num} of (or for) a bar.
 * 
 * <p>
 * <b>Hint:</b> It is recommended to use the {@code NumIndicator} with its
 * {@link #action} mainly for complex functions and not for simple get
 * functions. For simple get functions it might be better to use the
 * corresponding indicator due to better readability and complexity (e.g. to get
 * the close price just use the {@code ClosePriceIndicator} instead of the
 * {@code NumIndicator}).
 */
public class NumIndicator extends CachedIndicator<Num> {

    /** The action to calculate or determine a num on the bar. */
    private final Function<Bar, Num> action;

    /**
     * Constructor.
     * 
     * @param barSeries the bar series
     * @param action    the action to calculate or determine a num on the bar
     */
    public NumIndicator(BarSeries barSeries, Function<Bar, Num> action) {
        super(barSeries);
        this.action = action;
    }

    @Override
    protected Num calculate(int index) {
        Bar bar = getBarSeries().getBar(index);
        return this.action.apply(bar);
    }

    @Override
    public int getUnstableBars() {
        return 0;
    }
}
