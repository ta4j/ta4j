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
package org.ta4j.core.indicators.bollinger;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Buy - Occurs when the price line crosses from below to above the Lower
 * Bollinger Band.
 *
 * <p>
 * Sell - Occurs when the price line crosses from above to below the Upper
 * Bollinger Band.
 */
public class BollingerBandsLowerIndicator extends CachedIndicator<Num> {

    private final BollingerBandsMiddleIndicator bbm;
    private final Indicator<Num> indicator;
    private final Num k;

    /**
     * Constructor with {@code k} = 2.
     *
     * @param bbm       the middle band Indicator. Typically an {@code SMAIndicator}
     *                  is used.
     * @param indicator the deviation above and below the middle, factored by k.
     *                  Typically a {@code StandardDeviationIndicator} is used.
     */
    public BollingerBandsLowerIndicator(BollingerBandsMiddleIndicator bbm, Indicator<Num> indicator) {
        this(bbm, indicator, bbm.getBarSeries().numFactory().numOf(2));
    }

    /**
     * Constructor.
     *
     * @param bbm       the middle band Indicator. Typically an {@code SMAIndicator}
     *                  is used.
     * @param indicator the deviation above and below the middle, factored by k.
     *                  Typically a {@code StandardDeviationIndicator} is used.
     * @param k         the scaling factor to multiply the deviation by. Typically
     *                  2.
     */
    public BollingerBandsLowerIndicator(BollingerBandsMiddleIndicator bbm, Indicator<Num> indicator, Num k) {
        super(indicator);
        this.bbm = bbm;
        this.indicator = indicator;
        this.k = k;
    }

    @Override
    protected Num calculate(int index) {
        return bbm.getValue(index).minus(indicator.getValue(index).multipliedBy(k));
    }

    @Override
    public int getUnstableBars() {
        return 0;
    }

    /** @return the K multiplier */
    public Num getK() {
        return k;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "k: " + k + "deviation: " + indicator + "series: " + bbm;
    }
}
