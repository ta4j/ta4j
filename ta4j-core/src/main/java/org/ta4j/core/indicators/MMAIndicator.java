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
 * Modified moving average indicator.
 * 修正的移动平均线指标。
 *
 * It is similar to exponential moving average but smooths more slowly. Used in Welles Wilder's indicators like ADX, RSI.
 * * 它类似于指数移动平均线，但平滑更慢。 用于 Welles Wilder 的指标，如 ADX、RSI。
 *
 *
 * Modified Moving Average（修改移动平均，MMA）是一种技术指标，与传统的简单移动平均线（SMA）类似，但在计算方法上有所不同。MMA通过调整价格数据的权重，以使得移动平均更快地反应价格的变化。
 *
 * MMA的计算过程相对简单，主要包括以下几个步骤：
 *
 * 1. 选择一个固定的时间周期（例如20个交易周期）。
 * 2. 对于每个价格数据，分别赋予相应的权重，通常是使用指数加权移动平均线（EMA）来调整权重，使得近期价格数据的权重较高。
 * 3. 将加权后的价格数据相加，并除以权重的总和，得到MMA值。
 *
 * MMA指标的数值通常波动在价格数据的周围，其特点是能够更快地反应价格的变化，相比于SMA，更加敏感。
 *
 * 交易者通常使用MMA来识别价格的趋势方向和制定买卖信号。例如，当价格上穿MMA时，可能暗示着上涨趋势的开始，为买入信号；相反，当价格下穿MMA时，可能暗示着下跌趋势的开始，为卖出信号。
 *
 * 总的来说，MMA是一种简单但有效的移动平均线指标，适用于快速市场和对价格变化更敏感的交易策略。然而，像所有技术指标一样，单独使用MMA可能会导致误导，因此建议结合其他技术指标和价格模式进行综合分析。
 *
 */
public class MMAIndicator extends AbstractEMAIndicator {

    /**
     * Constructor.
     *
     * @param indicator an indicator
     *                  一个指标
     * @param barCount  the MMA time frame
     *                  MMA时间框架
     */
    public MMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator, barCount, 1.0 / barCount);
    }
}
