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
import org.ta4j.core.num.Num;

/**
 * Rate of change (ROCIndicator) indicator. Aka. Momentum
 * 变化率 (ROCIndicator) 指标。 阿卡。 势头
 *
 * The ROCIndicator calculation compares the current value with the value "n" periods ago.
 * ROCIndicator 计算将当前值与“n”个周期前的值进行比较。
 *
 *
 * 变动率指标（Rate of Change，ROC），又称动量指标，是一种常用的技术指标，用于衡量价格变动的速度和幅度。它显示了当前价格与一段时间前（通常是N个周期）的价格之间的百分比变化。
 *
 * ROC指标的计算公式如下：
 *
 *  ROC  =  (  当前价格  - N个周期前的价格 / N个周期前的价格 ) * 100
 *
 * 其中：
 * - 当前价格是最新的价格数据。
 * - N是选择的周期数。
 *
 * ROC指标的数值可以为正数或负数，表示价格相对于N个周期前的价格的百分比变化。正数表示价格上升，负数表示价格下降。
 *
 * 交易者通常使用ROC指标来确认价格的趋势和判断市场的超买超卖情况。例如，当ROC指标为正且数值较大时，可能暗示价格上升趋势较为强劲；相反，当ROC指标为负且数值较大时，可能暗示价格下降趋势较为强劲。当ROC指标的数值超过一定阈值时，可能意味着市场处于超买或超卖状态。
 *
 * 总的来说，ROC指标是一种用于衡量价格变动速度和幅度的技术指标，可以帮助交易者更好地理解市场的价格动态和趋势变化。
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/p/pricerateofchange.asp">https://www.investopedia.com/terms/p/pricerateofchange.asp</a>
 */
public class ROCIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;

    /**
     * Constructor.
     *
     * @param indicator the indicator
     *                  指标
     *
     * @param barCount  the time frame
     *                  时间范围
     */
    public ROCIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        int nIndex = Math.max(index - barCount, 0);
        Num nPeriodsAgoValue = indicator.getValue(nIndex);
        Num currentValue = indicator.getValue(index);
        return currentValue.minus(nPeriodsAgoValue).dividedBy(nPeriodsAgoValue).multipliedBy(numOf(100));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
