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
package org.ta4j.core.indicators.volume;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.Num;

/**
 * Intraday Intensity Index
 * 日内强度指数
 *
 * 日内强度指数（Intraday Intensity Index，III）是一种技术指标，用于衡量市场内的买卖力量，以及市场中的资金流入和流出情况。它是由大卫·布莱克（David Bostian）开发的，基于价格和成交量的关系。
 *
 * 日内强度指数的计算过程如下：
 *
 * 1. 计算典型价格（Typical Price）：
 *     TP = ( 高 + 低 + 收 ) / 3
 *
 * 2. 计算每个交易周期的成交量加权的典型价格（Typical Price Volume，TPV）：
 *     TPV = TP * 成交量
 *
 * 3. 计算日内强度（Intraday Intensity，II）：
 *    - 如果当日的典型价格高于前一日的典型价格，则日内强度为当日的典型价格成交量。
 *    - 如果当日的典型价格低于前一日的典型价格，则日内强度为负值，其绝对值为当日的典型价格成交量。
 *
 * 4. 计算日内强度指数（Intraday Intensity Index，III）：
 *    - 日内强度指数等于II值的n期移动平均，通常使用简单移动平均或指数移动平均。
 *
 * 日内强度指数的数值范围在正负之间，与III值的正负方向相关，正值表示市场上的资金流入，负值表示市场上的资金流出。 III的变化趋势可用于帮助交易者识别买卖信号和市场趋势的转折点。
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/i/intradayintensityindex.asp">https://www.investopedia.com/terms/i/intradayintensityindex.asp</a>
 */
public class IIIIndicator extends CachedIndicator<Num> {

    private final ClosePriceIndicator closePriceIndicator;
    private final HighPriceIndicator highPriceIndicator;
    private final LowPriceIndicator lowPriceIndicator;
    private final VolumeIndicator volumeIndicator;
    private final Num two;

    public IIIIndicator(BarSeries series) {
        super(series);
        this.closePriceIndicator = new ClosePriceIndicator(series);
        this.highPriceIndicator = new HighPriceIndicator(series);
        this.lowPriceIndicator = new LowPriceIndicator(series);
        this.volumeIndicator = new VolumeIndicator(series);
        this.two = numOf(2);
    }

    @Override
    protected Num calculate(int index) {
        if (index == getBarSeries().getBeginIndex()) {
            return numOf(0);
        }
        final Num doubledClosePrice = two.multipliedBy(closePriceIndicator.getValue(index));
        final Num high = highPriceIndicator.getValue(index);
        final Num low = lowPriceIndicator.getValue(index);
        final Num highMinusLow = high.minus(low);
        final Num highPlusLow = high.plus(low);

        return doubledClosePrice.minus(highPlusLow)
                .dividedBy(highMinusLow.multipliedBy(volumeIndicator.getValue(index)));
    }
}
