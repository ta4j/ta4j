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

/**
 * Trade count indicator.
 * 贸易计数指标。
 *
 * 交易次数指标是用于衡量特定交易期间内完成的交易数量的指标。这个指标通常用于分析交易活动的水平和市场的流动性。
 *
 * 交易次数指标可以针对不同的时间段进行计算，例如一天、一周或一个月。它提供了关于交易活动水平和市场参与程度的重要信息。
 *
 * 在金融市场分析中，交易次数指标可以用于多种情况，包括但不限于：
 *
 * 1. **市场流动性分析**：交易次数指标可以提供有关市场流动性的信息。较高的交易次数通常表示市场流动性较高，而较低的交易次数可能表示市场流动性较低。
 *
 * 2. **交易活动监控**：交易次数指标可以用于监控特定时间段内的交易活动水平。这有助于识别市场的活跃时段和交易量的变化。
 *
 * 3. **市场趋势分析**：交易次数指标也可以用于分析市场的趋势。例如，如果交易次数在上升，则可能表示市场参与度增加，反之亦然。
 *
 * 总的来说，交易次数指标是一种重要的市场参与度指标，用于衡量特定时间段内完成的交易数量，从而提供有关市场流动性和交易活动水平的信息。
 */
public class TradeCountIndicator extends CachedIndicator<Long> {

    public TradeCountIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Long calculate(int index) {
        return getBarSeries().getBar(index).getTrades();
    }
}