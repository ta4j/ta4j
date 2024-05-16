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
 * Lowest value indicator.
 * 最低值指标。
 *
 * 最低值指标通常用于确定在一段时间内资产或指标达到的最低水平。这种指标对于分析市场中的价格极值以及确定支撑位或价格底部非常有用。
 * 在技术分析中，最低值指标可以用于以下几种情况：
 *
 * 1. **价格分析**：最低值指标可以帮助确定资产在特定时间段内的价格低点。这有助于识别市场中的支撑位和价格底部，以及判断是否存在下降趋势。
 *
 * 2. **波动性分析**：通过比较不同时间段内的最低价格水平，可以衡量资产价格的波动范围。这有助于评估市场的波动性和价格变化的幅度。
 *
 * 3. **交易策略**：最低值指标可以作为交易策略中的一个参考因素。例如，一些交易者可能会在价格接近历史低点时采取相应的行动，如寻找反弹的机会或者设置止损位。
 *
 * 最低值指标通常以数字形式表示，例如某个资产在一段时间内的最低交易价格。这些指标提供了有关市场价格走势和价格极值的重要信息，并且对于制定交易决策和进行市场分析非常有帮助。
 */
public class LowestValueIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;

    private final int barCount;

    public LowestValueIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        if (indicator.getValue(index).isNaN() && barCount != 1) {
            return new LowestValueIndicator(indicator, barCount - 1).getValue(index - 1);
        }
        int end = Math.max(0, index - barCount + 1);
        Num lowest = indicator.getValue(index);
        for (int i = index - 1; i >= end; i--) {
            if (lowest.isGreaterThan(indicator.getValue(i))) {
                lowest = indicator.getValue(i);
            }
        }
        return lowest;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
