/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import static org.ta4j.core.num.NaN.NaN;

import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.num.Num;

/**
 * Volume force indicator used by Klinger-style oscillators.
 *
 * <p>
 * Formula:
 *
 * <pre>
 * vf(i) = volume(i) * trend(i) * |2 * ((measurement(i) / cumulativeMeasurement(i)) - 1)| * scaleMultiplier
 * </pre>
 *
 * @since 0.22.2
 */
public class VolumeForceIndicator extends CachedIndicator<Num> {

    /** Default scale multiplier used by the Klinger formula. */
    public static final int DEFAULT_SCALE_MULTIPLIER = 100;

    @SuppressWarnings("unused")
    private final Indicator<Num> volumeIndicator;

    @SuppressWarnings("unused")
    private final Indicator<Num> measurementIndicator;

    @SuppressWarnings("unused")
    private final Indicator<Num> trendIndicator;

    @SuppressWarnings("unused")
    private final Indicator<Num> cumulativeMeasurementIndicator;

    private final Num scaleMultiplier;
    private final Num one;
    private final Num two;
    private final int unstableBars;

    /**
     * Constructor.
     *
     * @param volumeIndicator                volume indicator
     * @param measurementIndicator           measurement indicator
     * @param trendIndicator                 trend indicator
     * @param cumulativeMeasurementIndicator cumulative measurement indicator
     * @since 0.22.2
     */
    public VolumeForceIndicator(final Indicator<Num> volumeIndicator, final Indicator<Num> measurementIndicator,
            final Indicator<Num> trendIndicator, final Indicator<Num> cumulativeMeasurementIndicator) {
        this(volumeIndicator, measurementIndicator, trendIndicator, cumulativeMeasurementIndicator,
                DEFAULT_SCALE_MULTIPLIER);
    }

    /**
     * Constructor.
     *
     * @param volumeIndicator                volume indicator
     * @param measurementIndicator           measurement indicator
     * @param trendIndicator                 trend indicator
     * @param cumulativeMeasurementIndicator cumulative measurement indicator
     * @param scaleMultiplier                scale multiplier (must be greater than
     *                                       0)
     * @since 0.22.2
     */
    public VolumeForceIndicator(final Indicator<Num> volumeIndicator, final Indicator<Num> measurementIndicator,
            final Indicator<Num> trendIndicator, final Indicator<Num> cumulativeMeasurementIndicator,
            final Number scaleMultiplier) {
        super(IndicatorUtils.requireSameSeries(volumeIndicator, measurementIndicator, trendIndicator,
                cumulativeMeasurementIndicator));

        final Number validatedScaleMultiplier = Objects.requireNonNull(scaleMultiplier,
                "scaleMultiplier must not be null");

        this.volumeIndicator = volumeIndicator;
        this.measurementIndicator = measurementIndicator;
        this.trendIndicator = trendIndicator;
        this.cumulativeMeasurementIndicator = cumulativeMeasurementIndicator;

        this.scaleMultiplier = getBarSeries().numFactory().numOf(validatedScaleMultiplier);
        if (this.scaleMultiplier.isLessThanOrEqual(getBarSeries().numFactory().zero())) {
            throw new IllegalArgumentException("Volume force scaleMultiplier must be greater than 0");
        }

        this.one = getBarSeries().numFactory().one();
        this.two = getBarSeries().numFactory().two();
        this.unstableBars = Math.max(1,
                Math.max(volumeIndicator.getCountOfUnstableBars(),
                        Math.max(measurementIndicator.getCountOfUnstableBars(),
                                Math.max(trendIndicator.getCountOfUnstableBars(),
                                        cumulativeMeasurementIndicator.getCountOfUnstableBars()))));
    }

    @Override
    protected Num calculate(final int index) {
        final int beginIndex = getBarSeries().getBeginIndex();
        if (beginIndex < 0 || index <= beginIndex) {
            return NaN;
        }

        final Num volume = volumeIndicator.getValue(index);
        final Num measurement = measurementIndicator.getValue(index);
        final Num trend = trendIndicator.getValue(index);
        final Num cumulativeMeasurement = cumulativeMeasurementIndicator.getValue(index);
        if (isInvalid(volume) || isInvalid(measurement) || isInvalid(trend) || isInvalid(cumulativeMeasurement)) {
            return NaN;
        }
        if (volume.isZero() || cumulativeMeasurement.isZero()) {
            return NaN;
        }

        final Num magnitude = two.multipliedBy(measurement.dividedBy(cumulativeMeasurement).minus(one)).abs();
        if (isInvalid(magnitude)) {
            return NaN;
        }

        return volume.multipliedBy(trend).multipliedBy(magnitude).multipliedBy(scaleMultiplier);
    }

    /**
     * Returns the first stable index for volume-force values.
     *
     * @return unstable bar count
     */
    @Override
    public int getCountOfUnstableBars() {
        return unstableBars;
    }

    /**
     * Returns the indicator label including configured scale multiplier.
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + " scaleMultiplier: " + scaleMultiplier;
    }

    private static boolean isInvalid(final Num value) {
        return Num.isNaNOrNull(value);
    }
}
