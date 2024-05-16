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
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;

/**
 * On-balance volume indicator.
 * 累积/派发线指标（On-balance Volume，OBV）是一种技术指标，用于衡量资产的买卖压力和资金流向。它基于成交量的变化来判断价格走势的可能性，是由乔·格兰舍（Joe Granville）于1963年开发的。
 *
 * OBV指标的计算方法如下：
 *
 * 1. 初始OBV值为0。
 * 2. 对于每个交易周期：
 *    - 如果当日的收盘价高于前一日的收盘价，则将当日的成交量加到OBV值上。
 *    - 如果当日的收盘价低于前一日的收盘价，则将当日的成交量从OBV值中减去。
 *    - 如果当日的收盘价等于前一日的收盘价，则OBV值不变。
 *
 * 通过这种方法，OBV指标将成交量的正负变化与价格的变动关联起来，以判断买卖压力的强弱。
 *
 * OBV指标的变化趋势通常与价格趋势相一致。如果价格趋势上涨，而OBV指标也上升，则表示买入力量强劲，价格上涨的可能性较高。相反，如果价格趋势下跌，而OBV指标也下降，则表示卖出力量较强，价格下跌的可能性较高。
 *
 * 交易者可以利用OBV指标来确认价格趋势的持续性和确认买卖信号。例如，当价格形成新高点而OBV没有形成新高时，可能暗示着价格的上涨动力正在减弱，可能发生价格的反转。因此，OBV指标常被用作辅助交易决策的工具之一。
 *
 * 平衡成交量指标。
 *
 * @see <a href="https://www.investopedia.com/terms/o/onbalancevolume.asp">
 *      https://www.investopedia.com/terms/o/onbalancevolume.asp</a>
 */
public class OnBalanceVolumeIndicator extends RecursiveCachedIndicator<Num> {

    public OnBalanceVolumeIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        if (index == 0) {
            return numOf(0);
        }
        final Num prevClose = getBarSeries().getBar(index - 1).getClosePrice();
        final Num currentClose = getBarSeries().getBar(index).getClosePrice();

        final Num obvPrev = getValue(index - 1);
        if (prevClose.isGreaterThan(currentClose)) {
            return obvPrev.minus(getBarSeries().getBar(index).getVolume());
        } else if (prevClose.isLessThan(currentClose)) {
            return obvPrev.plus(getBarSeries().getBar(index).getVolume());
        } else {
            return obvPrev;
        }
    }
}
