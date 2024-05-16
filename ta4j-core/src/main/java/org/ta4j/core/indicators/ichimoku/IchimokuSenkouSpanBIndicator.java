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
package org.ta4j.core.indicators.ichimoku;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Ichimoku clouds: Senkou Span B (Leading Span B) indicator
 * Ichimoku 云： Senkou Span B （Leading Span B） 指标
 *
 * Senkou Span B（先行线B）是一目均衡云指标的一个重要组成部分，用于显示将来的支撑和阻力水平。与 Senkou Span A 不同，Senkou Span B 是在一定时间段内的最高价和最低价之间的中点，并且向前移动到未来一定时间段内。
 *
 * Senkou Span B 的计算方式是将最高价和最低价的中点作为当前线，然后将这个值移动到未来一定时间段内。通常情况下，一目均衡云中的标准设置是将这个值移动到未来的 26 个周期。
 *
 * Senkou Span B 的计算公式如下：
 *
 * Senkou Span B = (Highest High + Lowest Low ) / 2
 *
 * 其中，“Highest High” 是一定时间段内的最高价，“Lowest Low” 是一定时间段内的最低价。
 *
 * Senkou Span B 在一目均衡云中通常被用作未来的支撑和阻力水平。当 Senkou Span B 处于价格图表的上方时，它可能作为一个未来的阻力水平；而当 Senkou Span B 处于价格图表的下方时，则可能作为一个未来的支撑水平。
 *
 * 总的来说，Senkou Span B 是一目均衡云中的一个重要指标，它用于显示未来的支撑和阻力水平，并提供了一些未来价格走势的参考，有助于交易者制定更明智的交易策略。
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud</a>
 */
public class IchimokuSenkouSpanBIndicator extends CachedIndicator<Num> {

    // ichimoku avg line indicator
    // ichimoku 平均线指标
    IchimokuLineIndicator lineIndicator;

    /**
     * Displacement on the chart (usually 26)
     * 图表上的位移（通常为 26）
     */
    private final int offset;

    /**
     * Constructor.
     * 
     * @param series the series
     */
    public IchimokuSenkouSpanBIndicator(BarSeries series) {

        this(series, 52, 26);
    }

    /**
     * Constructor.
     * 
     * @param series   the series
     * @param barCount the time frame (usually 52)
     *                 时间范围（通常为 52）
     */
    public IchimokuSenkouSpanBIndicator(BarSeries series, int barCount) {

        this(series, barCount, 26);
    }

    /**
     * Constructor.
     * 
     * @param series   the series
     * @param barCount the time frame (usually 52)
     *                 时间范围（通常为 52）
     * @param offset   displacement on the chart
     *                 图表上的位移
     */
    public IchimokuSenkouSpanBIndicator(BarSeries series, int barCount, int offset) {

        super(series);
        lineIndicator = new IchimokuLineIndicator(series, barCount);
        this.offset = offset;
    }

    @Override
    protected Num calculate(int index) {
        int spanIndex = index - offset + 1;
        if (spanIndex >= getBarSeries().getBeginIndex()) {
            return lineIndicator.getValue(spanIndex);
        } else {
            return NaN.NaN;
        }
    }
}
