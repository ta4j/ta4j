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
package org.ta4j.core.indicators.adx;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.MMAIndicator;
import org.ta4j.core.indicators.helpers.MinusDMIndicator;
import org.ta4j.core.num.Num;

/**
 * -DI indicator. Part of the Directional Movement System
 * * -DI 指标。 定向运动系统的一部分
 *
 * 负向趋向指标（-DI，Negative Directional Indicator）是技术分析中的一个工具，用于衡量市场下跌趋势的强度。与正向趋向指标（+DI，Positive Directional Indicator）一起，它是平均趋向指数（ADX）的组成部分之一。该指标由 J. Welles Wilder 在他的1978年出版物《New Concepts in Technical Trading Systems》中首次提出。
 *
 * ### 计算方法
 * 1. **计算DM（趋向运动）**：
 *     - **-DM（负向运动）**：
 *         - 当前低点 - 前一低点，如果结果为负或为零，则设为0，否则使用该值。
 *     - **+DM（正向运动）**：
 *         - 当前高点 - 前一高点，如果结果为负或为零，则设为0，否则使用该值。
 *
 * 2. **计算True Range（真实波动幅度，TR）**：
 *     - TR = max(当前高点 - 当前低点, 当前高点 - 前一收盘价, 当前低点 - 前一收盘价)
 *
 * 3. **计算-DI**：
 *     - -DI = (14期的-DM总和 / 14期的TR总和) * 100
 *
 * ### 解读-DI
 * - **-DI 高**：表示下跌趋势强度较大。
 * - **-DI 低**：表示下跌趋势强度较小。
 *
 * ### 使用策略
 * 1. **趋势确认**：将 -DI 与 +DI 结合使用。
 *     - 如果 -DI 高于 +DI，并且 ADX 高于 25，表示市场处于强下跌趋势。
 *     - 如果 +DI 高于 -DI，并且 ADX 高于 25，表示市场处于强上涨趋势。
 *
 * 2. **交叉点策略**：
 *     - 当 -DI 从下方交叉上升超过 +DI 时，这可能是卖出信号。
 *     - 当 -DI 从上方交叉下降低于 +DI 时，这可能是买入信号。
 *
 * 3. **趋势强度**：结合 ADX 使用。
 *     - 当 ADX 高于 25 时，如果 -DI 高于 +DI，趋势可能更强劲。
 *     - 当 ADX 低于 20 时，市场可能处于无趋势状态，不适合趋势跟踪策略。
 *
 * ### 注意事项
 * - **滞后性**：-DI 作为一个趋势指标，基于过去的数据，可能会在趋势已经开始或结束后才给出信号。
 * - **结合其他指标**：单独使用 -DI 可能会产生误导信号，通常需要结合其他指标如 ADX 和 +DI 进行综合分析。
 *
 * 总结，-DI 指标是技术分析中的一个重要工具，通过衡量市场下跌趋势的强度，帮助交易者识别卖出信号和确认市场趋势。结合 +DI 和 ADX，它可以为交易策略提供更全面的指导。
 *
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:average_directional_index_adx">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:average_directional_index_adx</a>
 * @see <a
 *      href="https://www.investopedia.com/terms/a/adx.asp>https://www.investopedia.com/terms/a/adx.asp</a>
 */
public class MinusDIIndicator extends CachedIndicator<Num> {

    private final MMAIndicator avgMinusDMIndicator;
    private final ATRIndicator atrIndicator;
    private final int barCount;

    public MinusDIIndicator(BarSeries series, int barCount) {
        super(series);
        this.barCount = barCount;
        this.avgMinusDMIndicator = new MMAIndicator(new MinusDMIndicator(series), barCount);
        this.atrIndicator = new ATRIndicator(series, barCount);
    }

    @Override
    protected Num calculate(int index) {
        return avgMinusDMIndicator.getValue(index).dividedBy(atrIndicator.getValue(index)).multipliedBy(numOf(100));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
