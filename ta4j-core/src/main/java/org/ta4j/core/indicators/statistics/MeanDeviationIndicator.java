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
 * Mean deviation indicator.
 * 平均偏差指标。
 *
 * 均值偏差指标（Mean Deviation Indicator）是一种用于衡量数据集中数据点与其平均值之间的偏差程度的指标。它是平均绝对偏差（Mean Absolute Deviation，MAD）的一种形式，用于描述数据的离散程度。
 *
 * 均值偏差指标的计算步骤如下：
 * 1. 计算数据集的平均值（均值）。
 * 2. 计算每个数据点与平均值之间的绝对偏差（即数据点与平均值之间的距离）。
 * 3. 将所有绝对偏差相加，并除以数据点的数量，得到平均绝对偏差。
 *
 * 数学公式如下：
 *
 * Mean Deviation = \frac{1}{n} \sum_{i=1}^{n} |x_i - \bar{x}| \]
 *
 * 其中：
 * - \( x_i \) 表示数据集中的第 \( i \) 个数据点。
 * - \( \bar{x} \) 表示数据集的平均值。
 * - \( n \) 表示数据点的数量。
 *
 * 均值偏差指标可以用来衡量数据的变异程度，即数据点在平均值周围的分散程度。较大的均值偏差值表示数据点相对于平均值更分散，而较小的值表示数据点更接近平均值。因此，均值偏差指标可用于比较不同数据集之间的变异程度，或者监测单个数据集的变化趋势。
 *
 * 在金融领域，均值偏差指标通常用于衡量资产价格的波动性或风险，以及投资组合的分散程度。
 *
 * @see <a href=
 *      "http://en.wikipedia.org/wiki/Mean_absolute_deviation#Average_absolute_deviation">
 *      http://en.wikipedia.org/wiki/Mean_absolute_deviation#Average_absolute_deviation</a>
 */
public class MeanDeviationIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;
    private final SMAIndicator sma;

    /**
     * Constructor.
     * 构造函数。
     *
     * @param indicator the indicator
     *                  指标
     * @param barCount  the time frame
     *                  時間範圍
     */
    public MeanDeviationIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
        sma = new SMAIndicator(indicator, barCount);
    }

    @Override
    protected Num calculate(int index) {
        Num absoluteDeviations = numOf(0);

        final Num average = sma.getValue(index);
        final int startIndex = Math.max(0, index - barCount + 1);
        final int nbValues = index - startIndex + 1;

        for (int i = startIndex; i <= index; i++) {
            // For each period...
            // 对于每个周期...
            absoluteDeviations = absoluteDeviations.plus(indicator.getValue(i).minus(average).abs());
        }
        return absoluteDeviations.dividedBy(numOf(nbValues));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
