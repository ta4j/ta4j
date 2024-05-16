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
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.num.Num;

/**
 * The Chandelier Exit (short) Indicator.
 * *枝形吊灯出口（空头）指示器。
 *
 * 悬吊退出（短）指标（Chandelier Exit (short) Indicator）与悬吊退出（长）指标类似，但用于帮助交易者确认并管理持有空头头寸时的止损水平。它也是由特里尔·张伯伦（Tushar Chande）开发的。
 *
 * 悬吊退出（短）指标的计算与悬吊退出（长）指标的计算类似，但在这种情况下，它用于确定空头头寸的止损水平。具体计算步骤如下：
 *
 * 1. 首先，确定一个固定的倍数（例如，3）和一个固定的时间周期（例如，22个交易周期）。
 *
 * 2. 计算过去一段时间内的最低价（Low）。
 *
 * 3. 计算过去一段时间内的最高收盘价（High Close）。
 *
 * 4. 计算悬吊退出的值：
 *    - 悬吊退出（短）= 最低价 + 最高收盘价的移动平均值 * 固定倍数
 *
 * 悬吊退出（短）指标的作用是作为空头头寸的止损水平。当价格上涨突破悬吊退出指标的数值时，可能暗示着价格趋势的转变，交易者可以考虑平仓空头头寸以避免进一步损失。
 *
 * 与悬吊退出（长）指标类似，悬吊退出（短）指标也可以根据交易者的偏好和市场的特点进行调整。交易者通常会根据自己的风险偏好和市场特点来选择合适的倍数和时间周期来使用悬吊退出指标。
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:chandelier_exit">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:chandelier_exit</a>
 */
public class ChandelierExitShortIndicator extends CachedIndicator<Num> {

    private final LowestValueIndicator low;
    private final ATRIndicator atr;
    private final Num k;

    /**
     * Constructor.
     *
     * @param series the bar series
     *               酒吧系列
     */
    public ChandelierExitShortIndicator(BarSeries series) {
        this(series, 22, 3d);
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
    public ChandelierExitShortIndicator(BarSeries series, int barCount, double k) {
        super(series);
        low = new LowestValueIndicator(new LowPriceIndicator(series), barCount);
        atr = new ATRIndicator(series, barCount);
        this.k = numOf(k);
    }

    @Override
    protected Num calculate(int index) {
        return low.getValue(index).plus(atr.getValue(index).multipliedBy(k));
    }
}
