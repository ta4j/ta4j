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
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * True range indicator.
 * 真实范围指示器。
 *
 * 真实波幅指标（True Range Indicator）是一种用于衡量金融资产价格波动性的技术指标。它是由威尔斯·威尔德（Welles Wilder）在他的著作《新概念技术分析》中提出的，并被广泛用于技术分析中。
 *
 * 真实波幅指标的计算方法通常基于以下几个值：
 *
 * 1. 当日的最高价和最低价之间的价格范围（High-Low Range）。
 * 2. 前一日收盘价与当日最高价之间的价格范围（Previous Close to Current High Range）。
 * 3. 前一日收盘价与当日最低价之间的价格范围（Previous Close to Current Low Range）。
 *
 * 真实波幅指标的计算公式如下：
 *
 *  TR = max(H - L, |H - C_{p}|, |L - C_{p}|)
 *
 * 其中：
 * - \( TR \) 是真实波幅。
 * - \( H \) 是当日的最高价。
 * - \( L \) 是当日的最低价。
 * - \( C_{p} \) 是前一日的收盘价。
 *
 * 真实波幅指标的值代表了资产价格在一段时间内的波动范围，它可以帮助交易者和投资者更好地理解市场的波动性，并用于确定止损位、波动性的测量以及其他交易决策。
 */
public class TRIndicator extends CachedIndicator<Num> {

    public TRIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        Num ts = getBarSeries().getBar(index).getHighPrice().minus(getBarSeries().getBar(index).getLowPrice());
        Num ys = index == 0 ? numOf(0)
                : getBarSeries().getBar(index).getHighPrice().minus(getBarSeries().getBar(index - 1).getClosePrice());
        Num yst = index == 0 ? numOf(0)
                : getBarSeries().getBar(index - 1).getClosePrice().minus(getBarSeries().getBar(index).getLowPrice());
        return ts.abs().max(ys.abs()).max(yst.abs());
    }
}
