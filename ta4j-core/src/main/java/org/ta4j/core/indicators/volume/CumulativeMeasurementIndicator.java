/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Cumulative measurement indicator with trend-reset behavior.
 *
 * <p>
 * Given a measurement series {@code m} and trend series {@code t} ({@code +1}
 * or {@code -1}), this indicator computes:
 *
 * <pre>
 * cm(i) = cm(i-1) + m(i), if t(i) == t(i-1)
 *         m(i-1) + m(i),  otherwise
 * </pre>
 *
 * @since 0.22.2
 */
public class CumulativeMeasurementIndicator extends RecursiveCachedIndicator<Num> {

    @SuppressWarnings("unused")
    private final Indicator<Num> measurementIndicator;

    @SuppressWarnings("unused")
    private final Indicator<Num> trendIndicator;

    private final int unstableBars;

    /**
     * Constructor.
     *
     * @param measurementIndicator measurement indicator
     * @param trendIndicator       trend indicator
     * @since 0.22.2
     */
    public CumulativeMeasurementIndicator(final Indicator<Num> measurementIndicator,
            final Indicator<Num> trendIndicator) {
        super(IndicatorUtils.requireSameSeries(measurementIndicator, trendIndicator));
        this.measurementIndicator = measurementIndicator;
        this.trendIndicator = trendIndicator;
        this.unstableBars = Math.max(measurementIndicator.getCountOfUnstableBars(),
                trendIndicator.getCountOfUnstableBars());
    }

    @Override
    protected Num calculate(final int index) {
        final Num measurement = measurementIndicator.getValue(index);
        if (isInvalid(measurement)) {
            return NaN;
        }

        final int beginIndex = getBarSeries().getBeginIndex();
        if (beginIndex < 0 || index <= beginIndex) {
            return measurement;
        }

        final Num previousMeasurement = measurementIndicator.getValue(index - 1);
        final Num trend = trendIndicator.getValue(index);
        final Num previousTrend = trendIndicator.getValue(index - 1);
        if (isInvalid(previousMeasurement) || isInvalid(trend) || isInvalid(previousTrend)) {
            return NaN;
        }

        if (trend.equals(previousTrend)) {
            final Num previousCumulative = getValue(index - 1);
            return isInvalid(previousCumulative) ? NaN : previousCumulative.plus(measurement);
        }

        return previousMeasurement.plus(measurement);
    }

    @Override
    public int getCountOfUnstableBars() {
        return unstableBars;
    }

    private static boolean isInvalid(final Num value) {
        return Num.isNaNOrNull(value) || Double.isNaN(value.doubleValue());
    }
}
