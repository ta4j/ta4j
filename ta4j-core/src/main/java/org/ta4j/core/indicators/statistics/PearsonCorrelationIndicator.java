/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Indicator-Pearson-Correlation
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
     * @param indicator2 the second indicator
     * @param barCount   the time frame
     */
    public PearsonCorrelationIndicator(Indicator<Num> indicator1, Indicator<Num> indicator2, int barCount) {
        super(indicator1);
        this.indicator1 = indicator1;
        this.indicator2 = indicator2;
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {

        final var numFactory = getBarSeries().numFactory();
        Num n = numFactory.numOf(barCount);

        Num zero = numFactory.zero();
        Num Sx = zero;
        Num Sy = zero;
        Num Sxx = zero;
        Num Syy = zero;
        Num Sxy = zero;

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

        if (toSqrt.isGreaterThan(numFactory.zero())) {
            // pearson = (n * Sxy - Sx * Sy) / sqrt((n * Sxx - Sx * Sx) * (n * Syy - Sy *
            // Sy))
            return (n.multipliedBy(Sxy).minus(Sx.multipliedBy(Sy))).dividedBy(toSqrt.sqrt());
        }

        return NaN;
    }

    @Override
    public int getCountOfUnstableBars() {
        return barCount;
    }
}
