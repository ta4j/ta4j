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
 * WMA indicator.
 * WMA 指标。
 *
 * 加权移动平均线（Weighted Moving Average，WMA）是一种技术指标，它使用加权的方法来计算价格的移动平均值，以平滑价格数据并识别价格的趋势。
 *
 * WMA指标的计算过程相对简单，但与简单移动平均线（SMA）不同，WMA给予最近的价格更高的权重。通常情况下，WMA指标的计算公式如下：
 *
 *   WMA = w_1 \cdot P_1 + w_2 \cdot P_2 + \ldots + w_n \cdot P_n  /  w_1 + w_2 + \ldots + w_n
 *
 * 其中：
 * - \( P_1, P_2, \ldots, P_n \) 是最近的 n 个价格数据。
 * - \( w_1, w_2, \ldots, w_n \) 是分配给每个价格的权重。
 *
 * 通常情况下，权重越大的价格对WMA的影响越大。一种常见的加权方法是使用等差数列，即最近的价格具有最高的权重，然后依次递减。例如，如果选择10个周期作为计算期间，那么最近的价格可能具有10的权重，而最早的价格可能只有1的权重。
 *
 * WMA指标的数值通常以一条平滑的曲线显示在价格图上，它可以帮助交易者更清晰地识别价格的趋势，并据此制定买卖策略。
 *
 * 交易者通常使用WMA指标来捕捉价格的短期和长期趋势，以及识别价格的拐点。例如，当价格上穿WMA线时，可能暗示着价格的上升趋势开始，为买入信号；相反，当价格下穿WMA线时，可能暗示着价格的下跌趋势开始，为卖出信号。
 *
 * 总的来说，WMA指标是一种用于平滑价格数据并识别价格趋势的技术指标，可以帮助交易者更好地理解市场的价格动态并制定相应的交易策略。
 *
 */
public class WMAIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final Indicator<Num> indicator;

    public WMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        if (index == 0) {
            return indicator.getValue(0);
        }

        Num value = numOf(0);
        int loopLength = (index - barCount < 0) ? index + 1 : barCount;
        int actualIndex = index;
        for (int i = loopLength; i > 0; i--) {
            value = value.plus(numOf(i).multipliedBy(indicator.getValue(actualIndex)));
            actualIndex--;
        }

        return value.dividedBy(numOf((loopLength * (loopLength + 1)) / 2));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
