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

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Indicator-Pearson-Correlation
 * 指标-皮尔逊相关
 *
 * Pearson相关系数是一种用于衡量两个变量之间线性关系强度和方向的统计指标。它通常用于描述两个变量之间的线性相关程度，取值范围在 -1 到 1 之间。
 *
 * Pearson相关系数的计算公式如下：
 *
 * \[ r_{xy} = \frac{\sum_{i=1}^{n} (x_i - \bar{x})(y_i - \bar{y})}{\sqrt{\sum_{i=1}^{n} (x_i - \bar{x})^2} \times \sqrt{\sum_{i=1}^{n} (y_i - \bar{y})^2}} \]
 *
 * 其中：
 * - \( r_{xy} \) 表示变量 \( X \) 和 \( Y \) 之间的Pearson相关系数。
 * - \( x_i \) 和 \( y_i \) 分别表示变量 \( X \) 和 \( Y \) 的第 \( i \) 个观察值。
 * - \( \bar{x} \) 和 \( \bar{y} \) 分别表示变量 \( X \) 和 \( Y \) 的样本均值。
 * - \( n \) 表示样本的数量。
 *
 * Pearson相关系数为正数表示两个变量之间存在正相关关系，即一个变量的增加伴随着另一个变量的增加；为负数表示两个变量之间存在负相关关系，即一个变量的增加伴随着另一个变量的减少；接近于零表示两个变量之间几乎没有线性关系。
 *
 * 在金融领域，Pearson相关系数常用于衡量不同资产价格之间的相关性，以及资产价格与其他因素（如利率、通胀率等）之间的关联程度。它是一种常用的统计工具，用于帮助分析师和交易者理解市场中不同因素之间的关系，并做出相应的决策。
 *
 * @see <a href=
 *      "http://www.statisticshowto.com/probability-and-statistics/correlation-coefficient-formula/">
 *      http://www.statisticshowto.com/probability-and-statistics/correlation-coefficient-formula/</a>
 */
public class PearsonCorrelationIndicator extends RecursiveCachedIndicator<Num> {

    private final Indicator<Num> indicator1;
    private final Indicator<Num> indicator2;
    private final int barCount;

    /**
     * Constructor.
     *
     * @param indicator1 the first indicator
     *                   第一个指标
     * @param indicator2 the second indicator
     *                   第二个指标
     * @param barCount   the time frame
     *                   時間範圍
     */
    public PearsonCorrelationIndicator(Indicator<Num> indicator1, Indicator<Num> indicator2, int barCount) {
        super(indicator1);
        this.indicator1 = indicator1;
        this.indicator2 = indicator2;
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) { //計算

        Num n = numOf(barCount);

        Num Sx = numOf(0);
        Num Sy = numOf(0);
        Num Sxx = numOf(0);
        Num Syy = numOf(0);
        Num Sxy = numOf(0);

        for (int i = Math.max(getBarSeries().getBeginIndex(), index - barCount + 1); i <= index; i++) {

            Num x = indicator1.getValue(i);
            Num y = indicator2.getValue(i);

            Sx = Sx.plus(x);
            Sy = Sy.plus(y);
            Sxy = Sxy.plus(x.multipliedBy(y));
            Sxx = Sxx.plus(x.multipliedBy(x));
            Syy = Syy.plus(y.multipliedBy(y));
        }

        // (n * Sxx - Sx * Sx) * (n * Syy - Sy * Sy)
        Num toSqrt = (n.multipliedBy(Sxx).minus(Sx.multipliedBy(Sx)))
                .multipliedBy(n.multipliedBy(Syy).minus(Sy.multipliedBy(Sy)));

        if (toSqrt.isGreaterThan(numOf(0))) {
            // pearson = (n * Sxy - Sx * Sy) / sqrt((n * Sxx - Sx * Sx) * (n * Syy - Sy *
            // Sy))
            return (n.multipliedBy(Sxy).minus(Sx.multipliedBy(Sy))).dividedBy(toSqrt.sqrt());
        }

        return NaN;
    }
}
