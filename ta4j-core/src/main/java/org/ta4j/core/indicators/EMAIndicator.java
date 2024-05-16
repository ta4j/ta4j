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

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Exponential moving average indicator.
 * 指数移动平均线指标。
 *
 * 指数移动平均（Exponential Moving Average，EMA）是一种常用的技术指标，用于平滑资产价格数据并识别价格的趋势方向。
 *
 * 与简单移动平均线（SMA）不同，指数移动平均对最新的价格数据给予了更高的权重，因此对价格的变化更为敏感。这使得EMA更适合用于追踪价格的近期变化。
 *
 * EMA的计算过程如下：
 *
 * 1. 选择一个固定的时间周期（例如20个交易周期）。
 * 2. 计算第一个周期的EMA，通常是使用该周期内的所有价格数据的简单移动平均作为初始值。
 * 3. 计算后续周期的EMA，使用以下公式：
 *    \[ EMA = (Close - EMA(previous)) \times \text{Multiplier} + EMA(previous) \]
 *    其中，Close是当前周期的收盘价，EMA(previous)是前一个周期的EMA值，Multiplier是平滑系数，通常计算方式为\[ 2 / (N + 1) \]，其中N为选定的时间周期数。
 *
 * EMA的主要作用包括：
 *
 * 1. **平滑价格数据：** EMA可以帮助平滑价格数据，去除价格的随机波动，更清晰地显示价格的趋势方向。
 * 2. **确定趋势方向：** 由于EMA对近期价格变化更为敏感，因此可以更快地反映价格的趋势变化，帮助交易者确定市场的短期和长期趋势。
 * 3. **作为支撑和阻力：** 价格经常在EMA附近出现支撑和阻力，交易者可以利用EMA来识别这些价格水平，并据此制定交易策略。
 *
 * EMA经常与其他技术指标和价格模式结合使用，例如移动平均线交叉、趋势线等，以帮助交易者制定更可靠的交易决策。
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/e/ema.asp">https://www.investopedia.com/terms/e/ema.asp</a>
 */
public class EMAIndicator extends AbstractEMAIndicator {

    /**
     * Constructor.
     *
     * @param indicator an indicator
     *                  一个指标
     * @param barCount  the EMA time frame
     *                  EMA时间框架
     */
    public EMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator, barCount, (2.0 / (barCount + 1)));
    }
}
