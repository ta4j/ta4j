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
import org.ta4j.core.indicators.helpers.CloseLocationValueIndicator;
import org.ta4j.core.num.Num;

/**
 * Accumulation-distribution indicator.
 * 累积分布指标。
 *
 * 累积分布线（Accumulation Distribution Line）是一种技术指标，用于衡量资产价格的累积流入和流出情况。它基于成交量和价格之间的关系，旨在揭示资金流动的方向和力量。
 *
 * 累积分布线的计算过程如下：
 *
 * 1. 计算每个交易周期的成交量加权平均价格（Typical Price）：
 *     TP =  ( 高 + 低 + 收 ) / 3
 *
 * 2. 计算每个交易周期的成交量流入（Flow）：
 *    - 如果当日的典型价格高于前一日的典型价格，则流入为当日的成交量。
 *    - 如果当日的典型价格低于前一日的典型价格，则流入为负值，其绝对值为当日的成交量。
 *
 * 3. 计算累积分布线值：
 *    - 第一个周期的累积分布线值等于第一个周期的成交量流入值。
 *    - 后续周期的累积分布线值等于前一个周期的累积分布线值加上当期的成交量流入值。
 *
 * 累积分布线的数值越高，表示资金流入越多，价格上涨的可能性增加；而数值越低，则表示资金流出越多，价格可能下跌。通过观察累积分布线的变化，交易者可以获取有关资金流动情况和价格走势的信息，辅助他们做出交易决策。
 *
 * 累积分布线常与价格走势图一起使用，通过观察累积分布线与价格趋势的背离情况，识别潜在的价格反转或趋势继续的信号。
 *
 */
public class AccumulationDistributionIndicator extends RecursiveCachedIndicator<Num> {

    private final CloseLocationValueIndicator clvIndicator;

    public AccumulationDistributionIndicator(BarSeries series) {
        super(series);
        this.clvIndicator = new CloseLocationValueIndicator(series);
    }

    @Override
    protected Num calculate(int index) {
        if (index == 0) {
            return numOf(0);
        }

        // Calculating the money flow multiplier
        // 计算资金流量乘数
        Num moneyFlowMultiplier = clvIndicator.getValue(index);

        // Calculating the money flow volume
        // 计算资金流量
        Num moneyFlowVolume = moneyFlowMultiplier.multipliedBy(getBarSeries().getBar(index).getVolume());

        return moneyFlowVolume.plus(getValue(index - 1));
    }
}
