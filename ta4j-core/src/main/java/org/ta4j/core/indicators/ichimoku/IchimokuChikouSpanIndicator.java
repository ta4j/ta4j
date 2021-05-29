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
package org.ta4j.core.indicators.ichimoku;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Ichimoku clouds: Chikou Span indicator
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud</a>
 */
public class IchimokuChikouSpanIndicator extends CachedIndicator<Num> {

    /**
     * The close price
     */
    private final ClosePriceIndicator closePriceIndicator;

    /**
     * Constructor. The ichimoku chikou span returns for an index i always the
     * current close price for i. Only its usage and comparison against other prices
     * takes into account the past. E.g. new OverIndicatorRule(chikouSpanIndicator,
     * new PreviousValueIndicator(closePriceIndicator, chikouSpanDelay)) This rule
     * is satisfied, if the current value of the span is over the old close price.
     *
     * The chikou span calculation is always based on the current values, but only
     * printed into the past!
     *
     * @param series the series
     */
    public IchimokuChikouSpanIndicator(BarSeries series) {
        super(series);
        this.closePriceIndicator = new ClosePriceIndicator(series);
    }

    @Override
    protected Num calculate(int i) {
        return closePriceIndicator.getValue(i);
    }

}
