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
package eu.verdelhan.ta4j.indicators.trackers.bollinger;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.indicators.CachedIndicator;
import eu.verdelhan.ta4j.indicators.statistics.StandardDeviationIndicator;
import eu.verdelhan.ta4j.indicators.trackers.SMAIndicator;

/**
 * %B indicator.
 * @see http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:bollinger_band_perce
 */
public class PercentBIndicator extends CachedIndicator<Decimal> {
    
    private final Indicator<Decimal> indicator;

    private final BollingerBandsUpperIndicator bbu;
    
    private final BollingerBandsMiddleIndicator bbm;
    
    private final BollingerBandsLowerIndicator bbl;

    /**
     * Constructor.
     * @param indicator an indicator (usually close price)
     * @param timeFrame the time frame
     * @param k the K multiplier (usually 2.0)
     */
    public PercentBIndicator(Indicator<Decimal> indicator, int timeFrame, Decimal k) {
        super(indicator);
        this.indicator = indicator;
        this.bbm = new BollingerBandsMiddleIndicator(new SMAIndicator(indicator, timeFrame));
        StandardDeviationIndicator sd = new StandardDeviationIndicator(indicator, timeFrame);
        this.bbu = new BollingerBandsUpperIndicator(bbm, sd, k);
        this.bbl = new BollingerBandsLowerIndicator(bbm, sd, k);;
    }

    @Override
    protected Decimal calculate(int index) {
        Decimal value = indicator.getValue(index);
        Decimal upValue = bbu.getValue(index);
        Decimal lowValue = bbl.getValue(index);
        return value.minus(lowValue).dividedBy(upValue.minus(lowValue));
    }
}
