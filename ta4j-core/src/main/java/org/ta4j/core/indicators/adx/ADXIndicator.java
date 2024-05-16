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
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.MMAIndicator;
import org.ta4j.core.indicators.helpers.DXIndicator;
import org.ta4j.core.num.Num;

/**
 * ADX indicator. Part of the Directional Movement System.
 * * ADX 指标。 定向运动系统的一部分。
 *
 * ADX（Average Directional Index，平均趋向指数）是一种用于技术分析的指标，旨在衡量市场趋势的强度，而不是趋势的方向。由 J. Welles Wilder 在他的1978年出版物《New Concepts in Technical Trading Systems》中首次介绍。ADX 是一个无方向性的指标，这意味着它仅表示趋势的强弱，而不表明趋势是上涨还是下跌。
 *
 * ### 主要组成部分
 * ADX 通常与两个其他指标一起使用，这两个指标也是由 Wilder 引入的：
 * 1. **+DI（正向趋向指标）**：衡量上升趋势的强度。
 * 2. **-DI（负向趋向指标）**：衡量下降趋势的强度。
 *
 * 这些指标的计算涉及到比较当前的高点和低点，以确定上升和下降运动的程度。
 *
 * ### 计算方法
 * 1. **计算+DM（正向运动）和-DM（负向运动）**：
 *     - +DM = 当前高点 - 前一高点（如果为负，则设为0）
 *     - -DM = 前一低点 - 当前低点（如果为负，则设为0）
 *
 * 2. **计算True Range（真实波动幅度，TR）**：
 *     - TR = max(当前高点 - 当前低点, 当前高点 - 前一收盘价, 当前低点 - 前一收盘价)
 *
 * 3. **计算+DI和-DI**：
 *     - +DI = (14期的+DM总和 / 14期的TR总和) * 100
 *     - -DI = (14期的-DM总和 / 14期的TR总和) * 100
 *
 * 4. **计算ADX**：
 *     - 首先计算DX（Directional Movement Index，趋向运动指数）：
 *         - DX = (|+DI - -DI| / (+DI + -DI)) * 100
 *     - 然后计算ADX：
 *         - ADX 是 DX 的14期平均值。
 *
 * ### 解读ADX
 * - **ADX 值高于 25**：市场处于强趋势中（无论是上涨还是下跌）。
 * - **ADX 值低于 20**：市场处于弱趋势或无趋势状态。
 * - **ADX 上升**：趋势正在增强。
 * - **ADX 下降**：趋势正在减弱。
 *
 * ### 使用策略
 * 1. **识别趋势**：当 ADX 高于 25 时，投资者可以寻找趋势交易机会。
 * 2. **避免无趋势市场**：当 ADX 低于 20 时，可能更适合采用震荡策略而不是趋势跟踪策略。
 * 3. **确认趋势反转**：如果 ADX 从高位回落，可能意味着趋势的结束。
 *
 * ### 注意事项
 * - ADX 是一个滞后指标，因为它基于过去的价格数据，因此可能在趋势已经开始或结束后才给出信号。
 * - 它不提供买入或卖出信号，仅提供趋势强度的信息，需要与其他指标结合使用以制定交易决策。
 *
 * 总结，ADX 指标是一个强大的工具，用于确认市场的趋势强度，帮助交易者在适当的时间点进入和退出市场。
 *
 * @see <a
 *      href="https://www.investopedia.com/terms/a/adx.asp>https://www.investopedia.com/terms/a/adx.asp</a>
 */
public class ADXIndicator extends CachedIndicator<Num> {

    private final MMAIndicator averageDXIndicator;
    private final int diBarCount;
    private final int adxBarCount;

    public ADXIndicator(BarSeries series, int diBarCount, int adxBarCount) {
        super(series);
        this.diBarCount = diBarCount;
        this.adxBarCount = adxBarCount;
        this.averageDXIndicator = new MMAIndicator(new DXIndicator(series, diBarCount), adxBarCount);
    }

    public ADXIndicator(BarSeries series, int barCount) {
        this(series, barCount, barCount);
    }

    @Override
    protected Num calculate(int index) {
        return averageDXIndicator.getValue(index);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " diBarCount: " + diBarCount + " adxBarCount: " + adxBarCount;
    }
}
