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
import org.ta4j.core.num.Num;

/**
 * Standard deviation indicator.
 * 标准偏差指标。
 *
 * 标准差指标是用于衡量数据集中数据点分散程度的统计指标。它表示数据点与数据集平均值之间的平均偏差程度。标准差越大，表示数据点越分散；标准差越小，表示数据点越集中。
 *
 * 标准差的计算公式如下：
 *
 * \[ \text{标准差} = \sqrt{\frac{1}{n} \sum_{i=1}^{n} (x_i - \bar{x})^2} \]
 *
 * 其中：
 * - \( x_i \) 是数据集中的第 \( i \) 个数据点。
 * - \( \bar{x} \) 是数据集的平均值。
 * - \( n \) 是数据点的数量。
 *
 * 标准差指标提供了关于数据点分布的重要信息。较大的标准差表示数据点相对于平均值的偏离程度较大，即数据的分散程度较高；较小的标准差表示数据点相对于平均值的偏离程度较小，即数据的集中程度较高。
 *
 * 在金融领域，标准差常用于衡量资产价格的波动性或风险。例如，股票的日收益率的标准差可以用来衡量股票的波动性，从而评估其风险水平。标准差也可以用于构建投资组合，帮助投资者在风险和回报之间进行权衡。
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:standard_deviation_volatility">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:standard_deviation_volatility</a>
 */
public class StandardDeviationIndicator extends CachedIndicator<Num> {

    private final VarianceIndicator variance;

    /**
     * Constructor.
     *
     * @param indicator the indicator 指標
     * @param barCount  the time frame 時間範圍
     */
    public StandardDeviationIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        variance = new VarianceIndicator(indicator, barCount);
    }

    @Override
    protected Num calculate(int index) {
        return variance.getValue(index).sqrt();
    }
}
