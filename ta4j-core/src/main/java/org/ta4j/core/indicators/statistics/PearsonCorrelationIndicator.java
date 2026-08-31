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
        super(indicator1, indicator2);
        this.indicator1 = indicator1;
        this.indicator2 = indicator2;
        this.barCount = barCount;
    }

    /**
     * The correlation over a fixed window reads only retained bars, so every cached
     * value stays recomputable from the retained window after a head advance: the
     * unstable-range floor applies instead of keeping every cached value.
     */
    @Override
    protected boolean hasRecursiveDependencies() {
        return false;
    }

    @Override
    protected Num calculate(int index) {

        final var numFactory = getBarSeries().numFactory();
        final int beginIndex = Math.max(0, getBarSeries().getBeginIndex());
        final int start = Math.max(beginIndex, index - barCount + 1);
        // Once bar eviction has advanced the begin index, the retained window may be
        // smaller than the requested barCount; normalize by the number of values
        // actually iterated. The warm-up phase keeps the historical barCount
        // normalization for backward compatibility.
        final int normalizationCount = beginIndex > 0 ? index - start + 1 : barCount;
        Num n = numFactory.numOf(normalizationCount);

        Num zero = numFactory.zero();
        Num Sx = zero;
        Num Sy = zero;
        Num Sxx = zero;
        Num Syy = zero;
        Num Sxy = zero;

        for (int i = start; i <= index; i++) {

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
        int baseUnstableBars = Math.max(indicator1.getCountOfUnstableBars(), indicator2.getCountOfUnstableBars());
        return baseUnstableBars + barCount - 1;
    }
}
