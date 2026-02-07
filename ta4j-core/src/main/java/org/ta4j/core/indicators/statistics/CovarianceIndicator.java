/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.num.Num;

/**
 * Covariance indicator.
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
     * @param indicator2 the second indicator
     * @param barCount   the time frame
     */
    public CovarianceIndicator(Indicator<Num> indicator1, Indicator<Num> indicator2, int barCount) {
        super(indicator1);
        this.indicator1 = indicator1;
        this.indicator2 = indicator2;
        this.barCount = barCount;
        this.sma1 = new SMAIndicator(indicator1, barCount);
        this.sma2 = new SMAIndicator(indicator2, barCount);
    }

    @Override
    protected Num calculate(int index) {
        final int startIndex = Math.max(0, index - barCount + 1);
        final int numberOfObservations = index - startIndex + 1;
        Num covariance = getBarSeries().numFactory().zero();
        Num average1 = sma1.getValue(index);
        Num average2 = sma2.getValue(index);
        for (int i = startIndex; i <= index; i++) {
            Num mul = indicator1.getValue(i).minus(average1).multipliedBy(indicator2.getValue(i).minus(average2));
            covariance = covariance.plus(mul);
        }
        covariance = covariance.dividedBy(getBarSeries().numFactory().numOf(numberOfObservations));
        return covariance;
    }

    @Override
    public int getCountOfUnstableBars() {
        int baseUnstableBars = Math.max(indicator1.getCountOfUnstableBars(), indicator2.getCountOfUnstableBars());
        return baseUnstableBars + barCount - 1;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
