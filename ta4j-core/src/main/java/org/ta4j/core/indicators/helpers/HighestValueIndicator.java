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
 * Highest value indicator.
 * 最高价值指标。
 *
 * "最高值指标"通常是指用于确定在一段时间内资产或指标达到的最高水平的指标。这种指标可以帮助分析市场中的价格极值，并提供有关资产或指标在特定时间段内的最高价位的信息。
 *
 * 最高值指标通常用于以下几种情况：
 *
 * 1. **价格分析**：在技术分析中，最高值指标可以用来识别资产或指标在一段时间内的价格高点。这对于确定支撑和阻力水平以及趋势的转折点非常有用。
 *
 * 2. **波动性分析**：在波动性分析中，最高值指标可以帮助衡量资产价格的波动范围。通过比较最高值和最低值，可以得出价格波动的幅度和变化情况。
 *
 * 3. **指标分析**：一些技术指标，如相对强弱指标（RSI）或移动平均线，可能会在特定时间段内达到最高水平。通过识别指标的最高值，可以帮助分析市场的强弱程度和可能的转折点。
 *
 * 最高值指标通常以数字形式表示，例如某个资产在一段时间内的最高价格，或者某个指标在特定时间段内的最高数值。这些指标可以提供有关市场走势和价格极值的重要信息，并且对于制定交易策略和进行市场分析非常有帮助。
 */
public class HighestValueIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;

    private final int barCount;

    public HighestValueIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        if (indicator.getValue(index).isNaN() && barCount != 1) {
            return new HighestValueIndicator(indicator, barCount - 1).getValue(index - 1);
        }
        int end = Math.max(0, index - barCount + 1);
        Num highest = indicator.getValue(index);
        for (int i = index - 1; i >= end; i--) {
            if (highest.isLessThan(indicator.getValue(i))) {
                highest = indicator.getValue(i);
            }
        }
        return highest;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
