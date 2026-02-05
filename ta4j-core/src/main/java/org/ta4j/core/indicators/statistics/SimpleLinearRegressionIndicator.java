/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
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
    }

    @Override
    protected Num calculate(int index) {
        final int startIndex = Math.max(0, index - barCount + 1);
        if (index - startIndex + 1 < 2) {
            // Not enough observations to compute a regression line
            return NaN;
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
