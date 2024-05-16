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

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Difference indicator.
 * 差异指标。
 *
 * I.e.: first - second
 * 即：第一 - 第二
 *
 * "差值指标"通常是指在技术分析中用于测量两个指标或数据之间的差异或变化的指标。这种指标可以用于衡量价格变动、趋势变化、或者其他技术指标之间的差异。在不同的情况下，"差值指标"可以采用不同的计算方法和应用方式。
 *
 * 在常见的技术分析中，"差值指标"可能指的是以下一些指标或计算方法：
 *
 * 1. **价格差异指标**：用于测量两种不同价格之间的差异，例如收盘价与开盘价之间的差价或者当前价格与移动平均线之间的差异。
 *
 * 2. **技术指标之间的差异**：用于比较两个或多个技术指标之间的差异，例如MACD线和信号线之间的差值，或者RSI指标和价格之间的差异。
 *
 * 3. **百分比差异指标**：用于计算两个值之间的百分比变化，例如前一天收盘价和当天收盘价之间的百分比变化。
 *
 * 4. **价格和成交量之间的差异**：用于比较价格变动和成交量变动之间的关系，例如价格上涨时成交量的增加程度。
 *
 * 这些差值指标可以帮助分析师和交易者更好地理解市场的变化和趋势，提供了一种衡量价格变动和指标变化的方式。在使用差值指标时，通常需要结合其他分析方法和指标，以便更全面地评估市场情况。
 *
 */
public class DifferenceIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> first;
    private final Indicator<Num> second;

    /**
     * Constructor. (first minus second)
     * 构造函数。 （第一个减去第二个）
     * 
     * @param first  the first indicator
     *               第一个指标
     * @param second the second indicator
     *               第二个指标
     */
    public DifferenceIndicator(Indicator<Num> first, Indicator<Num> second) {
        // TODO: check if first series is equal to second one
        // TODO: 检查第一个系列是否等于第二个系列
        super(first);
        this.first = first;
        this.second = second;
    }

    @Override
    protected Num calculate(int index) {
        return first.getValue(index).minus(second.getValue(index));
    }
}
