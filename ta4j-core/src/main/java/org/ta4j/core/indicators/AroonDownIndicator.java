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
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.num.Num;

/**
 * Aroon down indicator.
 * 阿隆下跌指标。
 *
 * Aroon下降指标是用于衡量价格下降趋势强度和时间的技术指标。它是由Tushar Chande在他的书《新的技术指标》（New Technical Indicators）中提出的。Aroon指标基于两个主要组成部分：Aroon Up和Aroon Down。
 *
 * Aroon Down指标衡量了在一定时间周期内，价格创下新低点后经过的期间。Aroon Down值的计算公式如下：
 *
 *  Aroon Down  = (周期 - 自价格出现新高后的周期数) / 周期 * 100
 *
 * 通常，周期的设定是比较近期的若干个交易周期。例如，若周期设定为25个交易周期，Aroon Down将计算最近25个交易周期内价格创下新低点后经过的周期数，并以百分比的形式表示。
 *
 * Aroon Down指标的数值范围从0到100，数值越高，表示价格创新低后经过的时间越长，即价格下降趋势的强度越高。
 *
 * 交易者通常将Aroon Down指标与Aroon Up指标结合使用。当Aroon Down高于Aroon Up时，可能暗示着价格处于下降趋势；反之，当Aroon Up高于Aroon Down时，则可能暗示着价格处于上升趋势。此外，Aroon指标还可用于识别价格趋势的转折点和确认交易信号。
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:aroon">chart_school:technical_indicators:aroon</a>
 */
public class AroonDownIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final LowestValueIndicator lowestLowPriceIndicator;
    private final Indicator<Num> lowPriceIndicator;
    private final Num hundred;

    /**
     * Constructor.
     *
     * @param lowPriceIndicator the indicator for the low price (default  {@link LowPriceIndicator})
     *                          低价指标（默认 {@link LowPriceIndicator}）
     * @param barCount          the time frame
     *                          时间范围
     */
    public AroonDownIndicator(Indicator<Num> lowPriceIndicator, int barCount) {
        super(lowPriceIndicator);
        this.barCount = barCount;
        this.lowPriceIndicator = lowPriceIndicator;
        this.hundred = numOf(100);
        // + 1 needed for last possible iteration in loop
        // + 1 循环中最后一次可能的迭代需要
        this.lowestLowPriceIndicator = new LowestValueIndicator(lowPriceIndicator, barCount + 1);
    }

    /**
     * Default Constructor that is using the low price
     * 使用低价的默认构造函数
     *
     * @param series   the bar series
     *                 酒吧系列
     * @param barCount the time frame
     *                 時間範圍
     */
    public AroonDownIndicator(BarSeries series, int barCount) {
        this(new LowPriceIndicator(series), barCount);
    }

    @Override
    protected Num calculate(int index) {
        if (getBarSeries().getBar(index).getLowPrice().isNaN())
            return NaN;

        // Getting the number of bars since the lowest close price
        // 获取自最低收盘价以来的柱数
        int endIndex = Math.max(0, index - barCount);
        int nbBars = 0;
        for (int i = index; i > endIndex; i--) {
            if (lowPriceIndicator.getValue(i).isEqual(lowestLowPriceIndicator.getValue(index))) {
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
