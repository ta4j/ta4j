/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import java.util.Objects;

import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Estimates the rolling Hurst exponent of a numeric indicator using a
 * log-variogram regression.
 *
 * <p>
 * For each lag, this indicator calculates the mean squared difference across
 * the configured rolling window, then regresses the logarithm of that variogram
 * against the logarithm of the lag. Half of the fitted slope is returned and
 * bounded to the theoretical interval {@code [0, 1]}.
 *
 * <p>
 * The default maximum lag is ten, or {@code barCount - 1} for shorter windows.
 * Use the explicit maximum-lag constructor when a different lag range is
 * required. A result is unavailable until the full window is stable, and any
 * non-finite source value makes the affected window unavailable.
 *
 * <p>
 * Rolling differences and variograms remain in the source {@link Num} type.
 * Conversion to {@code double} is limited to the logarithmic regression
 * boundary required by Commons Math.
 *
 * @since 0.23.1
 */
public final class HurstExponentIndicator extends CachedIndicator<Num> {

    private static final int DEFAULT_MAXIMUM_LAG = 10;
    private static final double VARIOGRAM_FLOOR = 1e-12d;

    private final Indicator<Num> indicator;
    private final int barCount;
    private final int maximumLag;

    /**
     * Creates a close-price Hurst exponent indicator using the default maximum lag.
     *
     * @param series   bar series
     * @param barCount rolling observation count, at least three
     * @since 0.23.1
     */
    public HurstExponentIndicator(BarSeries series, int barCount) {
        this(new ClosePriceIndicator(Objects.requireNonNull(series, "series must not be null")), barCount);
    }

    /**
     * Creates a close-price Hurst exponent indicator.
     *
     * @param series     bar series
     * @param barCount   rolling observation count, at least three
     * @param maximumLag largest regression lag, from two through
     *                   {@code barCount - 1}
     * @since 0.23.1
     */
    public HurstExponentIndicator(BarSeries series, int barCount, int maximumLag) {
        this(new ClosePriceIndicator(Objects.requireNonNull(series, "series must not be null")), barCount, maximumLag);
    }

    /**
     * Creates a Hurst exponent indicator using the default maximum lag.
     *
     * @param indicator numeric source
     * @param barCount  rolling observation count, at least three
     * @since 0.23.1
     */
    public HurstExponentIndicator(Indicator<Num> indicator, int barCount) {
        this(indicator, barCount, Math.min(DEFAULT_MAXIMUM_LAG, barCount - 1));
    }

    /**
     * Creates a Hurst exponent indicator.
     *
     * @param indicator  numeric source
     * @param barCount   rolling observation count, at least three
     * @param maximumLag largest regression lag, from two through
     *                   {@code barCount - 1}
     * @since 0.23.1
     */
    public HurstExponentIndicator(Indicator<Num> indicator, int barCount, int maximumLag) {
        super(validatedIndicator(indicator, barCount, maximumLag));
        this.indicator = indicator;
        this.barCount = barCount;
        this.maximumLag = maximumLag;
    }

    @Override
    protected Num calculate(int index) {
        int firstIndex = index - barCount + 1;
        if (index < getCountOfUnstableBars() || firstIndex < getBarSeries().getBeginIndex()) {
            return NaN.NaN;
        }

        NumFactory numFactory = getBarSeries().numFactory();
        Num variogramFloor = numFactory.numOf(VARIOGRAM_FLOOR);
        SimpleRegression regression = new SimpleRegression(true);
        try {
            for (int lag = 1; lag <= maximumLag; lag++) {
                Num squaredDifferenceTotal = numFactory.zero();
                int pairCount = 0;
                for (int currentIndex = firstIndex + lag; currentIndex <= index; currentIndex++) {
                    Num current = indicator.getValue(currentIndex);
                    Num previous = indicator.getValue(currentIndex - lag);
                    if (!Num.isFinite(current) || !Num.isFinite(previous)) {
                        return NaN.NaN;
                    }
                    Num difference = current.minus(previous);
                    squaredDifferenceTotal = squaredDifferenceTotal.plus(difference.multipliedBy(difference));
                    if (!Num.isFinite(squaredDifferenceTotal)) {
                        return NaN.NaN;
                    }
                    pairCount++;
                }

                Num variogram = squaredDifferenceTotal.dividedBy(numFactory.numOf(pairCount));
                if (!Num.isFinite(variogram) || variogram.isNegative()) {
                    return NaN.NaN;
                }
                Num flooredVariogram = variogram.isLessThan(variogramFloor) ? variogramFloor : variogram;
                double primitiveVariogram = flooredVariogram.doubleValue();
                if (!Double.isFinite(primitiveVariogram) || primitiveVariogram <= 0d) {
                    return NaN.NaN;
                }
                regression.addData(Math.log(lag), Math.log(primitiveVariogram));
            }
        } catch (ArithmeticException exception) {
            return NaN.NaN;
        }

        double slope = regression.getSlope();
        if (!Double.isFinite(slope)) {
            return NaN.NaN;
        }
        double hurst = Math.max(0d, Math.min(1d, slope * 0.5d));
        Num result = numFactory.numOf(hurst);
        return Num.isFinite(result) ? result : NaN.NaN;
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.23.1
     */
    @Override
    public int getCountOfUnstableBars() {
        return indicator.getCountOfUnstableBars() + barCount - 1;
    }

    private static Indicator<Num> validatedIndicator(Indicator<Num> indicator, int barCount, int maximumLag) {
        Indicator<Num> validated = Objects.requireNonNull(indicator, "indicator must not be null");
        if (barCount < 3) {
            throw new IllegalArgumentException("barCount must be >= 3");
        }
        if (maximumLag < 2 || maximumLag >= barCount) {
            throw new IllegalArgumentException("maximumLag must be in [2, barCount - 1]");
        }
        return validated;
    }
}
