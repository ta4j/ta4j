/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.indicators.numeric.BinaryOperationIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.Num;

import java.util.Objects;

/**
 * Klinger Volume Oscillator (KVO).
 *
 * <p>
 * This implementation follows the commonly used Klinger formulation:
 *
 * <pre>
 * trend(i) = sign((high(i) + low(i) + close(i)) - (high(i-1) + low(i-1) + close(i-1)))
 * dm(i)    = high(i) - low(i)
 * cm(i)    = cm(i-1) + dm(i),               if trend(i) == trend(i-1)
 *            dm(i-1) + dm(i),               otherwise
 * vf(i)    = volume(i) * trend(i) * |2 * ((dm(i) / cm(i)) - 1)| * 100
 * KVO(i)   = EMA(vf, shortPeriod) - EMA(vf, longPeriod)
 * </pre>
 *
 * @see <a href=
 *      "https://www.tradingview.com/support/solutions/43000589157-klinger-oscillator/">TradingView:
 *      Klinger Oscillator</a>
 * @see <a href=
 *      "https://www.fmlabs.com/reference/default.htm?url=KlingerOscillator.htm">FMLabs:
 *      Klinger Oscillator</a>
 * @since 0.22.4
 */
public class KlingerVolumeOscillatorIndicator extends CachedIndicator<Num> {

    /** Default short EMA period. */
    public static final int DEFAULT_SHORT_PERIOD = 34;

    /** Default long EMA period. */
    public static final int DEFAULT_LONG_PERIOD = 55;

    /** Default scale multiplier used in the Klinger volume-force formula. */
    public static final int DEFAULT_SCALE_MULTIPLIER = 100;

    private final int shortPeriod;
    private final int longPeriod;
    private final Num scaleMultiplier;

    @SuppressWarnings("unused")
    private final Indicator<Num> highPriceIndicator;

    @SuppressWarnings("unused")
    private final Indicator<Num> lowPriceIndicator;

    @SuppressWarnings("unused")
    private final Indicator<Num> closePriceIndicator;

    @SuppressWarnings("unused")
    private final Indicator<Num> volumeIndicator;

    private final transient DailyMeasurementIndicator dailyMeasurementIndicator;
    private final transient TrendDirectionIndicator trendDirectionIndicator;
    private final transient CumulativeMeasurementIndicator cumulativeMeasurementIndicator;
    private final transient VolumeForceIndicator volumeForceIndicator;
    private final transient EMAIndicator shortEmaIndicator;
    private final transient EMAIndicator longEmaIndicator;

    /**
     * Constructor using canonical periods (34, 55).
     *
     * @param series the bar series
     * @since 0.22.4
     */
    public KlingerVolumeOscillatorIndicator(final BarSeries series) {
        this(series, DEFAULT_SHORT_PERIOD, DEFAULT_LONG_PERIOD);
    }

    /**
     * Constructor.
     *
     * @param series      the bar series
     * @param shortPeriod short EMA period (must be greater than 0)
     * @param longPeriod  long EMA period (must be greater than shortPeriod)
     * @since 0.22.4
     */
    public KlingerVolumeOscillatorIndicator(final BarSeries series, final int shortPeriod, final int longPeriod) {
        this(series, shortPeriod, longPeriod, DEFAULT_SCALE_MULTIPLIER);
    }

    /**
     * Constructor using canonical periods (34, 55) with a custom scale multiplier.
     *
     * @param series          the bar series
     * @param scaleMultiplier scale multiplier used in the volume-force term (must
     *                        be finite and greater than 0)
     * @since 0.22.4
     */
    public KlingerVolumeOscillatorIndicator(final BarSeries series, final Number scaleMultiplier) {
        this(series, DEFAULT_SHORT_PERIOD, DEFAULT_LONG_PERIOD, scaleMultiplier);
    }

    /**
     * Constructor.
     *
     * @param series          the bar series
     * @param shortPeriod     short EMA period (must be greater than 0)
     * @param longPeriod      long EMA period (must be greater than shortPeriod)
     * @param scaleMultiplier scale multiplier used in the volume-force term (must
     *                        be finite and greater than 0)
     * @since 0.22.4
     */
    public KlingerVolumeOscillatorIndicator(final BarSeries series, final int shortPeriod, final int longPeriod,
            final Number scaleMultiplier) {
        this(new HighPriceIndicator(series), new LowPriceIndicator(series), new ClosePriceIndicator(series),
                new VolumeIndicator(series), shortPeriod, longPeriod, scaleMultiplier);
    }

    /**
     * Constructor using canonical periods (34, 55).
     *
     * @param highPriceIndicator  high-price indicator
     * @param lowPriceIndicator   low-price indicator
     * @param closePriceIndicator close-price indicator
     * @param volumeIndicator     volume indicator
     * @since 0.22.4
     */
    public KlingerVolumeOscillatorIndicator(final Indicator<Num> highPriceIndicator,
            final Indicator<Num> lowPriceIndicator, final Indicator<Num> closePriceIndicator,
            final Indicator<Num> volumeIndicator) {
        this(highPriceIndicator, lowPriceIndicator, closePriceIndicator, volumeIndicator, DEFAULT_SHORT_PERIOD,
                DEFAULT_LONG_PERIOD);
    }

    /**
     * Constructor.
     *
     * @param highPriceIndicator  high-price indicator
     * @param lowPriceIndicator   low-price indicator
     * @param closePriceIndicator close-price indicator
     * @param volumeIndicator     volume indicator
     * @param shortPeriod         short EMA period (must be greater than 0)
     * @param longPeriod          long EMA period (must be greater than shortPeriod)
     * @since 0.22.4
     */
    public KlingerVolumeOscillatorIndicator(final Indicator<Num> highPriceIndicator,
            final Indicator<Num> lowPriceIndicator, final Indicator<Num> closePriceIndicator,
            final Indicator<Num> volumeIndicator, final int shortPeriod, final int longPeriod) {
        this(highPriceIndicator, lowPriceIndicator, closePriceIndicator, volumeIndicator, shortPeriod, longPeriod,
                DEFAULT_SCALE_MULTIPLIER);
    }

    /**
     * Constructor.
     *
     * @param highPriceIndicator  high-price indicator
     * @param lowPriceIndicator   low-price indicator
     * @param closePriceIndicator close-price indicator
     * @param volumeIndicator     volume indicator
     * @param shortPeriod         short EMA period (must be greater than 0)
     * @param longPeriod          long EMA period (must be greater than shortPeriod)
     * @param scaleMultiplier     scale multiplier used in the volume-force term
     *                            (must be finite and greater than 0)
     * @since 0.22.4
     */
    public KlingerVolumeOscillatorIndicator(final Indicator<Num> highPriceIndicator,
            final Indicator<Num> lowPriceIndicator, final Indicator<Num> closePriceIndicator,
            final Indicator<Num> volumeIndicator, final int shortPeriod, final int longPeriod,
            final Number scaleMultiplier) {
        super(IndicatorUtils.requireSameSeries(highPriceIndicator, lowPriceIndicator, closePriceIndicator,
                volumeIndicator));

        validatePeriods(shortPeriod, longPeriod);
        final Number validatedScaleMultiplier = validateScaleMultiplier(scaleMultiplier);

        this.shortPeriod = shortPeriod;
        this.longPeriod = longPeriod;
        this.scaleMultiplier = getBarSeries().numFactory().numOf(validatedScaleMultiplier);
        this.highPriceIndicator = highPriceIndicator;
        this.lowPriceIndicator = lowPriceIndicator;
        this.closePriceIndicator = closePriceIndicator;
        this.volumeIndicator = volumeIndicator;
        this.dailyMeasurementIndicator = new DailyMeasurementIndicator(this.highPriceIndicator, this.lowPriceIndicator);
        this.trendDirectionIndicator = new TrendDirectionIndicator(this.highPriceIndicator, this.lowPriceIndicator,
                closePriceIndicator);
        this.cumulativeMeasurementIndicator = new CumulativeMeasurementIndicator(this.dailyMeasurementIndicator,
                this.trendDirectionIndicator);
        this.volumeForceIndicator = new VolumeForceIndicator(this.volumeIndicator, this.dailyMeasurementIndicator,
                this.trendDirectionIndicator, this.cumulativeMeasurementIndicator, this.scaleMultiplier);

        this.shortEmaIndicator = new EMAIndicator(this.volumeForceIndicator, shortPeriod);
        this.longEmaIndicator = new EMAIndicator(this.volumeForceIndicator, longPeriod);
    }

    @Override
    protected Num calculate(final int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }

        final Num shortValue = shortEmaIndicator.getValue(index);
        final Num longValue = longEmaIndicator.getValue(index);
        if (isInvalid(shortValue) || isInvalid(longValue)) {
            return NaN;
        }

        return shortValue.minus(longValue);
    }

    /**
     * Returns the first stable index for Klinger oscillator values.
     *
     * @return unstable bar count
     */
    @Override
    public int getCountOfUnstableBars() {
        final int emaUnstableBars = Math.max(shortEmaIndicator.getCountOfUnstableBars(),
                longEmaIndicator.getCountOfUnstableBars());
        return volumeForceIndicator.getCountOfUnstableBars() + emaUnstableBars;
    }

    /**
     * Returns the indicator label including configured EMA periods.
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + " shortPeriod: " + shortPeriod + " longPeriod: " + longPeriod
                + " scaleMultiplier: " + scaleMultiplier;
    }

    private static void validatePeriods(final int shortPeriod, final int longPeriod) {
        if (shortPeriod <= 0) {
            throw new IllegalArgumentException("Klinger shortPeriod must be greater than 0");
        }
        if (longPeriod <= shortPeriod) {
            throw new IllegalArgumentException("Klinger longPeriod must be greater than shortPeriod");
        }
    }

    private static Number validateScaleMultiplier(final Number scaleMultiplier) {
        if (scaleMultiplier == null) {
            throw new IllegalArgumentException("Klinger scaleMultiplier must be finite and greater than 0");
        }
        final Number validatedScaleMultiplier = scaleMultiplier;
        final double scaleMultiplierValue = validatedScaleMultiplier.doubleValue();
        if (!Double.isFinite(scaleMultiplierValue) || scaleMultiplierValue <= 0) {
            throw new IllegalArgumentException("Klinger scaleMultiplier must be finite and greater than 0");
        }
        return validatedScaleMultiplier;
    }

    private static boolean isInvalid(final Num value) {
        return Num.isNaNOrNull(value);
    }

    private static final class CumulativeMeasurementIndicator extends RecursiveCachedIndicator<Num> {

        private final Indicator<Num> measurementIndicator;
        private final Indicator<Num> trendIndicator;
        private final int unstableBars;

        private CumulativeMeasurementIndicator(final Indicator<Num> measurementIndicator,
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
    }

    private static final class VolumeForceIndicator extends CachedIndicator<Num> {

        private final Indicator<Num> volumeIndicator;
        private final Indicator<Num> measurementIndicator;
        private final Indicator<Num> trendIndicator;
        private final Indicator<Num> cumulativeMeasurementIndicator;
        private final Num scaleMultiplier;
        private final Num one;
        private final Num two;
        private final int unstableBars;

        private VolumeForceIndicator(final Indicator<Num> volumeIndicator, final Indicator<Num> measurementIndicator,
                final Indicator<Num> trendIndicator, final Indicator<Num> cumulativeMeasurementIndicator,
                final Num scaleMultiplier) {
            super(IndicatorUtils.requireSameSeries(volumeIndicator, measurementIndicator, trendIndicator,
                    cumulativeMeasurementIndicator));

            this.volumeIndicator = volumeIndicator;
            this.measurementIndicator = measurementIndicator;
            this.trendIndicator = trendIndicator;
            this.cumulativeMeasurementIndicator = cumulativeMeasurementIndicator;
            this.scaleMultiplier = scaleMultiplier;

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

        @Override
        public int getCountOfUnstableBars() {
            return unstableBars;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " scaleMultiplier: " + scaleMultiplier;
        }
    }

    private static final class DailyMeasurementIndicator extends CachedIndicator<Num> {
        private final Indicator<Num> highPriceIndicator;

        private final Indicator<Num> lowPriceIndicator;

        private final int unstableBars;

        private DailyMeasurementIndicator(final Indicator<Num> highPriceIndicator,
                final Indicator<Num> lowPriceIndicator) {
            super(IndicatorUtils.requireSameSeries(highPriceIndicator, lowPriceIndicator));
            this.highPriceIndicator = highPriceIndicator;
            this.lowPriceIndicator = lowPriceIndicator;
            this.unstableBars = Math.max(highPriceIndicator.getCountOfUnstableBars(),
                    lowPriceIndicator.getCountOfUnstableBars());
        }

        @Override
        protected Num calculate(final int index) {
            if (index < getCountOfUnstableBars()) {
                return NaN;
            }

            final Num high = highPriceIndicator.getValue(index);
            final Num low = lowPriceIndicator.getValue(index);
            if (isInvalid(high) || isInvalid(low)) {
                return NaN;
            }
            return high.minus(low);
        }

        @Override
        public int getCountOfUnstableBars() {
            return unstableBars;
        }
    }

    private static final class TrendDirectionIndicator extends RecursiveCachedIndicator<Num> {
        private final Indicator<Num> basisIndicator;

        private final Num one;
        private final Num minusOne;
        private final int unstableBars;

        private TrendDirectionIndicator(final Indicator<Num> highPriceIndicator, final Indicator<Num> lowPriceIndicator,
                final Indicator<Num> closePriceIndicator) {
            this(buildTrendBasis(highPriceIndicator, lowPriceIndicator, closePriceIndicator));
        }

        private TrendDirectionIndicator(final Indicator<Num> basisIndicator) {
            super(IndicatorUtils.requireSameSeries(basisIndicator, basisIndicator));
            this.basisIndicator = basisIndicator;
            this.one = getBarSeries().numFactory().one();
            this.minusOne = getBarSeries().numFactory().minusOne();
            this.unstableBars = basisIndicator.getCountOfUnstableBars() + 1;
        }

        @Override
        protected Num calculate(final int index) {
            final int beginIndex = getBarSeries().getBeginIndex();
            if (beginIndex < 0 || index <= beginIndex) {
                return one;
            }

            final Num currentBasis = basisIndicator.getValue(index);
            final Num previousBasis = basisIndicator.getValue(index - 1);
            if (isInvalid(currentBasis) || isInvalid(previousBasis)) {
                return NaN;
            }

            if (currentBasis.isGreaterThan(previousBasis)) {
                return one;
            }
            if (currentBasis.isLessThan(previousBasis)) {
                return minusOne;
            }

            final Num previousTrend = getValue(index - 1);
            return isInvalid(previousTrend) ? NaN : previousTrend;
        }

        @Override
        public int getCountOfUnstableBars() {
            return unstableBars;
        }

        private static Indicator<Num> buildTrendBasis(final Indicator<Num> highPriceIndicator,
                final Indicator<Num> lowPriceIndicator, final Indicator<Num> closePriceIndicator) {
            return BinaryOperationIndicator.sum(BinaryOperationIndicator.sum(highPriceIndicator, lowPriceIndicator),
                    closePriceIndicator);
        }
    }
}
