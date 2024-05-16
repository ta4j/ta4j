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

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.GainIndicator;
import org.ta4j.core.indicators.helpers.LossIndicator;
import org.ta4j.core.num.Num;

/**
 * Relative strength index indicator.
 * 相对强弱指数指标。
 *
 * Computed using original Welles Wilder formula.
 * 使用原始 Welles Wilder 公式计算。
 *
 *
 * 相对强弱指标（Relative Strength Index，RSI）是一种广泛使用的技术指标，用于衡量价格变动的速度和幅度，并据此提供超买和超卖信号。
 *
 * RSI指标的计算基于一段时间内价格的平均收盘涨跌幅度。通常情况下，RSI的计算过程可以分为以下几个步骤：
 *
 * 1. 计算每个交易日的价格变动幅度，即当日收盘价与前一个交易日收盘价之间的差值。
 * 2. 将正的价格变动幅度（涨幅）与负的价格变动幅度（跌幅）分别求平均，通常采用指数加权移动平均线（EMA）。
 * 3. 计算相对强弱指数（RSI）：将涨幅的平均值除以跌幅的平均值，并将结果转化为百分比形式。
 *
 * RSI指标的数值通常在0到100之间波动。一般来说，当RSI超过70时，表示市场处于超买状态，可能会出现价格下跌的信号；当RSI低于30时，表示市场处于超卖状态，可能会出现价格上涨的信号。
 *
 * 交易者通常使用RSI指标来确认价格趋势的可靠性、识别超买和超卖区域，并制定买卖信号。例如，当RSI从超买区域跌入超卖区域时，可能暗示价格即将反转，为买入信号；相反，当RSI从超卖区域上升至超买区域时，可能暗示价格即将下跌，为卖出信号。
 *
 * 总的来说，RSI指标是一种用于衡量价格变动速度和幅度，并提供超买和超卖信号的常用技术指标，但仍建议将其与其他技术指标和价格模式结合使用，以增强交易决策的准确性。
 *
 */
public class RSIIndicator extends CachedIndicator<Num> {

    private final MMAIndicator averageGainIndicator;
    private final MMAIndicator averageLossIndicator;

    public RSIIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.averageGainIndicator = new MMAIndicator(new GainIndicator(indicator), barCount);
        this.averageLossIndicator = new MMAIndicator(new LossIndicator(indicator), barCount);
    }

    @Override
    protected Num calculate(int index) {
        // compute relative strength
        // 计算相对强度
        Num averageGain = averageGainIndicator.getValue(index);
        Num averageLoss = averageLossIndicator.getValue(index);
        if (averageLoss.isZero()) {
            if (averageGain.isZero()) {
                return numOf(0);
            } else {
                return numOf(100);
            }
        }
        Num relativeStrength = averageGain.dividedBy(averageLoss);
        // compute relative strength index
        // 计算相对强度指数
        return numOf(100).minus(numOf(100).dividedBy(numOf(1).plus(relativeStrength)));
    }
}
