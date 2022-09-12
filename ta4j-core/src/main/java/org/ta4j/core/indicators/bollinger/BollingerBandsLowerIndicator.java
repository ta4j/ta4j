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
package org.ta4j.core.indicators.bollinger;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Buy - Occurs when the price line crosses from below to above the Lower Bollinger Band. Sell - Occurs when the price line crosses from above to below  the Upper Bollinger Band.
 * * 买入 - 当价格线从下布林带下方穿越到上方布林带时发生。 卖出 - 当价格线从上布林带上方向下穿过时发生。
 * 
 */
public class BollingerBandsLowerIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final BollingerBandsMiddleIndicator bbm;
    private final Num k;

    /**
     * Constructor. Defaults k value to 2.
     * * 构造函数。 默认 k 值为 2。
     * 
     * @param bbm       the middle band Indicator. Typically an SMAIndicator is  used.
     *                  中带指标。 通常使用 SMAIndicator。
     * @param indicator the deviation above and below the middle, factored by k.
     *                  中间上方和下方的偏差，以 k 为因数。
     *                  Typically a StandardDeviationIndicator is used.
     *                  通常使用 StandardDeviationIndicator。
     */
    public BollingerBandsLowerIndicator(BollingerBandsMiddleIndicator bbm, Indicator<Num> indicator) {
        this(bbm, indicator, bbm.getBarSeries().numOf(2));
    }

    /**
     * Constructor.
     * 
     * @param bbm       the middle band Indicator. Typically an SMAIndicator is   used.
     *                  中带指标。 通常使用 SMAIndicator。
     * @param indicator the deviation above and below the middle, factored by k.
     *                  中间上方和下方的偏差，以 k 为因数。
     *                  Typically a StandardDeviationIndicator is used.
     *                  通常使用 StandardDeviationIndicator。
     * @param k         the scaling factor to multiply the deviation by. Typically  2.
     *                  将偏差乘以的比例因子。 通常为 2。
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

    /**
     * @return the K multiplier
     * * @return K 乘数
     */
    public Num getK() {
        return k;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "k: " + k + "deviation: " + indicator + "series: " + bbm;
    }
}
