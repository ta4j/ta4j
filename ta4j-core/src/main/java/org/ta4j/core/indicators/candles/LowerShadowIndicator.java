/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan, Ta4j Organization & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.candles;

import org.ta4j.core.Bar;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Lower shadow height indicator.
 * <p></p>
 * Provides the (absolute) difference between the min price and the lowest price of the candle body.
 * I.e.: min price - min(open price, close price)
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:chart_analysis:introduction_to_candlesticks#formation">
 *     http://stockcharts.com/school/doku.php?id=chart_school:chart_analysis:introduction_to_candlesticks#formation</a>
 */
public class LowerShadowIndicator extends CachedIndicator<Num> {

    private final TimeSeries series;

    /**
     * Constructor.
     * @param series a time series
     */
    public LowerShadowIndicator(TimeSeries series) {
        super(series);
        this.series = series;
    }

    @Override
    protected Num calculate(int index) {
        Bar t = series.getBar(index);
        final Num openPrice = t.getOpenPrice();
        final Num closePrice = t.getClosePrice();
        if (closePrice.isGreaterThan(openPrice)) {
            // Bullish
            return openPrice.minus(t.getMinPrice());
        } else {
            // Bearish
            return closePrice.minus(t.getMinPrice());
        }
    }
}
