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
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

/**
 * Close price indicator.
 * 收盘价指标。
 *
 * Close price indicator（收盘价指标）是一种简单而常用的技术分析指标，它基于资产在交易周期结束时的收盘价格。这个指标通常用于跟踪资产价格的变化，并作为其他技术指标的输入。
 *
 * ### 计算方法
 * 收盘价指标的计算非常简单，只需记录资产在每个交易周期结束时的收盘价格即可。在日线图表中，收盘价格通常是交易日结束时的最后成交价。
 *
 * ### 意义
 * 收盘价指标的主要意义在于：
 * - 提供了资产在每个交易周期结束时的价格水平。
 * - 作为其他技术指标和分析方法的基础，用于生成信号或计算其他衍生指标。
 *
 * ### 应用
 * 收盘价指标的应用包括但不限于以下方面：
 * - **价格分析**：收盘价是技术分析中最重要的价格之一，可以用于识别支撑和阻力水平、趋势的方向和强度等。
 * - **指标计算**：收盘价常被用作其他技术指标的输入，例如移动平均线、相对强弱指标等。
 * - **信号生成**：某些交易策略可能会根据收盘价生成买入或卖出信号，例如，当收盘价突破某一阻力位时产生买入信号。
 *
 * ### 注意事项
 * - 虽然收盘价是技术分析中的重要指标，但单独使用它可能无法提供足够的信息来做出有效的交易决策，建议结合其他指标和分析方法使用。
 * - 在使用收盘价进行分析时，应该考虑市场的整体环境、交易量、以及其他因素的影响。
 *
 * ### 总结
 * 收盘价指标是一种简单但重要的技术分析指标，它提供了资产在每个交易周期结束时的价格水平。作为技术分析的基础，收盘价常被用于价格分析、指标计算和信号生成等方面。在使用时，应结合其他指标和分析方法，并考虑市场的整体环境和其他因素的影响。
 *
 */
public class ClosePriceIndicator extends PriceIndicator {

    public ClosePriceIndicator(BarSeries series) {
        super(series, Bar::getClosePrice);
    }
}
