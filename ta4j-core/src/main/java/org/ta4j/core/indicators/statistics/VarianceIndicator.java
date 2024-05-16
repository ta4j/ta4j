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
package org.ta4j.core.indicators.statistics;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.num.Num;

/**
 * Variance indicator.
 * 方差指标。
 *
 * 方差指标是用于衡量一组数据的离散程度或变异程度的统计量。它表示数据点与数据集均值之间的平均偏差的平方，反映了数据的分布情况。
 *
 * 方差的计算公式如下：
 *
 * \[ \text{方差} = \frac{1}{n} \sum_{i=1}^{n} (x_i - \bar{x})^2 \]
 *
 * 其中：
 * - \( x_i \) 是数据集中的第 \( i \) 个数据点。
 * - \( \bar{x} \) 是数据集的均值。
 * - \( n \) 是数据点的数量。
 *
 * 方差的值越大，表示数据点相对于均值的偏离程度越大，即数据的分散程度越高。而方差的值越小，表示数据点相对于均值的偏离程度越小，即数据的集中程度越高。
 *
 * 方差是描述数据分布的重要统计量之一，它在金融领域和其他领域都有广泛的应用。在金融领域，方差常用于衡量资产价格的波动性或风险。例如，股票价格的日收益率的方差可以用来衡量股票的波动性，从而评估其风险水平。方差也可以用于构建投资组合，帮助投资者在风险和回报之间进行权衡。
 *
 */
public class VarianceIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;
    private final SMAIndicator sma;

    /**
     * Constructor.
     * 
     * @param indicator the indicator 指標
     * @param barCount  the time frame 時間範圍
     */
    public VarianceIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
        this.sma = new SMAIndicator(indicator, barCount);
    }

    @Override
    protected Num calculate(int index) {
        final int startIndex = Math.max(0, index - barCount + 1);
        final int numberOfObservations = index - startIndex + 1;
        Num variance = numOf(0);
        Num average = sma.getValue(index);
        for (int i = startIndex; i <= index; i++) {
            Num pow = indicator.getValue(i).minus(average).pow(2);
            variance = variance.plus(pow);
        }
        variance = variance.dividedBy(numOf(numberOfObservations));
        return variance;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
