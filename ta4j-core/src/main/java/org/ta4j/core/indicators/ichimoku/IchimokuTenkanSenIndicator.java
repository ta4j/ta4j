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

/**
 * Ichimoku clouds: Tenkan-sen (Conversion line) indicator
 * Ichimoku 云： Tenkan-sen （转换线） 指标
 *
 * Tenkan-sen（转换线）是一目均衡云指标的一个组成部分，它用于显示短期市场趋势的变化。Tenkan-sen 是一段特定时间内的最高价和最低价的平均值，并且向前移动到未来一定时间段内。
 *
 * Tenkan-sen 的计算方式是将一定时间段内的最高价和最低价的平均值作为当前线，然后将这个值移动到未来一定时间段内。通常情况下，一目均衡云中的标准设置是将这个值移动到未来的 9 个周期。
 *
 * Tenkan-sen 的计算公式如下：
 *
 * Tenkan-sen = (Highest High + Lowest Low ) / 2
 *
 * 其中，“Highest High” 是一定时间段内的最高价，“Lowest Low” 是一定时间段内的最低价。
 *
 * Tenkan-sen 在一目均衡云中通常被用作短期市场趋势的参考线。当 Tenkan-sen 上升时，这可能表示短期趋势为上升；而当 Tenkan-sen 下降时，则可能表示短期趋势为下降。
 *
 * 总的来说，Tenkan-sen 是一目均衡云中的一个重要指标，它用于显示短期市场趋势的变化，并提供了一些未来价格走势的参考，有助于交易者更好地理解市场的动态并制定相应的交易策略。
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud</a>
 */
public class IchimokuTenkanSenIndicator extends IchimokuLineIndicator {

    /**
     * Constructor.
     * 
     * @param series the series
     */
    public IchimokuTenkanSenIndicator(BarSeries series) {
        this(series, 9);
    }

    /**
     * Constructor.
     * 
     * @param series   the series
     * @param barCount the time frame (usually 9)
     *                 时间范围（通常为 9）
     */
    public IchimokuTenkanSenIndicator(BarSeries series, int barCount) {
        super(series, barCount);
    }
}
