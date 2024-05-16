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
 * Positive Volume Index (PVI) indicator.
 * * 正成交量指数 (PVI) 指标。
 *
 * 正成交量指标（Positive Volume Index，PVI）是一种技术指标，用于衡量在成交量上升的情况下，资产价格的变化情况。它是由保罗·奥林格（Paul Dysart）开发的，旨在帮助识别市场中的买入压力和价格上涨的可能性。
 *
 * PVI指标的计算过程如下：
 *
 * 1. 初始PVI值为一个基础值，通常为100或者1000。
 * 2. 对于每个交易周期，比较当日的成交量与前一日的成交量：
 *    - 如果当日的成交量高于前一日的成交量，则认为当日的价格变化受到了积极支持，PVI指数增加。
 *    - 如果当日的成交量低于或等于前一日的成交量，则PVI指数不变。
 *
 * PVI指数的变化趋势主要反映了在成交量上升时，资产价格的变化情况。较高的PVI值表示资产价格可能处于积极支持的阶段，而较低的PVI值则表示价格可能处于分布的阶段。
 *
 * PVI指标的主要用途是辅助投资者识别潜在的价格趋势转折点。当PVI指数开始上涨时，可能暗示着市场上出现了积极支持，可能会导致价格的上涨。因此，一些交易者可能会将PVI指标与其他技术指标结合使用，以确认价格趋势的转折点并做出相应的交易决策。
 *
 *
 * @see <a href=
 *      "http://www.metastock.com/Customer/Resources/TAAZ/Default.aspx?p=92">
 *      http://www.metastock.com/Customer/Resources/TAAZ/Default.aspx?p=92</a>
 * @see <a href="http://www.investopedia.com/terms/p/pvi.asp">
 *      http://www.investopedia.com/terms/p/pvi.asp</a>
 */
public class PVIIndicator extends RecursiveCachedIndicator<Num> {

    public PVIIndicator(BarSeries series) {
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

        if (currentBar.getVolume().isGreaterThan(previousBar.getVolume())) {
            Num currentPrice = currentBar.getClosePrice();
            Num previousPrice = previousBar.getClosePrice();
            Num priceChangeRatio = currentPrice.minus(previousPrice).dividedBy(previousPrice);
            return previousValue.plus(priceChangeRatio.multipliedBy(previousValue));
        }
        return previousValue;
    }

}