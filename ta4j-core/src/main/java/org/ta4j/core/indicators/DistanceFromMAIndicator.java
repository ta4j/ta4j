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

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Distance From Moving Average (close - MA)/MA
 * 与移动平均线的距离（收盘价 - MA）/MA
 *
 * "距离移动平均线的距离"是一种常见的技术指标，用于衡量资产价格与其移动平均线之间的偏离程度。通常表示为（收盘价 - 移动平均线）/ 移动平均线。
 *
 * 这个指标可以用来评估价格相对于其长期趋势的偏离程度。具体而言，当资产的价格高于其移动平均线时，指标值为正，表明价格相对于移动平均线偏高；相反，当资产的价格低于其移动平均线时，指标值为负，表明价格相对于移动平均线偏低。
 *
 * 这个指标的一般用途包括：
 * - **判断超买超卖情况：** 当指标值大于0时，可能表示资产被过度买入；当指标值小于0时，可能表示资产被过度卖出。
 * - **确认趋势方向：** 当指标值处于正值区间时，可能暗示着价格趋势向上；当指标值处于负值区间时，可能暗示着价格趋势向下。
 * - **确认价格反转：** 当指标值远离0时，可能暗示着价格反转的可能性增加。
 *
 * 通常情况下，分析师和交易者会结合其他技术指标和价格模式来使用距离移动平均线的距离指标，以辅助他们的交易决策。
 *
 *
 * @see <a href=
 *      "https://school.stockcharts.com/doku.php?id=technical_indicators:distance_from_ma">
 *      https://school.stockcharts.com/doku.php?id=technical_indicators:distance_from_ma
 *      </a>
 */
public class DistanceFromMAIndicator extends CachedIndicator<Num> {
    private static final Set<Class> supportedMovingAverages = new HashSet<>(
            Arrays.asList(EMAIndicator.class, DoubleEMAIndicator.class, TripleEMAIndicator.class, SMAIndicator.class,
                    WMAIndicator.class, ZLEMAIndicator.class, HMAIndicator.class, KAMAIndicator.class,
                    LWMAIndicator.class, AbstractEMAIndicator.class, MMAIndicator.class));
    private final CachedIndicator movingAverage;

    /**
     *
     * @param series        the bar series {@link BarSeries}.
     *                      酒吧系列{@link BarSeries}。
     * @param movingAverage the moving average.
     *                      移动平均线。
     */
    public DistanceFromMAIndicator(BarSeries series, CachedIndicator movingAverage) {
        super(series);
        if (!(supportedMovingAverages.contains(movingAverage.getClass()))) {
            throw new IllegalArgumentException(
                    "Passed indicator must be a moving average based indicator. 通过的指标必须是基于移动平均线的指标。 " + movingAverage.toString());
        }
        this.movingAverage = movingAverage;
    }

    @Override
    protected Num calculate(int index) {
        Bar currentBar = getBarSeries().getBar(index);
        Num closePrice = currentBar.getClosePrice();
        Num maValue = (Num) movingAverage.getValue(index);
        return (closePrice.minus(maValue)).dividedBy(maValue);
    }
}
