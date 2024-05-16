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
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.num.Num;

/**
 * The Chandelier Exit (long) Indicator.
 * 枝形吊灯出口（长）指标。
 *
 * 悬吊退出（长）指标（Chandelier Exit (long) Indicator）是一种技术指标，用于帮助交易者确认并管理持有多头头寸时的止损水平。它是由特里尔·张伯伦（Tushar Chande）开发的，旨在帮助交易者在持有多头头寸时设定止损水平，以保护他们的投资。
 *
 * 悬吊退出指标的计算过程如下：
 *
 * 1. 首先，确定一个固定的倍数（例如，3）和一个固定的时间周期（例如，22个交易周期）。
 *
 * 2. 计算过去一段时间内的最高价（High）。
 *
 * 3. 计算过去一段时间内的最低收盘价（Low Close）。
 *
 * 4. 计算悬吊退出的值：
 *    - 悬吊退出（长）= 最高价 - 最低收盘价的移动平均值 * 固定倍数
 *
 * 悬吊退出指标的作用是作为多头头寸的止损水平。当价格跌破悬吊退出指标的数值时，可能暗示着价格趋势的转变，交易者可以考虑平仓多头头寸以避免进一步损失。
 *
 * 悬吊退出指标可以根据交易者的偏好和特定市场的特点进行调整。较大的倍数会导致更宽的止损水平，因此更容易触发止损；而较小的倍数则会产生较窄的止损水平，因此更容易忽略短期价格波动。交易者通常会根据自己的风险偏好和市场特点来选择合适的倍数和时间周期来使用悬吊退出指标。
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:chandelier_exit">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:chandelier_exit</a>
 */
public class ChandelierExitLongIndicator extends CachedIndicator<Num> {

    private final HighestValueIndicator high;
    private final ATRIndicator atr;
    private final Num k;

    /**
     * Constructor.
     *
     * @param series the bar series
     *               酒吧系列
     */
    public ChandelierExitLongIndicator(BarSeries series) {
        this(series, 22, 3);
    }

    /**
     * Constructor.
     *
     * @param series   the bar series
     *                 酒吧系列
     * @param barCount the time frame (usually 22)
     *                 时间范围（通常为 22）
     * @param k        the K multiplier for ATR (usually 3.0)
     *                 ATR 的 K 乘数（通常为 3.0）
     */
    public ChandelierExitLongIndicator(BarSeries series, int barCount, double k) {
        super(series);
        high = new HighestValueIndicator(new HighPriceIndicator(series), barCount);
        atr = new ATRIndicator(series, barCount);
        this.k = numOf(k);
    }

    @Override
    protected Num calculate(int index) {
        return high.getValue(index).minus(atr.getValue(index).multipliedBy(k));
    }
}
