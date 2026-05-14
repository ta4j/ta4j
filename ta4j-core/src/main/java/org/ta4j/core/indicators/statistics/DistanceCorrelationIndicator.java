/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

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
        double[][] window = CorrelationWindowSupport.pairedWindow(first, second, index, barCount);
        if (window == null) {
            return NaN.NaN;
        }

        double[][] firstDistances = centeredDistances(window[0]);
        double[][] secondDistances = centeredDistances(window[1]);
        double distanceCovariance = meanProduct(firstDistances, secondDistances);
        double firstDistanceVariance = meanProduct(firstDistances, firstDistances);
        double secondDistanceVariance = meanProduct(secondDistances, secondDistances);

        double denominator = Math.sqrt(firstDistanceVariance * secondDistanceVariance);
        if (denominator <= 0.0 || Double.isNaN(denominator) || Double.isInfinite(denominator)) {
            return NaN.NaN;
        }
        double squaredCorrelation = Math.max(0.0, distanceCovariance / denominator);
        return getBarSeries().numFactory().numOf(Math.sqrt(squaredCorrelation));
    }

    @Override
    public int getCountOfUnstableBars() {
        return CorrelationWindowSupport.unstableBars(barCount, first, second);
    }

    private static double[][] centeredDistances(double[] values) {
        int size = values.length;
        double[][] distances = new double[size][size];
        double[] rowMeans = new double[size];
        double grandTotal = 0.0;
        for (int row = 0; row < size; row++) {
            double rowTotal = 0.0;
            for (int column = 0; column < size; column++) {
                double distance = Math.abs(values[row] - values[column]);
                distances[row][column] = distance;
                rowTotal += distance;
            }
            rowMeans[row] = rowTotal / size;
            grandTotal += rowTotal;
        }

        double grandMean = grandTotal / (size * size);
        double[][] centered = new double[size][size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                centered[row][column] = distances[row][column] - rowMeans[row] - rowMeans[column] + grandMean;
            }
        }
        return centered;
    }

    private static double meanProduct(double[][] firstMatrix, double[][] secondMatrix) {
        int size = firstMatrix.length;
        double total = 0.0;
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                total += firstMatrix[row][column] * secondMatrix[row][column];
            }
        }
        return total / (size * size);
    }
}
