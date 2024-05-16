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

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Price variation indicator.
 * 价格变化指标。
 *
 * 价格变动指标是一种用于衡量资产价格波动性或变化幅度的指标。它提供了有关价格波动性的信息，有助于分析市场的波动特征和价格变化的趋势。这个指标通常用于金融市场分析中，可以帮助交易者和投资者更好地理解市场的波动性，并辅助他们做出投资决策。
 *
 * 价格变动指标的计算方式可以有多种，具体取决于所使用的方法和数据。以下是一些常见的价格变动指标：
 *
 * 1. **标准差**：标准差是衡量数据集合中数值分散程度的一种方法，用于量化价格的波动性。较大的标准差表示价格波动性较高，而较小的标准差则表示价格波动性较低。
 *
 * 2. **百分比变化**：百分比变化指标衡量价格在一定时间段内的变化幅度，通常以百分比的形式表示。这可以帮助分析价格的涨跌趋势和波动性。
 *
 * 3. **价格范围**：价格范围指标表示在一段时间内资产价格的最高值和最低值之间的差值，用于衡量价格的变动范围和波动性。
 *
 * 4. **平均真实范围（ATR）**：ATR指标是一种衡量价格波动性的技术指标，它考虑了价格的真实范围，通常用于确定止损位和目标价格。
 *
 * 这些价格变动指标可以根据具体的需求和市场情况选择合适的方法来计算，并结合其他指标和分析方法一起使用，以提供对市场价格波动性的更全面的认识。
 *
 */
public class PriceVariationIndicator extends CachedIndicator<Num> {

    public PriceVariationIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        Num previousBarClosePrice = getBarSeries().getBar(Math.max(0, index - 1)).getClosePrice();
        Num currentBarClosePrice = getBarSeries().getBar(index).getClosePrice();
        return currentBarClosePrice.dividedBy(previousBarClosePrice);
    }
}