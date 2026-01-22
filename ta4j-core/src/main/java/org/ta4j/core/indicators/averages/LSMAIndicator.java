/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

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

    /**
     * Constructor.
     *
     * @param indicator an indicator
     * @param barCount  the moving average time window
     */
    public LSMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator.getBarSeries());
        this.indicator = indicator;
        this.barCount = barCount;
        this.numFactory = indicator.getBarSeries().numFactory();
    }

    @Override
    protected Num calculate(int index) {
        if (index < barCount - 1) {
            return indicator.getValue(index); // Not enough data points
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

    @Override
    public int getCountOfUnstableBars() {
        return barCount;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
