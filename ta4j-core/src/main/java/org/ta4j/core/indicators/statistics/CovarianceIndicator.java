/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Covariance indicator.
 * <p>
 * Zero-origin warm-up retains legacy partial-window calculations. After either
 * source loses history, incomplete retained windows return {@link NaN#NaN}. The
 * stability boundary includes both sources and their retained begin indexes.
 */
public class CovarianceIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator1;
    private final Indicator<Num> indicator2;
    private final int barCount;

    /**
     * Constructor for indicators backed by a shared bar series.
     *
     * @param series     the bar series underlying both indicators
     * @param indicator1 the first indicator
     * @param indicator2 the second indicator
     * @param barCount   the time frame
     * @since 0.24.3
     */
    public CovarianceIndicator(BarSeries series, Indicator<Num> indicator1, Indicator<Num> indicator2, int barCount) {
        super(series, indicator1, indicator2);
        this.indicator1 = indicator1;
        this.indicator2 = indicator2;
        this.barCount = Math.max(1, barCount);
    }

    /**
     * Constructor. The underlying series is the first indicator's series.
     *
     * @param indicator1 the first indicator
     * @param indicator2 the second indicator
     * @param barCount   the time frame
     */
    public CovarianceIndicator(Indicator<Num> indicator1, Indicator<Num> indicator2, int barCount) {
        this(indicator1.getBarSeries(), indicator1, indicator2, barCount);
    }

    @Override
    protected Num calculate(int index) {
        int retainedBegin = Math.max(getBarSeries().getBeginIndex(),
                Math.max(indicator1.getBarSeries().getBeginIndex(), indicator2.getBarSeries().getBeginIndex()));
        if (retainedBegin > 0 && index < stableBoundary()) {
            return NaN.NaN;
        }
        final int startIndex = (int) Math.max(Math.max(0L, retainedBegin), (long) index - barCount + 1L);
        final int numberOfObservations = index - startIndex + 1;
        Num firstAnchor = indicator1.getValue(startIndex);
        Num secondAnchor = indicator2.getValue(startIndex);
        Num firstAverageOffset = getBarSeries().numFactory().zero();
        Num secondAverageOffset = getBarSeries().numFactory().zero();
        Num coDeviationTotal = getBarSeries().numFactory().zero();
        int observations = 1;
        // The online co-moment uses the same retained window as variance, reads
        // each source once, and avoids unrelated SMA warm-up and terminal loops.
        for (long i = (long) startIndex + 1L; i <= (long) index; i++) {
            Num firstOffset = indicator1.getValue((int) i).minus(firstAnchor);
            Num secondOffset = indicator2.getValue((int) i).minus(secondAnchor);
            observations++;
            Num firstDifference = firstOffset.minus(firstAverageOffset);
            Num secondDifference = secondOffset.minus(secondAverageOffset);
            Num count = getBarSeries().numFactory().numOf(observations);
            firstAverageOffset = firstAverageOffset.plus(firstDifference.dividedBy(count));
            secondAverageOffset = secondAverageOffset.plus(secondDifference.dividedBy(count));
            coDeviationTotal = coDeviationTotal
                    .plus(firstDifference.multipliedBy(secondOffset.minus(secondAverageOffset)));
        }
        return coDeviationTotal.dividedBy(getBarSeries().numFactory().numOf(numberOfObservations));
    }

    @Override
    public int getCountOfUnstableBars() {
        return CorrelationWindowSupport.clampUnstableBars(stableBoundary());
    }

    private long stableBoundary() {
        long firstStart = Math.max(Math.max(0L, indicator1.getBarSeries().getBeginIndex()),
                indicator1.getCountOfUnstableBars());
        long secondStart = Math.max(Math.max(0L, indicator2.getBarSeries().getBeginIndex()),
                indicator2.getCountOfUnstableBars());
        long retainedStart = Math.max(Math.max(firstStart, secondStart), getBarSeries().getBeginIndex());
        return retainedStart + (long) barCount - 1L;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
