/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.averages.LWMAIndicator;
import org.ta4j.core.indicators.helpers.RunningTotalIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;

/**
 * Simple linear regression indicator.
 *
 * <p>
 * A moving (i.e. over the time frame) simple linear regression (least squares).
 *
 * <pre>
 * y = slope * x + intercept
 * </pre>
 *
 * see <a href=
 * "http://introcs.cs.princeton.edu/java/97data/LinearRegression.java.html">LinearRegression</a>
 */
public class SimpleLinearRegressionIndicator extends CachedIndicator<Num> {

    /**
     * The type for the outcome of the {@link SimpleLinearRegressionIndicator}.
     */
    public enum SimpleLinearRegressionType {
        Y, SLOPE, INTERCEPT
    }

    private final Indicator<Num> indicator;
    private final int barCount;
    private Num slope;
    private Num intercept;
    private final SimpleLinearRegressionType type;
    private final transient LWMAIndicator weightedAverage;
    private final transient RunningTotalIndicator total;
    private final transient RunningTotalIndicator prefixWeighted;

    /**
     * Constructor for the y-values of the formula (y = slope * x + intercept).
     *
     * @param indicator the indicator for the x-values of the formula.
     * @param barCount  the time frame
     */
    public SimpleLinearRegressionIndicator(Indicator<Num> indicator, int barCount) {
        this(indicator, barCount, SimpleLinearRegressionType.Y);
    }

    /**
     * Constructor.
     *
     * @param indicator the indicator for the x-values of the formula.
     * @param barCount  the time frame
     * @param type      the type of the outcome value (y, slope, intercept)
     */
    public SimpleLinearRegressionIndicator(Indicator<Num> indicator, int barCount, SimpleLinearRegressionType type) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
        this.type = type;
        this.weightedAverage = new LWMAIndicator(indicator, barCount);
        this.total = new RunningTotalIndicator(indicator, barCount);
        this.prefixWeighted = new RunningTotalIndicator(new CachedIndicator<Num>(indicator) {
            @Override
            protected Num calculate(int index) {
                return indicator.getValue(index).multipliedBy(getBarSeries().numFactory().numOf((long) index + 1));
            }

            @Override
            public int getCountOfUnstableBars() {
                return indicator.getCountOfUnstableBars();
            }
        }, barCount);
    }

    @Override
    protected Num calculate(int index) {
        final int startIndex = Math.max(Math.max(0, getBarSeries().getBeginIndex()), index - barCount + 1);
        if (index - startIndex + 1 < 2) {
            // Not enough observations to compute a regression line
            return NaN;
        }
        Num rolling = calculateRolling(startIndex, index);
        if (rolling != null) {
            return rolling;
        }
        calculateRegressionLine(startIndex, index);

        if (type == SimpleLinearRegressionType.SLOPE) {
            return slope;
        }

        if (type == SimpleLinearRegressionType.INTERCEPT) {
            return intercept;
        }

        return slope.multipliedBy(getBarSeries().numFactory().numOf(index)).plus(intercept);
    }

    private Num calculateRolling(int start, int index) {
        var factory = getBarSeries().numFactory();
        int count = index - start + 1;
        boolean fullWindow = count == barCount && index >= weightedAverage.getCountOfUnstableBars();
        if (barCount < 2 || barCount > 10000 || index > 1000000 || (!fullWindow && start != 0)
                || (factory.zero() instanceof DecimalNum decimal && decimal.getMathContext().getPrecision() < 16)) {
            return null;
        }
        try {
            long observations = count;
            Num n = factory.numOf(observations);
            Num sumX = factory.numOf(observations * (observations + 1) / 2);
            Num sumX2 = factory.numOf(observations * (observations + 1) * (2 * observations + 1) / 6);
            Num sumY = total.getValue(index);
            Num sumXY = fullWindow ? weightedAverage.getValue(index).multipliedBy(sumX)
                    : prefixWeighted.getValue(index);
            Num first = n.multipliedBy(sumXY);
            Num second = sumX.multipliedBy(sumY);
            Num numerator = first.minus(second);
            Num denominator = n.multipliedBy(sumX2).minus(sumX.multipliedBy(sumX));
            if (!Num.isFinite(sumY) || !Num.isFinite(sumXY) || !Num.isFinite(numerator) || !Num.isFinite(denominator)
                    || denominator.isZero() || numerator.abs()
                            .isLessThanOrEqual(first.abs().max(second.abs()).dividedBy(factory.numOf(100000000)))) {
                return null;
            }
            Num lineSlope = numerator.dividedBy(denominator);
            Num xBar = factory.numOf(start).plus(factory.numOf(count - 1).dividedBy(factory.numOf(2)));
            Num lineIntercept = sumY.dividedBy(n).minus(lineSlope.multipliedBy(xBar));
            Num result = type == SimpleLinearRegressionType.SLOPE ? lineSlope
                    : type == SimpleLinearRegressionType.INTERCEPT ? lineIntercept
                            : lineSlope.multipliedBy(factory.numOf(index)).plus(lineIntercept);
            return Num.isFinite(result) ? result : null;
        } catch (ArithmeticException e) {
            return null;
        }
    }

    @Override
    public int getCountOfUnstableBars() {
        return indicator.getCountOfUnstableBars() + Math.max(1, barCount - 1);
    }

    /**
     * Calculates the regression line.
     *
     * @param startIndex the start index (inclusive) in the bar series
     * @param endIndex   the end index (inclusive) in the bar series
     */
    private void calculateRegressionLine(int startIndex, int endIndex) {
        final var numFactory = getBarSeries().numFactory();
        Num zero = numFactory.zero();
        // First pass: compute xBar and yBar
        Num sumX = zero;
        Num sumY = zero;
        for (int i = startIndex; i <= endIndex; i++) {
            sumX = sumX.plus(numFactory.numOf(i));
            sumY = sumY.plus(indicator.getValue(i));
        }
        Num nbObservations = numFactory.numOf(endIndex - startIndex + 1);
        Num xBar = sumX.dividedBy(nbObservations);
        Num yBar = sumY.dividedBy(nbObservations);

        // Second pass: compute slope and intercept
        Num xxBar = zero;
        Num xyBar = zero;
        for (int i = startIndex; i <= endIndex; i++) {
            Num dX = numFactory.numOf(i).minus(xBar);
            Num dY = indicator.getValue(i).minus(yBar);
            xxBar = xxBar.plus(dX.multipliedBy(dX));
            xyBar = xyBar.plus(dX.multipliedBy(dY));
        }

        slope = xyBar.dividedBy(xxBar);
        intercept = yBar.minus(slope.multipliedBy(xBar));
    }
}
