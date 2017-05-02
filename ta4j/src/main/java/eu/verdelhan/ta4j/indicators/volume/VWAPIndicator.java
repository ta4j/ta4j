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
package eu.verdelhan.ta4j.indicators.volume;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.CachedIndicator;
import eu.verdelhan.ta4j.indicators.simple.TypicalPriceIndicator;
import eu.verdelhan.ta4j.indicators.simple.VolumeIndicator;

/**
 * The volume-weighted average price (VWAP) Indicator.
 * @see http://www.investopedia.com/articles/trading/11/trading-with-vwap-mvwap.asp
 * @see http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:vwap_intraday
 * @see https://en.wikipedia.org/wiki/Volume-weighted_average_price
 */
public class VWAPIndicator extends CachedIndicator<Decimal> {

    private final int timeFrame;
    
    private final Indicator<Decimal> typicalPrice;
    
    private final Indicator<Decimal> volume;
    
    /**
     * Constructor.
     * @param series the series
     * @param timeFrame the time frame
     */
    public VWAPIndicator(TimeSeries series, int timeFrame) {
        super(series);
        this.timeFrame = timeFrame;
        typicalPrice = new TypicalPriceIndicator(series);
        volume = new VolumeIndicator(series);
    }

    @Override
    protected Decimal calculate(int index) {
        if (index <= 0) {
            return typicalPrice.getValue(index);
        }
        int startIndex = Math.max(0, index - timeFrame + 1);
        Decimal cumulativeTPV = Decimal.ZERO;
        Decimal cumulativeVolume = Decimal.ZERO;
        for (int i = startIndex; i <= index; i++) {
            Decimal currentVolume = volume.getValue(i);
            cumulativeTPV = cumulativeTPV.plus(typicalPrice.getValue(i).multipliedBy(currentVolume));
            cumulativeVolume = cumulativeVolume.plus(currentVolume);
        }
        return cumulativeTPV.dividedBy(cumulativeVolume);
    }
}
