/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Rolling distance correlation indicator.
 *
 * <p>
 * Distance correlation measures linear and non-linear dependence by comparing
 * centered pairwise distance matrices for the two rolling windows. Unlike
 * Pearson correlation, it can detect relationships where association is not
 * well represented by a straight line.
 * </p>
 *
 * <p>
 * This implementation is {@code O(barCount^2)} for each calculated index
 * because it builds the pairwise distance matrices inside the rolling window.
 * Prefer modest windows for large backtests.
 * </p>
 *
 * @since 0.22.7
 */
public class DistanceCorrelationIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> first;
    private final Indicator<Num> second;
    private final int barCount;

    /**
     * Constructor.
     *
     * @param first    first numeric indicator
     * @param second   second numeric indicator
     * @param barCount rolling window length, must be at least 2
     * @throws IllegalArgumentException if {@code barCount < 2} or indicators use
     *                                  different series
     * @throws NullPointerException     if an indicator is null
     * @since 0.22.7
     */
    public DistanceCorrelationIndicator(Indicator<Num> first, Indicator<Num> second, int barCount) {
        super(first);
        IndicatorUtils.requireSameSeries(first, second);
        this.first = first;
        this.second = second;
        this.barCount = CorrelationWindowSupport.validateBarCount(barCount);
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN.NaN;
        }
        CorrelationWindowSupport.NumericWindow window = CorrelationWindowSupport.pairedWindow(first, second, index,
                barCount);
        if (window == null) {
            return NaN.NaN;
        }

        NumFactory numFactory = getBarSeries().numFactory();
        CenteringStats firstStats = centeringStats(numFactory, window.firstValues(), window.sampleCount());
        CenteringStats secondStats = centeringStats(numFactory, window.secondValues(), window.sampleCount());
        Num totalsDivisor = numFactory.numOf(window.sampleCount()).multipliedBy(numFactory.numOf(window.sampleCount()));
        Num distanceCovariance = numFactory.zero();
        Num firstDistanceVariance = numFactory.zero();
        Num secondDistanceVariance = numFactory.zero();
        for (int row = 0; row < window.sampleCount(); row++) {
            for (int column = 0; column < window.sampleCount(); column++) {
                Num firstCentered = centeredDistance(window.firstValues(), firstStats, row, column);
                Num secondCentered = centeredDistance(window.secondValues(), secondStats, row, column);
                distanceCovariance = distanceCovariance.plus(firstCentered.multipliedBy(secondCentered));
                firstDistanceVariance = firstDistanceVariance.plus(firstCentered.multipliedBy(firstCentered));
                secondDistanceVariance = secondDistanceVariance.plus(secondCentered.multipliedBy(secondCentered));
            }
        }
        distanceCovariance = distanceCovariance.dividedBy(totalsDivisor);
        firstDistanceVariance = firstDistanceVariance.dividedBy(totalsDivisor);
        secondDistanceVariance = secondDistanceVariance.dividedBy(totalsDivisor);

        Num denominatorSquared = firstDistanceVariance.multipliedBy(secondDistanceVariance);
        if (!CorrelationWindowSupport.isFinite(denominatorSquared) || !denominatorSquared.isPositive()) {
            return NaN.NaN;
        }
        Num denominator = denominatorSquared.sqrt();
        if (!CorrelationWindowSupport.isFinite(denominator) || denominator.isZero()) {
            return NaN.NaN;
        }
        Num squaredCorrelation = distanceCovariance.dividedBy(denominator);
        if (!CorrelationWindowSupport.isFinite(squaredCorrelation)) {
            return NaN.NaN;
        }
        if (squaredCorrelation.isNegative()) {
            squaredCorrelation = numFactory.zero();
        }
        return squaredCorrelation.sqrt();
    }

    @Override
    public int getCountOfUnstableBars() {
        return CorrelationWindowSupport.unstableBars(barCount, first, second);
    }

    private static CenteringStats centeringStats(NumFactory numFactory, Num[] values, int size) {
        Num[] rowMeans = new Num[size];
        Num grandTotal = numFactory.zero();
        Num sampleCount = numFactory.numOf(size);
        for (int row = 0; row < size; row++) {
            Num rowTotal = numFactory.zero();
            for (int column = 0; column < size; column++) {
                rowTotal = rowTotal.plus(values[row].minus(values[column]).abs());
            }
            rowMeans[row] = rowTotal.dividedBy(sampleCount);
            grandTotal = grandTotal.plus(rowTotal);
        }
        return new CenteringStats(rowMeans, grandTotal.dividedBy(sampleCount.multipliedBy(sampleCount)));
    }

    private static Num centeredDistance(Num[] values, CenteringStats stats, int row, int column) {
        return values[row].minus(values[column])
                .abs()
                .minus(stats.rowMeans()[row])
                .minus(stats.rowMeans()[column])
                .plus(stats.grandMean());
    }

    private record CenteringStats(Num[] rowMeans, Num grandMean) {
    }
}
