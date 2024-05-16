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
 * Ichimoku clouds: Kijun-sen (Base line) indicator
 * Ichimoku clouds (Ichimoku云): Kijun-sen（基线）指标
 *
 * 在一目均衡云（Ichimoku Cloud）中，Kijun-sen（基准线）是指一段特定时间范围内的平均价格。它是一目均衡云中的一个重要指标，用于识别趋势的方向、支撑和阻力水平。
 *
 * Kijun-sen 的计算方式是取一定周期内的最高价和最低价的平均值。通常情况下，Kijun-sen 使用的周期为 26 个周期，这个周期数是根据一目均衡云的设定而来。
 *
 * Kijun-sen 可以用来识别价格趋势的方向。当价格位于 Kijun-sen 之上时，这可能表示市场处于上涨趋势，而当价格位于 Kijun-sen 之下时，则可能表示市场处于下跌趋势。
 *
 * 此外，Kijun-sen 也可以用作支撑和阻力水平。当价格接近或穿过 Kijun-sen 时，Kijun-sen 可能会提供支撑或阻力，从而影响价格的反弹或突破。
 *
 * 总的来说，Kijun-sen 是一目均衡云中的一个重要指标，它可以帮助交易者识别趋势的方向，并提供支撑和阻力水平，有助于制定更有效的交易策略。
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud</a>
 */
public class IchimokuKijunSenIndicator extends IchimokuLineIndicator {

    /**
     * Constructor.
     * 
     * @param series the series
     */
    public IchimokuKijunSenIndicator(BarSeries series) {
        super(series, 26);
    }

    /**
     * Constructor.
     * 
     * @param series   the series
     * @param barCount the time frame (usually 26)
     *                 时间范围（通常为 26）
     */
    public IchimokuKijunSenIndicator(BarSeries series, int barCount) {
        super(series, barCount);
    }
}
