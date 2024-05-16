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
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Simple linear regression indicator.
 * 简单的线性回归指标。
 *
 * A moving (i.e. over the time frame) simple linear regression (least squares).
 * * 移动（即在时间范围内）简单线性回归（最小二乘）。
 * y = slope * x + intercept See also:
 * y = 斜率 * x + 截距 另见：
 * http://introcs.cs.princeton.edu/java/97data/LinearRegression.java.html
 *
 *
 * 简单线性回归指标是一种统计方法，用于分析两个变量之间的线性关系，并通过拟合一条直线来描述它们之间的关系。这种方法通常用于预测一个变量（称为因变量）如何随着另一个变量（称为自变量）的变化而变化。
 *
 * 简单线性回归模型的数学形式如下：
 *
 *   y =  beta_0 +  beta_1x + varepsilon
 *
 * 其中：
 * - \( y \) 是因变量的值。
 * - \( x \) 是自变量的值。
 * - \( \beta_0 \) 是截距（模型的纵轴截距）。
 * - \( \beta_1 \) 是斜率（自变量对因变量的影响）。
 * - \( \varepsilon \) 是误差项（未被模型解释的随机误差）。
 *
 * 简单线性回归模型的目标是找到最适合数据的直线，使得观测值与预测值之间的残差（观测值与直线之间的垂直距离）之和最小。这通常通过最小二乘法来实现，即最小化残差平方和。
 *
 * 简单线性回归模型可用于估计自变量和因变量之间的关系，预测因变量的值，并评估预测结果的准确性。在金融领域，简单线性回归模型常用于分析市场数据，例如分析股票收益与某个指数之间的关系，或者分析经济指标对股票市场的影响等。
 *
 */
public class SimpleLinearRegressionIndicator extends CachedIndicator<Num> {

    /**
     * The type for the outcome of the {@link SimpleLinearRegressionIndicator}
     * {@link SimpleLinearRegressionIndicator} 结果的类型
     */
    public enum SimpleLinearRegressionType {
        Y, SLOPE, INTERCEPT
    }

    private Indicator<Num> indicator;
    private int barCount;
    private Num slope;
    private Num intercept;
    private SimpleLinearRegressionType type;

    /**
     * Constructor for the y-values of the formula (y = slope * x + intercept).
     * * 公式 y 值的构造函数（y = 斜率 * x + 截距）。
     *
     * @param indicator the indicator for the x-values of the formula.
     *                  公式的 x 值的指标。
     * @param barCount  the time frame
     *                  时间范围
     */
    public SimpleLinearRegressionIndicator(Indicator<Num> indicator, int barCount) {
        this(indicator, barCount, SimpleLinearRegressionType.Y);
    }

    /**
     * Constructor.
     *
     * @param indicator the indicator for the x-values of the formula.
     *                  公式的 x 值的指标。
     * @param barCount  the time frame
     *                  时间范围
     * @param type      the type of the outcome value (y, slope, intercept)
     *                  结果值的类型（y、斜率、截距）
     */
    public SimpleLinearRegressionIndicator(Indicator<Num> indicator, int barCount, SimpleLinearRegressionType type) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
        this.type = type;
    }

    @Override
    protected Num calculate(int index) {
        final int startIndex = Math.max(0, index - barCount + 1);
        if (index - startIndex + 1 < 2) {
            // Not enough observations to compute a regression line
            // 没有足够的观测值来计算回归线
            return NaN;
        }
        calculateRegressionLine(startIndex, index);

        if (type == SimpleLinearRegressionType.SLOPE) {
            return slope;
        }

        if (type == SimpleLinearRegressionType.INTERCEPT) {
            return intercept;
        }

        return slope.multipliedBy(numOf(index)).plus(intercept);
    }

    /**
     * Calculates the regression line.
     * 计算回归线。
     *
     * @param startIndex the start index (inclusive) in the bar series
     *                   条形系列中的起始索引（包括）
     * @param endIndex   the end index (inclusive) in the bar series
     *                   条形系列中的结束索引（包括）
     */
    private void calculateRegressionLine(int startIndex, int endIndex) {
        // First pass: compute xBar and yBar
        // 第一遍：计算 xBar 和 yBar
        Num sumX = numOf(0);
        Num sumY = numOf(0);
        for (int i = startIndex; i <= endIndex; i++) {
            sumX = sumX.plus(numOf(i));
            sumY = sumY.plus(indicator.getValue(i));
        }
        Num nbObservations = numOf(endIndex - startIndex + 1);
        Num xBar = sumX.dividedBy(nbObservations);
        Num yBar = sumY.dividedBy(nbObservations);

        // Second pass: compute slope and intercept
        // 第二遍：计算斜率和截距
        Num xxBar = numOf(0);
        Num xyBar = numOf(0);
        for (int i = startIndex; i <= endIndex; i++) {
            Num dX = numOf(i).minus(xBar);
            Num dY = indicator.getValue(i).minus(yBar);
            xxBar = xxBar.plus(dX.multipliedBy(dX));
            xyBar = xyBar.plus(dX.multipliedBy(dY));
        }

        slope = xyBar.dividedBy(xxBar);
        intercept = yBar.minus(slope.multipliedBy(xBar));
    }
}
