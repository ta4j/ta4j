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
package org.ta4j.core.indicators.bollinger;

import org.ta4j.core.Decimal;
import org.ta4j.core.indicators.CachedIndicator;

/**
 * Bollinger BandWidth indicator.
 * @see http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:bollinger_band_width
 */
public class BollingerBandWidthIndicator extends CachedIndicator<Decimal> {

    private final BollingerBandsUpperIndicator bbu;
    
    private final BollingerBandsMiddleIndicator bbm;
    
    private final BollingerBandsLowerIndicator bbl;

    public BollingerBandWidthIndicator(BollingerBandsUpperIndicator bbu, BollingerBandsMiddleIndicator bbm, BollingerBandsLowerIndicator bbl) {
        super(bbm.getTimeSeries());
        this.bbu = bbu;
        this.bbm = bbm;
        this.bbl = bbl;
    }

    @Override
    protected Decimal calculate(int index) {
        return bbu.getValue(index).minus(bbl.getValue(index))
                .dividedBy(bbm.getValue(index)).multipliedBy(Decimal.HUNDRED);
    }
}
