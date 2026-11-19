/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.RunningTotalIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Least Squares Moving Average (LSMA) Indicator.
 *
 * Least Squares Moving Average (LSMA), also known as the Linear Regression Line
 * or End Point Moving Average, is a unique type of moving average that
 * minimizes the sum of squared differences between data points and a regression
 * line. Unlike other moving averages that calculate a simple or weighted
 * average of prices, the LSMA fits a straight line to the price data over a
 * specific period and uses the end point of that line as the average. This
 * makes LSMA effective at forecasting trends while reducing lag, making it
 * popular in technical analysis.
 *
 * slope = (N * ∑(X * Y) - ∑(X) * ∑(Y)) / (N * ∑(X^2) - (∑(X))^2)
 *
 * intercept = (∑(Y) - slope * ∑(X)) / N
 *
 * lsma = slope * x + intercept
 *
 */
public class LSMAIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;
    private final NumFactory numFactory;
    private final transient LWMAIndicator weightedAverage;
    private final transient RunningTotalIndicator total;

    /**
     * Constructor.
     *
     * @param indicator an indicator
     * @param barCount  the moving average time window
     */
    public LSMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);

        this.indicator = indicator;
        this.barCount = barCount;
        this.numFactory = indicator.getBarSeries().numFactory();
        this.weightedAverage = new LWMAIndicator(indicator, barCount);
        this.total = new RunningTotalIndicator(indicator, barCount);
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }

        Num fast = calculateRolling(index);
        if (fast != null) {
            return fast;
        }

        Num zero = numFactory.zero();
        Num sumX = zero;
        Num sumY = zero;
        Num sumXY = zero;
        Num sumX2 = zero;

        int startIdx = index - barCount + 1;
        for (int i = 0; i < barCount; i++) {
            int currentIndex = startIdx + i;
            Num x = numFactory.numOf(i + 1); // 1-based index for X
            Num y = indicator.getValue(currentIndex); // Y values are prices

            sumX = sumX.plus(x);
            sumY = sumY.plus(y);
            sumXY = sumXY.plus(x.multipliedBy(y));
            sumX2 = sumX2.plus(x.multipliedBy(x));
        }

        Num numBarCount = numFactory.numOf(barCount);

        // Calculate slope
        Num numerator = numBarCount.multipliedBy(sumXY).minus(sumX.multipliedBy(sumY));
        Num denominator = numBarCount.multipliedBy(sumX2).minus(sumX.multipliedBy(sumX));

        if (denominator.isZero()) {
            return zero;
        }

        Num slope = numerator.dividedBy(denominator);
        Num intercept = sumY.minus(slope.multipliedBy(sumX)).dividedBy(numBarCount);

        return slope.multipliedBy(numBarCount).plus(intercept);
    }

    private Num calculateRolling(int index) {
        int start = index - barCount + 1;
        if (barCount <= 1 || barCount > 10000 || start < Math.max(0, getBarSeries().getBeginIndex())
                || !RollingWeighted.supports(numFactory.zero())) {
            return null;
        }
        try {
            long count = barCount;
            Num n = numFactory.numOf(count);
            Num sumX = numFactory.numOf(count * (count + 1) / 2);
            Num sumX2 = numFactory.numOf(count * (count + 1) * (2 * count + 1) / 6);
            Num sumY = total.getValue(index);
            Num sumXY = weightedAverage.getValue(index).multipliedBy(sumX);
            Num first = n.multipliedBy(sumXY);
            Num second = sumX.multipliedBy(sumY);
            Num numerator = first.minus(second);
            Num denominator = n.multipliedBy(sumX2).minus(sumX.multipliedBy(sumX));
            if (!Num.isFinite(sumY) || !Num.isFinite(sumXY) || !Num.isFinite(numerator) || !Num.isFinite(denominator)
                    || denominator.isZero() || numerator.abs()
                            .isLessThanOrEqual(first.abs().max(second.abs()).dividedBy(numFactory.numOf(100000000)))) {
                return null;
            }
            Num slope = numerator.dividedBy(denominator);
            Num intercept = sumY.minus(slope.multipliedBy(sumX)).dividedBy(n);
            Num result = slope.multipliedBy(n).plus(intercept);
            return Num.isFinite(result) ? result : null;
        } catch (ArithmeticException e) {
            return null;
        }
    }

    @Override
    public int getCountOfUnstableBars() {
        return indicator.getCountOfUnstableBars() + barCount - 1;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
