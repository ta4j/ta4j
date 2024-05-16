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
 * Covariance indicator.
 * 协方差指标。
 *
 * 协方差指标（Covariance Indicator）用于衡量两个随机变量之间的关系强度和方向。在金融领域，协方差通常用于分析两个资产价格之间的关系。
 *
 * 协方差表示两个随机变量的变化趋势是如何一起变化的。如果两个变量的变化趋势是一致的（即同时增加或同时减少），则它们的协方差为正数；如果它们的变化趋势相反（一个增加而另一个减少），则协方差为负数；如果它们之间没有明显的变化趋势，则协方差接近于零。
 *
 * 协方差的计算公式如下：
 *
 * Cov(X, Y) = 1/n  sum_i=1^n (x_i -x)(y_i - y)
 *
 * 其中：
 * - \( X \) 和 \( Y \) 是两个随机变量。
 * - \( x_i \) 和 \( y_i \) 分别是样本 \( i \) 的观察值。
 * - \( \bar{x} \) 和 \( \bar{y} \) 分别是 \( X \) 和 \( Y \) 的样本均值。
 * - \( n \) 是样本的数量。
 *
 * 协方差指标的数值大小表示两个随机变量之间的关系强度，但它的数值受到变量单位的影响，因此很难进行比较。通常，人们更倾向于使用标准化的指标，例如相关系数，来衡量两个变量之间的关系。
 *
 * 尽管如此，协方差指标仍然在金融分析中有其价值，特别是在分析资产价格之间的关系和构建投资组合时，它可以提供一些有用的信息。
 *
 */
public class CovarianceIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator1;
    private final Indicator<Num> indicator2;
    private final int barCount;
    private final SMAIndicator sma1;
    private final SMAIndicator sma2;

    /**
     * Constructor.
     *
     * @param indicator1 the first indicator
     *                   第一个指标
     * @param indicator2 the second indicator
     *                   第二个指标
     * @param barCount   the time frame
     *                   时间范围
     */
    public CovarianceIndicator(Indicator<Num> indicator1, Indicator<Num> indicator2, int barCount) {
        super(indicator1);
        this.indicator1 = indicator1;
        this.indicator2 = indicator2;
        this.barCount = barCount;
        sma1 = new SMAIndicator(indicator1, barCount);
        sma2 = new SMAIndicator(indicator2, barCount);
    }

    @Override
    protected Num calculate(int index) {
        final int startIndex = Math.max(0, index - barCount + 1);
        final int numberOfObservations = index - startIndex + 1;
        Num covariance = numOf(0);
        Num average1 = sma1.getValue(index);
        Num average2 = sma2.getValue(index);
        for (int i = startIndex; i <= index; i++) {
            Num mul = indicator1.getValue(i).minus(average1).multipliedBy(indicator2.getValue(i).minus(average2));
            covariance = covariance.plus(mul);
        }
        covariance = covariance.dividedBy(numOf(numberOfObservations));
        return covariance;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
