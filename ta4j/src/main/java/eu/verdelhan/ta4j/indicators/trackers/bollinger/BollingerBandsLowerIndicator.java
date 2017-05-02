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

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.indicators.CachedIndicator;

/**
 * Buy - Occurs when the price line cross from down to up de Bollinger Band Low.
 * Sell - Occurs when the price line cross from up to down de Bollinger Band High.
 * 
 */
public class BollingerBandsLowerIndicator extends CachedIndicator<Decimal> {

    private final Indicator<Decimal> indicator;

    private final BollingerBandsMiddleIndicator bbm;

    private final Decimal k;

    public BollingerBandsLowerIndicator(BollingerBandsMiddleIndicator bbm, Indicator<Decimal> indicator) {
        this(bbm, indicator, Decimal.TWO);
    }

    public BollingerBandsLowerIndicator(BollingerBandsMiddleIndicator bbm, Indicator<Decimal> indicator, Decimal k) {
        super(indicator);
        this.bbm = bbm;
        this.indicator = indicator;
        this.k = k;
    }

    @Override
    protected Decimal calculate(int index) {
        return bbm.getValue(index).minus(indicator.getValue(index).multipliedBy(k));
    }

    /**
     * @return the K multiplier
     */
    public Decimal getK() {
        return k;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "deviation: " + indicator + "series: " + bbm;
    }
}
