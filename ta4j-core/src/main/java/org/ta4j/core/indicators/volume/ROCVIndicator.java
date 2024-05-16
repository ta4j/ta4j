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
import org.ta4j.core.num.Num;

/**
 * Rate of change of volume (ROCVIndicator) indicator. Aka. Momentum of Volume
 * 成交量变化率 （ROCVIndicator） 指标。即。成交量动量
 *
 * 成交量变化率指标（Rate of Change of Volume，ROCV）也被称为成交量动量指标（Momentum of Volume），它是一种衡量成交量变化速度的技术指标。ROCV指标可以帮助交易者分析成交量的增长或减少速度，以辅助他们对市场趋势和价格走势的判断。
 *
 * ROCV指标的计算方法如下：
 *
 * 1. 首先，选择一个时间段（通常是一定数量的交易周期）。
 *
 * 2. 然后，计算当前时刻的成交量与该时间段之前的成交量之间的变化率。计算公式如下：
 *
 *  ROCV =  V_t - V_{t-n} / V_{t-n} * 100
 *
 * 其中：
 * - \( V_t \) 是当前时刻的成交量。
 * - \( V_{t-n} \) 是 \( n \) 个交易周期前的成交量。
 * - \( n \) 是所选时间段的长度。
 *
 * ROCV指标通常以百分比的形式呈现。如果ROCV为正值，表示当前时刻的成交量高于 \( n \) 个交易周期前的成交量，成交量正在增加；如果ROCV为负值，则表示当前时刻的成交量低于 \( n \) 个交易周期前的成交量，成交量正在减少。
 *
 * 交易者可以利用ROCV指标来识别成交量的加速或减速情况，进而辅助他们对市场趋势的判断。例如，当价格上涨而ROCV也处于上升趋势时，可能表明市场上涨的力量正在增强，进一步支持价格上涨的可能性。相反，如果价格上涨而ROCV下降，可能意味着市场上涨的动力正在减弱，可能发生价格反转。
 *
 * The ROCVIndicator calculation compares the current volume with the volume "n" periods ago.
 * * ROCVIndicator 计算将当前交易量与“n”个周期前的交易量进行比较。
 */
public class ROCVIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final Num hundred;

    /**
     * Constructor.
     *
     * @param series   the bar series
     *                 酒吧系列
     * @param barCount the time frame
     *                 时间范围
     */
    public ROCVIndicator(BarSeries series, int barCount) {
        super(series);
        this.barCount = barCount;
        this.hundred = numOf(100);
    }

    @Override
    protected Num calculate(int index) {
        int nIndex = Math.max(index - barCount, 0);
        Num nPeriodsAgoValue = getBarSeries().getBar(nIndex).getVolume();
        Num currentValue = getBarSeries().getBar(index).getVolume();
        return currentValue.minus(nPeriodsAgoValue).dividedBy(nPeriodsAgoValue).multipliedBy(hundred);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
