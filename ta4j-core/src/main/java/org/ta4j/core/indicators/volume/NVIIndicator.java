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

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Negative Volume Index (NVI) indicator.
 * 负体积指数 (NVI) 指标。
 *
 * 负成交量指标（Negative Volume Index，NVI）是一种技术指标，旨在衡量在成交量下降的情况下，资产价格的变化情况。它是由保罗·奥林格（Paul Dysart）开发的，基于成交量下降时可能发生的分布情况。
 *
 * NVI指标的计算过程如下：
 *
 * 1. 初始NV指数值为一个基础值，通常为1000或者1。
 * 2. 对于每个交易周期，比较当日的成交量与前一日的成交量：
 *    - 如果当日的成交量低于前一日的成交量，则认为当日的价格变化受到了分布的影响，NV指数增加。
 *    - 如果当日的成交量高于或等于前一日的成交量，则认为当日的价格变化受到了积极的支持，NV指数不变。
 *
 * NVI指数的变化趋势主要反映了在成交量下降时，资产价格的变化情况。较高的NVI值表示资产价格可能处于分布阶段，而较低的NVI值则表示价格可能处于积极支持的阶段。
 *
 * NVI指标的主要用途是辅助投资者识别潜在的价格趋势转折点。当NVI指数开始下跌时，可能暗示着市场上出现了分布压力，可能会导致价格的下跌。因此，一些交易者可能会将NVI指标与其他技术指标结合使用，以确认价格趋势的转折点并做出相应的交易决策。
 *
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:negative_volume_inde">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:negative_volume_inde</a>
 * @see <a href=
 *      "http://www.metastock.com/Customer/Resources/TAAZ/Default.aspx?p=75">
 *      http://www.metastock.com/Customer/Resources/TAAZ/Default.aspx?p=75</a>
 * @see <a href="http://www.investopedia.com/terms/n/nvi.asp">
 *      http://www.investopedia.com/terms/n/nvi.asp</a>
 */
public class NVIIndicator extends RecursiveCachedIndicator<Num> {

    public NVIIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        if (index == 0) {
            return numOf(1000);
        }

        Bar currentBar = getBarSeries().getBar(index);
        Bar previousBar = getBarSeries().getBar(index - 1);
        Num previousValue = getValue(index - 1);

        if (currentBar.getVolume().isLessThan(previousBar.getVolume())) {
            Num currentPrice = currentBar.getClosePrice();
            Num previousPrice = previousBar.getClosePrice();
            Num priceChangeRatio = currentPrice.minus(previousPrice).dividedBy(previousPrice);
            return previousValue.plus(priceChangeRatio.multipliedBy(previousValue));
        }
        return previousValue;
    }

}