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
 * Chande Momentum Oscillator indicator.
 * Chande 动量振荡器指标。
 *
 * 香农动量振荡器（Chande Momentum Oscillator，CMO）是一种技术指标，用于衡量资产的价格动量，从而帮助交易者确认价格的趋势方向和可能的超买或超卖情况。它是由特里尔·张伯伦（Tushar Chande）开发的。
 *
 * 香农动量振荡器的计算过程如下：
 *
 * 1. 首先，计算过去一定时间周期内的价格变化量（Change）。
 *    - 可以选择任意固定的时间周期，例如20个交易周期。
 *
 * 2. 然后，将过去一定时间周期内的正价格变化量（上涨幅度）的总和与过去一定时间周期内的负价格变化量（下跌幅度）的总和相加，得到总变化量（Sum of Changes）。
 *
 * 3. 计算过去一定时间周期内的正价格变化量（上涨幅度）的绝对值的总和与过去一定时间周期内的负价格变化量（下跌幅度）的绝对值的总和相加，得到总变化量的绝对值（Sum of Absolute Changes）。
 *
 * 4. 最后，计算香农动量振荡器的值：
 *     CMO =  ( 总变化量} - 总变化量的绝对值 / 总变化量 + 总变化量的绝对值) * 100
 *
 * 香农动量振荡器的数值范围从-100到+100，其中：
 * - 当CMO值为正时，表示上涨幅度大于下跌幅度，市场可能处于超买状态。
 * - 当CMO值为负时，表示下跌幅度大于上涨幅度，市场可能处于超卖状态。
 *
 * 交易者通常使用香农动量振荡器来确认价格的趋势方向和市场的超买或超卖情况，并据此制定交易策略。例如，当CMO指标显示市场处于超买状态时，可能会考虑卖出；而当CMO指标显示市场处于超卖状态时，可能会考虑买入。
 *
 *
 * @see <a href=
 *      "http://tradingsim.com/blog/chande-momentum-oscillator-cmo-technical-indicator/">
 *      http://tradingsim.com/blog/chande-momentum-oscillator-cmo-technical-indicator/</a>
 * @see <a href=
 *      "http://www.investopedia.com/terms/c/chandemomentumoscillator.asp">
 *      href="http://www.investopedia.com/terms/c/chandemomentumoscillator.asp"</a>
 */
public class CMOIndicator extends CachedIndicator<Num> {

    private final GainIndicator gainIndicator;
    private final LossIndicator lossIndicator;
    private final int barCount;

    /**
     * Constructor.
     *
     * @param indicator a price indicator
     *                  价格指标
     * @param barCount  the time frame
     *                  时间范围
     */
    public CMOIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.gainIndicator = new GainIndicator(indicator);
        this.lossIndicator = new LossIndicator(indicator);
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        Num sumOfGains = numOf(0);
        for (int i = Math.max(1, index - barCount + 1); i <= index; i++) {
            sumOfGains = sumOfGains.plus(gainIndicator.getValue(i));
        }
        Num sumOfLosses = numOf(0);
        for (int i = Math.max(1, index - barCount + 1); i <= index; i++) {
            sumOfLosses = sumOfLosses.plus(lossIndicator.getValue(i));
        }
        return sumOfGains.minus(sumOfLosses).dividedBy(sumOfGains.plus(sumOfLosses)).multipliedBy(numOf(100));
    }
}
