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

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.num.Num;

/**
 * Aroon up indicator.
 * * 阿隆向上指标。
 *
 * Aroon上升指标（Aroon Up）是一种技术指标，用于衡量资产价格在一定时间内创出新高点后经过的期间数。它是由Tushar Chande开发的，旨在帮助交易者确认价格趋势的强度和可能的转折点。
 *
 * Aroon Up指标的计算方法如下：
 *
 * 1. 首先，确定一个固定的时间周期（通常是14个交易周期）。
 *
 * 2. 在给定的时间周期内，找到最近的价格创新高点，并计算自该新高点出现以来经过的周期数。
 *
 * 3. 计算Aroon Up值：
 *     Aroon Up  =  周期  - 自价格出现新高后的周期数 / 周期 * 100
 *
 * Aroon Up指标的数值范围从0到100，数值越高，表示价格创新高点后经过的时间越短，即价格上升趋势的强度越高。
 *
 * 交易者通常将Aroon Up指标与Aroon Down指标结合使用。当Aroon Up高于Aroon Down时，可能暗示着价格处于上升趋势；反之，当Aroon Down高于Aroon Up时，则可能暗示着价格处于下降趋势。此外，Aroon指标还可用于识别价格趋势的转折点和确认交易信号。
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:aroon">chart_school:technical_indicators:aroon</a>
 */
public class AroonUpIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final HighestValueIndicator highestHighPriceIndicator;
    private final Indicator<Num> highPriceIndicator;
    private final Num hundred;

    /**
     * Constructor.
     *
     * @param highPriceIndicator the indicator for the high price (default  {@link HighPriceIndicator})
     *                           * @param highPriceIndicator 高价指标（默认 {@link HighPriceIndicator}）
     * @param barCount           the time frame 時間範圍
     */
    public AroonUpIndicator(Indicator<Num> highPriceIndicator, int barCount) {
        super(highPriceIndicator);
        this.barCount = barCount;
        this.highPriceIndicator = highPriceIndicator;
        this.hundred = numOf(100);
        // + 1 needed for last possible iteration in loop
        // + 1 循环中最后一次可能的迭代需要
        this.highestHighPriceIndicator = new HighestValueIndicator(highPriceIndicator, barCount + 1);
    }

    /**
     * Default Constructor that is using the high price
     * 使用高价的默认构造函数
     *
     * @param series   the bar series
     *                 酒吧系列
     * @param barCount the time frame
     *                 时间范围
     */
    public AroonUpIndicator(BarSeries series, int barCount) {
        this(new HighPriceIndicator(series), barCount);
    }

    @Override
    protected Num calculate(int index) {
        if (getBarSeries().getBar(index).getHighPrice().isNaN())
            return NaN;

        // Getting the number of bars since the highest close price
        // 获取自最高收盘价以来的柱数
        int endIndex = Math.max(0, index - barCount);
        int nbBars = 0;
        for (int i = index; i > endIndex; i--) {
            if (highPriceIndicator.getValue(i).isEqual(highestHighPriceIndicator.getValue(index))) {
                break;
            }
            nbBars++;
        }

        return numOf(barCount - nbBars).dividedBy(numOf(barCount)).multipliedBy(hundred);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
