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
package org.ta4j.core.indicators.pivotpoints;

import static org.ta4j.core.num.NaN.NaN;

import java.util.List;

import org.ta4j.core.Bar;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Fibonacci Reversal Indicator.
 * 斐波那契反转指标。
 *
 * Fibonacci Reversal Indicator是一种技术分析工具，它基于斐波那契数列和黄金分割比率来识别资产价格可能发生反转的点位。这个指标利用了斐波那契数列中的特定比率，通常包括0.236、0.382、0.500、0.618和0.786等级，以及其他一些扩展级别如1.272、1.618等。
 *
 * Fibonacci Reversal Indicator通常用于绘制斐波那契回撤线或扩展线。斐波那契回撤线通常是由一段价格上升趋势中的高点和低点之间的水平距离按照斐波那契比率绘制出的水平线。扩展线则是根据斐波那契数列的比率在价格反转之后的新趋势中绘制的线。
 *
 * 这些水平线可以提供潜在的支撑和阻力水平，帮助交易者确定价格可能发生反转的点位。例如，当价格向上趋势时，如果价格回撤到了斐波那契回撤线的某个水平附近，可能会出现买入信号。反之，当价格向下趋势时，如果价格反弹到了斐波那契回撤线的某个水平附近，可能会出现卖出信号。
 *
 * Fibonacci Reversal Indicator也可以与其他技术指标结合使用，例如移动平均线、相对强弱指标等，以提供更全面的市场分析和交易信号。
 *
 * 总的来说，Fibonacci Reversal Indicator是一种常用的技术分析工具，它可以帮助交易者识别价格可能发生反转的点位，并提供潜在的交易信号，有助于制定更有效的交易策略。
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:pivot_points">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:pivot_points</a>
 */
public class FibonacciReversalIndicator extends RecursiveCachedIndicator<Num> {

    private final PivotPointIndicator pivotPointIndicator;
    private final FibReversalTyp fibReversalTyp;
    private final Num fibonacciFactor;

    public enum FibReversalTyp {
        SUPPORT, RESISTANCE
    }

    /**
     * Standard Fibonacci factors
     * 标准斐波那契因子
     */
    public enum FibonacciFactor {
        FACTOR_1(0.382), FACTOR_2(0.618), FACTOR_3(1);

        private final double factor;

        FibonacciFactor(double factor) {
            this.factor = factor;
        }

        public double getFactor() {
            return this.factor;
        }

    }

    /**
     * Constructor.
     *
     * Calculates a (fibonacci) reversal
     * 计算（斐波那契）反转
     *
     * @param pivotPointIndicator the {@link PivotPointIndicator} for this reversal
     *                            此反转的 {@link PivotPointIndicator}
     * @param fibonacciFactor     the fibonacci factor for this reversal
     *                            这种反转的斐波那契因子
     * @param fibReversalTyp      the FibonacciReversalIndicator.FibReversalTyp of  the reversal (SUPPORT, RESISTANCE)
     *                            反转的 FibonacciReversalIndicator.FibReversalTyp (SUPPORT, RESISTANCE)
     */
    public FibonacciReversalIndicator(PivotPointIndicator pivotPointIndicator, double fibonacciFactor,
            FibReversalTyp fibReversalTyp) {
        super(pivotPointIndicator);
        this.pivotPointIndicator = pivotPointIndicator;
        this.fibonacciFactor = numOf(fibonacciFactor);
        this.fibReversalTyp = fibReversalTyp;
    }

    /**
     * Constructor.
     *
     * Calculates a (fibonacci) reversal
     * 计算（斐波那契）反转
     *
     * @param pivotPointIndicator the {@link PivotPointIndicator} for this reversal
     *                            此反转的 {@link PivotPointIndicator}
     * @param fibonacciFactor     the {@link FibonacciFactor} factor for this  reversal
     *                            此反转的 {@link 斐波那契因子} 因子
     * @param fibReversalTyp      the FibonacciReversalIndicator.FibReversalTyp of  the reversal (SUPPORT, RESISTANCE)
     *                            反转的 FibonacciReversalIndicator.FibReversalTyp (SUPPORT, RESISTANCE)
     */
    public FibonacciReversalIndicator(PivotPointIndicator pivotPointIndicator, FibonacciFactor fibonacciFactor,
            FibReversalTyp fibReversalTyp) {
        this(pivotPointIndicator, fibonacciFactor.getFactor(), fibReversalTyp);
    }

    @Override
    protected Num calculate(int index) {
        List<Integer> barsOfPreviousPeriod = pivotPointIndicator.getBarsOfPreviousPeriod(index);
        if (barsOfPreviousPeriod.isEmpty())
            return NaN;
        Bar bar = getBarSeries().getBar(barsOfPreviousPeriod.get(0));
        Num high = bar.getHighPrice();
        Num low = bar.getLowPrice();
        for (int i : barsOfPreviousPeriod) {
            high = (getBarSeries().getBar(i).getHighPrice()).max(high);
            low = (getBarSeries().getBar(i).getLowPrice()).min(low);
        }

        if (fibReversalTyp == FibReversalTyp.RESISTANCE) {
            return pivotPointIndicator.getValue(index).plus(fibonacciFactor.multipliedBy(high.minus(low)));
        }
        return pivotPointIndicator.getValue(index).minus(fibonacciFactor.multipliedBy(high.minus(low)));
    }
}
