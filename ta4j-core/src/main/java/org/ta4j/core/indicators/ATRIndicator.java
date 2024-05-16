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
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.helpers.TRIndicator;
import org.ta4j.core.num.Num;

/**
 * Average true range indicator.
 * *平均真实范围指标。
 *
 * 真实波动幅度平均指标（Average True Range，ATR）是一种技术指标，用于衡量资产价格的波动性。它是由威尔斯·怀尔德（Welles Wilder）开发的，旨在帮助交易者确认资产价格的波动性和可能的趋势变化。
 *
 * ATR指标的计算基于资产的价格波动范围，它不考虑价格方向，只考虑价格的波动幅度。具体计算步骤如下：
 *
 * 1. 首先，计算当前周期的真实波动幅度（True Range，TR）。
 *    - TR的计算通常基于以下三个值之一：
 *      - 当前周期的最高价和最低价之间的价差。
 *      - 当前周期的最高价和前一周期的收盘价之间的价差。
 *      - 当前周期的最低价和前一周期的收盘价之间的价差。
 *    - TR取这三个值中的最大值。
 *
 * 2. 计算真实波动幅度的移动平均值，即平均真实波动幅度（Average True Range）。
 *    - ATR通常是使用一段固定的时间周期进行计算，例如14个交易周期。
 *
 * ATR指标的值表示资产价格的平均波动幅度。通常情况下，ATR值越高，表示价格波动越大，市场越具有波动性；而ATR值越低，则表示价格波动较小，市场越平稳。
 *
 * 交易者可以利用ATR指标来确认价格波动性的水平，并结合其他技术指标一起使用，以辅助他们的交易决策。例如，在制定止损和止盈策略时，可以使用ATR指标来确定合适的止损和止盈距离；在确认趋势的强度时，可以使用ATR指标来判断价格波动是否符合预期。
 *
 */

public class ATRIndicator extends CachedIndicator<Num> {

    private final MMAIndicator averageTrueRangeIndicator;

    public ATRIndicator(BarSeries series, int barCount) {
        super(series);
        this.averageTrueRangeIndicator = new MMAIndicator(new TRIndicator(series), barCount);
    }

    @Override
    protected Num calculate(int index) {
        return averageTrueRangeIndicator.getValue(index);
    }
}
