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
import org.ta4j.core.indicators.helpers.DifferenceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Mass index indicator.
 * 质量指数指标。
 *
 * 质量指数（Mass Index）是一种技术指标，用于识别价格波动的变化，并提供可能的市场转折点。它是由唐纳德·道林格（Donald Dorsey）在1970年代初期开发的，旨在测量价格波动的幅度和速度。
 *
 * 质量指数的计算过程相对复杂，但基本思想是基于价格范围的指数移动平均线（EMA）的比率。下面是质量指数的主要计算步骤：
 *
 * 1. 计算价格范围（Price Range）：通常是当日的最高价与最低价之差。
 * 2. 计算价格范围的指数加权移动平均线（EMA）：通常使用9个周期的指数移动平均线。
 * 3. 计算价格范围的指数加权移动平均线的比率（EMA比率）：将当日价格范围的指数加权移动平均线除以指定周期的指数加权移动平均线。
 * 4. 计算质量指数值：将连续多个周期的EMA比率相加，得到质量指数的值。
 *
 * 质量指数的数值通常在较高水平时，表明市场波动性增加，可能预示着市场转折点的出现。当质量指数超过一定的阈值（通常为27）时，交易者可能会开始关注市场的潜在转折点。
 *
 * 交易者可以根据质量指数的数值变化来识别市场的潜在转折点，并据此制定买卖策略。然而，质量指数也具有一定的局限性，例如它可能会产生过多的虚假信号，因此建议将其与其他技术指标和价格模式结合使用，以增强分析的准确性。
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:mass_index">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:mass_index</a>
 */
public class MassIndexIndicator extends CachedIndicator<Num> {

    private final EMAIndicator singleEma;
    private final EMAIndicator doubleEma;
    private final int barCount;

    /**
     * Constructor.
     *
     * @param series      the bar series
     *                    酒吧系列
     * @param emaBarCount the time frame for EMAs (usually 9)
     *                    EMA 的时间范围（通常为 9）
     * @param barCount    the time frame
     *                    时间范围
     */
    public MassIndexIndicator(BarSeries series, int emaBarCount, int barCount) {
        super(series);
        Indicator<Num> highLowDifferential = new DifferenceIndicator(new HighPriceIndicator(series),
                new LowPriceIndicator(series));
        singleEma = new EMAIndicator(highLowDifferential, emaBarCount);
        // Not the same formula as DoubleEMAIndicator
        // 与 DoubleEMAIndicator 的公式不同
        doubleEma = new EMAIndicator(singleEma, emaBarCount);
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        final int startIndex = Math.max(0, index - barCount + 1);
        Num massIndex = numOf(0);
        for (int i = startIndex; i <= index; i++) {
            Num emaRatio = singleEma.getValue(i).dividedBy(doubleEma.getValue(i));
            massIndex = massIndex.plus(emaRatio);
        }
        return massIndex;
    }
}
