/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.num.Num;

/**
 * Variance indicator.
 */
public class VarianceIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;
    private final SMAIndicator sma;

    /**
     * Constructor.
     *
     * @param indicator the indicator
     * @param barCount  the time frame
     */
    public VarianceIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
        this.sma = new SMAIndicator(indicator, barCount);
    }

    @Override
    protected Num calculate(int index) {
        final int startIndex = Math.max(0, index - barCount + 1);
        final int numberOfObservations = index - startIndex + 1;
        final var numFactory = getBarSeries().numFactory();
        Num variance = numFactory.zero();
        Num average = sma.getValue(index);
        for (int i = startIndex; i <= index; i++) {
            Num pow = indicator.getValue(i).minus(average).pow(2);
            variance = variance.plus(pow);
        }
        variance = variance.dividedBy(numFactory.numOf(numberOfObservations));
        return variance;
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
