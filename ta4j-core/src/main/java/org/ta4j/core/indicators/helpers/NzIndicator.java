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
 * nz indicator, inspired by PineScript nz function.
 *
 * @see <a href=
 *      "https://www.tradingview.com/pine-script-reference/v5/#fun_nz">TradingView</a>
 */
public class NzIndicator extends CachedIndicator<Num> {
    private final Indicator<Num> indicator;
    private final Indicator<Num> replacement;

    public NzIndicator(Indicator<Num> indicator) {
        this(indicator, indicator.getBarSeries().numFactory().zero());
    }

    public NzIndicator(Indicator<Num> indicator, Number replacement) {
        this(indicator, indicator.getBarSeries().numFactory().numOf(replacement));
    }

    public NzIndicator(Indicator<Num> indicator, Num replacement) {
        this(indicator, new ConstantIndicator<>(indicator.getBarSeries(), replacement));
    }

    public NzIndicator(Indicator<Num> indicator, Indicator<Num> replacement) {
        super(indicator);

        this.indicator = indicator;
        this.replacement = replacement;
    }

    @Override
    protected Num calculate(int index) {
        Num value = indicator.getValue(index);
        return value.isNaN() ? replacement.getValue(index) : value;
    }

    @Override
    public int getUnstableBars() {
        return indicator.getUnstableBars();
    }
}
