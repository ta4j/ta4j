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
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.DifferenceIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.num.Num;

/**
 * The Detrended Price Oscillator (DPO) indicator.
 * 去趋势价格振荡器 (DPO) 指标。
 *
 * The Detrended Price Oscillator (DPO) is an indicator designed to remove trend
  from price and make it easier to identify cycles. DPO does not extend to the
  last date because it is based on a displaced moving average. However,
  alignment with the most recent is not an issue because DPO is not a momentum
  oscillator. Instead, DPO is used to identify cycles highs/lows and estimate
  cycle length.
 去趋势价格震荡指标 (DPO) 是一种旨在消除价格趋势并更容易识别周期的指标。 DPO 不会延续到最后日期，因为它基于移动平均线。然而，与最新的一致不是问题，因为 DPO 不是动量振荡器。相反，DPO 用于识别周期高低点并估计周期长度。
 *
 * In short, DPO(20) equals price 11 days ago less the 20-day SMA.
 * 简而言之，DPO(20) 等于 11 天前的价格减去 20 天的 SMA。
 *
 *
 *
 * 去趋势价格振荡器（Detrended Price Oscillator，DPO）是一种用于衡量资产价格相对于其移动平均值的偏离程度的技术指标。与许多其他指标不同，DPO不考虑当前价格相对于移动平均线的位置，而是专注于当前价格相对于过去一段时间内的平均价格的偏离程度。
 *
 * DPO的计算步骤如下：
 *
 * 1. 选择一个固定的时间周期（例如20个交易周期）。
 * 2. 计算该时间周期内的移动平均值（MA）。
 * 3. 从过去一段时间内的中间点（通常是指定时间周期的一半）开始，计算当前价格与该中间点的价格之间的差值。
 * 4. 将这些差值绘制成图表，即为DPO指标的值。
 *
 * DPO指标的主要作用是帮助交易者识别资产价格的周期性波动，而不受长期趋势的影响。通过观察DPO的数值变化，交易者可以更好地了解价格在短期内的波动模式，并据此制定交易策略。
 *
 * 一般而言，当DPO的数值为正时，表示当前价格高于过去一段时间内的平均价格，可能暗示着价格处于上升趋势；相反，当DPO的数值为负时，表示当前价格低于过去一段时间内的平均价格，可能暗示着价格处于下降趋势。
 *
 * 交易者通常会将DPO与其他技术指标结合使用，例如移动平均线、趋势线等，以辅助他们的交易决策。
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:detrended_price_osci">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:detrended_price_osci</a>
 */
public class DPOIndicator extends CachedIndicator<Num> {

    private final DifferenceIndicator indicatorMinusPreviousSMAIndicator;
    private final String name;

    /**
     * Constructor.
     *
     * @param series   the series  该系列
     * @param barCount the time frame  时间范围
     */
    public DPOIndicator(BarSeries series, int barCount) {
        this(new ClosePriceIndicator(series), barCount);
    }

    /**
     * Constructor.
     *
     * @param price    the price  价格
     * @param barCount the time frame 時間範圍
     */
    public DPOIndicator(Indicator<Num> price, int barCount) {
        super(price);
        int timeFrame = barCount / 2 + 1;
        final SMAIndicator simpleMovingAverage = new SMAIndicator(price, barCount);
        final PreviousValueIndicator previousSimpleMovingAverage = new PreviousValueIndicator(simpleMovingAverage,
                timeFrame);

        this.indicatorMinusPreviousSMAIndicator = new DifferenceIndicator(price, previousSimpleMovingAverage);
        this.name = String.format("%s barCount: %s", getClass().getSimpleName(), barCount);
    }

    @Override
    protected Num calculate(int index) {
        return indicatorMinusPreviousSMAIndicator.getValue(index);
    }

    @Override
    public String toString() {
        return name;
    }
}
