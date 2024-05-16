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
 * Linearly Weighted Moving Average (LWMA).
 * 线性加权移动平均线 (LWMA)。
 *
 * 线性加权移动平均线（Linearly Weighted Moving Average，LWMA）是一种基于线性加权的移动平均线指标，与简单移动平均线（SMA）和指数移动平均线（EMA）相比，它对最新的价格数据给予了更高的权重。
 *
 * LWMA的计算过程相对简单，但与SMA和EMA相比，更为复杂。它的计算步骤如下：
 *
 * 1. 选择一个固定的时间周期（例如20个交易周期）。
 * 2. 对于每个价格数据，分别赋予相应的权重，权重随时间递减，最新的价格数据权重最高，最老的价格数据权重最低。
 * 3. 将加权后的价格数据相加，并除以权重的总和，得到LWMA值。
 *
 * LWMA指标的主要特点是其更强的反应速度，对最新价格的变化更为敏感。因此，在价格快速变化的市场中，LWMA能够更快地反应价格趋势的变化。
 *
 * 交易者通常使用LWMA来识别价格的趋势方向和制定买卖信号。例如，当价格上穿LWMA时，可能暗示着上涨趋势的开始，为买入信号；相反，当价格下穿LWMA时，可能暗示着下跌趋势的开始，为卖出信号。
 *
 * 总的来说，LWMA是一种简单但有效的移动平均线指标，适用于快速市场和对价格变化更敏感的交易策略。然而，像所有技术指标一样，单独使用LWMA可能会导致误导，因此建议结合其他技术指标和价格模式进行综合分析。
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/l/linearlyweightedmovingaverage.asp">
 *      https://www.investopedia.com/terms/l/linearlyweightedmovingaverage.asp</a>
 */
public class LWMAIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;
    private final Num zero = numOf(0);

    public LWMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        Num sum = zero;
        Num denominator = zero;
        int count = 0;

        if ((index + 1) < barCount) {
            return zero;
        }

        int startIndex = (index - barCount) + 1;
        for (int i = startIndex; i <= index; i++) {
            count++;
            denominator = denominator.plus(numOf(count));
            sum = sum.plus(indicator.getValue(i).multipliedBy(numOf(count)));
        }
        return sum.dividedBy(denominator);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
