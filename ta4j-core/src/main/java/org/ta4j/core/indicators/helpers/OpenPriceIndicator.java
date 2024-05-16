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
 * Open price indicator.
 * 打开价格指标。
 *
 * 开盘价指标通常是指用于分析金融市场中资产开盘价变化的指标。开盘价是某一交易周期（如一天、一周或一个月）的第一笔交易的价格，通常被视为市场情绪的一种体现，因为它反映了市场在新的交易周期开始时的买卖意愿和预期。
 *
 * 开盘价指标可以有多种形式，具体取决于分析的目的和所关注的市场。一些常见的开盘价指标包括：
 *
 * 1. **开盘价走势图**：简单地绘制每个交易周期的开盘价，并通过图表分析来观察开盘价的走势和变化。
 *
 * 2. **开盘价比较**：将每个交易周期的开盘价与前一个周期或其他参考价位进行比较，以评估市场的变化和趋势。
 *
 * 3. **开盘价差异分析**：计算开盘价之间的差异或百分比变化，以帮助分析市场的波动性和价格变化。
 *
 * 开盘价指标在技术分析和市场研究中经常用于衡量市场的情绪和预期，以及分析市场走势的起点。然而，需要注意的是，单独使用开盘价指标可能不足以提供全面的市场分析，通常需要结合其他指标和分析方法一起使用。
 *
 */
public class OpenPriceIndicator extends PriceIndicator {

    public OpenPriceIndicator(BarSeries series) {
        super(series, Bar::getOpenPrice);
    }
}