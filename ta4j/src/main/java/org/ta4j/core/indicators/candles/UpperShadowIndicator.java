/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
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
package org.ta4j.core.indicators.candles;

import org.ta4j.core.Decimal;
import org.ta4j.core.Tick;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;

/**
 * Upper shadow height indicator.
 * <p>
 * Provides the (absolute) difference between the max price and the highest price of the candle body.
 * I.e.: max price - max(open price, close price)
 * @see http://stockcharts.com/school/doku.php?id=chart_school:chart_analysis:introduction_to_candlesticks#formation
 */
public class UpperShadowIndicator extends CachedIndicator<Decimal> {

    private final TimeSeries series;

    /**
     * Constructor.
     * @param series a time series
     */
    public UpperShadowIndicator(TimeSeries series) {
        super(series);
        this.series = series;
    }

    @Override
    protected Decimal calculate(int index) {
        Tick t = series.getTick(index);
        final Decimal openPrice = t.getOpenPrice();
        final Decimal closePrice = t.getClosePrice();
        if (closePrice.isGreaterThan(openPrice)) {
            // Bullish
            return t.getMaxPrice().minus(closePrice);
        } else {
            // Bearish
            return t.getMaxPrice().minus(openPrice);
        }
    }
}
