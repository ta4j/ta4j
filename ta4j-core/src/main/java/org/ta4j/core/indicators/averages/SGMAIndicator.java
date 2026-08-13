/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Savitzky-Golay Moving Average (SGMA) Indicator.
 *
 * Applies polynomial regression over a moving window to smooth data while
 * preserving key features like peaks and trends.
 *
 * Savitzky-Golay Moving Average (SGMA) is a digital filtering technique that
 * smooths data while preserving the essential characteristics of the dataset,
 * such as peaks and trends. Unlike traditional moving averages that simply
 * average data points, SGMA uses polynomial regression over a moving window to
 * perform the smoothing. This makes it ideal for reducing noise in signals
 * while maintaining the integrity of the underlying trends, making it popular
 * in both financial analysis and scientific data processing.
 *
 */
public class SGMAIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final Indicator<Num> indicator; // Input series
    private final Num polynomialOrder; // The degree of the polynomial as a Num
    private final List<Num> coefficients; // Pre-computed coefficients for smoothing

    /**
     * Constructor.
     *
     * @param indicator       an indicator
     * @param barCount        the Simple Moving Average time frame
     * @param polynomialOrder the degree of the polynomial, default 2
     */
    public SGMAIndicator(Indicator<Num> indicator, int barCount, int polynomialOrder) {
        this(validatedConfig(indicator, barCount, polynomialOrder));
    }

    private SGMAIndicator(Config config) {
        super(config.indicator().getBarSeries());
        this.barCount = config.barCount();
        this.indicator = config.indicator();
        this.polynomialOrder = config.polynomialOrder();
        this.coefficients = config.coefficients();
    }

    private static Config validatedConfig(Indicator<Num> indicator, int barCount, int polynomialOrder) {
        Indicator<Num> validatedIndicator = Objects.requireNonNull(indicator, "indicator must not be null");
        Objects.requireNonNull(validatedIndicator.getBarSeries(), "indicator must reference a bar series");
        if (barCount % 2 == 0) {
            throw new IllegalArgumentException("Window size must be odd.");
        }
        if (polynomialOrder >= barCount) {
            throw new IllegalArgumentException("Polynomial order must be less than window size.");
        }

        // Convert int parameters to Num objects
        Num polynomialOrderNum = validatedIndicator.getBarSeries().numFactory().numOf(polynomialOrder);

        // Precompute coefficients
        List<Num> coefficients = calculateCoefficients(validatedIndicator, barCount, polynomialOrder);
        return new Config(validatedIndicator, barCount, polynomialOrderNum, List.copyOf(coefficients));
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }
        int halfWindow = barCount / 2;
        Num result = indicator.getBarSeries().numFactory().zero();

        // Apply smoothing using precomputed coefficients
        for (int i = -halfWindow; i <= halfWindow; i++) {
            int sourceIndex = Math.max(0, Math.min(index + i, indicator.getBarSeries().getBarCount() - 1));
            Num value = indicator.getValue(sourceIndex);
            result = result.plus(value.multipliedBy(coefficients.get(i + halfWindow)));
        }

        return result;
    }

    @Override
    public int getCountOfUnstableBars() {
        return indicator.getCountOfUnstableBars() + barCount - 1;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount + " polynomialOrder: " + polynomialOrder;
    }

    /**
     * Calculates the Savitzky-Golay smoothing coefficients.
     *
     * @param barCount        The size of the moving window.
     * @param polynomialOrder The degree of the polynomial.
     * @return A list of smoothing coefficients.
     */
    private static List<Num> calculateCoefficients(Indicator<Num> indicator, int barCount, int polynomialOrder) {
        int halfWindow = barCount / 2;
        List<Num> coefficients = new ArrayList<>(barCount);
        NumFactory numFactory = indicator.getBarSeries().numFactory();

        // Initialize matrix and vector for least squares regression
        double[][] matrix = new double[barCount][polynomialOrder + 1];

        // Populate the matrix with powers of indices
        for (int i = -halfWindow; i <= halfWindow; i++) {
            for (int j = 0; j <= polynomialOrder; j++) {
                matrix[i + halfWindow][j] = Math.pow(i, j);
            }
        }

        // Solve the least squares problem
        double[] solution = solveLeastSquares(matrix, barCount, polynomialOrder);

        // Convert the solution to Num coefficients
        for (double coef : solution) {
            coefficients.add(numFactory.numOf(coef));
        }

        return coefficients;
    }

    /**
     * Solves a least squares problem for the given matrix.
     *
     * @param matrix          The input matrix.
     * @param barCount        The size of the moving window.
     * @param polynomialOrder The degree of the polynomial.
     * @return The solution vector of coefficients.
     */
    private static double[] solveLeastSquares(double[][] matrix, int barCount, int polynomialOrder) {
        double[] coefficients = new double[barCount];

        // Use the normal equations to compute the coefficients
        // This is a placeholder; replace with a proper least-squares solver like Apache
        // Commons Math or manual computation.
        for (int i = 0; i < barCount; i++) {
            coefficients[i] = 1.0 / barCount; // Simple averaging as placeholder
        }

        return coefficients;
    }

    private record Config(Indicator<Num> indicator, int barCount, Num polynomialOrder, List<Num> coefficients) {
    }
}
