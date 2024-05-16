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
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Average high-low indicator.
 * 平均高低指标。
 *
 * 平均高低指标通常用于衡量资产在一段时间内的价格波动范围。这种指标可以帮助分析市场的波动性，并提供关于价格变化的平均幅度的信息。
 *
 * 计算平均高低指标的方法是取每个时间段内的最高价和最低价之间的差值，然后计算这些差值的平均值。通常，这个平均值可以根据投资者的偏好和市场的特性以不同的方式计算。
 *
 * 平均高低指标可以用于以下几种情况：
 *
 * 1. **波动性分析**：通过观察平均高低值，可以评估资产价格的波动性。较高的平均高低值通常表示较大的价格波动，而较低的平均高低值则表示较小的价格波动。
 *
 * 2. **趋势确认**：在市场处于趋势状态时，观察平均高低值的变化可以提供有关趋势强度的信息。例如，如果平均高低值逐渐增加，可能表明趋势的动力在增加，反之亦然。
 *
 * 3. **交易策略**：一些交易策略可能会使用平均高低值作为进出场的参考因素。例如，交易者可能会在平均高低值较高的时候采取较激进的交易策略，而在平均高低值较低的时候采取较保守的交易策略。
 *
 * 总的来说，平均高低指标提供了有关资产价格波动范围的重要信息，对于市场分析和交易决策都具有一定的参考价值。
 *
 */
public class MedianPriceIndicator extends CachedIndicator<Num> {

    public MedianPriceIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        final Bar bar = getBarSeries().getBar(index);
        return bar.getHighPrice().plus(bar.getLowPrice()).dividedBy(numOf(2));
    }
}
