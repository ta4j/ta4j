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
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.Num;

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
 * @since 0.22.2
 */
public class KlingerVolumeOscillatorIndicator extends CachedIndicator<Num> {

    /** Default short EMA period. */
    public static final int DEFAULT_SHORT_PERIOD = 34;

    /** Default long EMA period. */
    public static final int DEFAULT_LONG_PERIOD = 55;

    private final int shortPeriod;
    private final int longPeriod;

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
     * @since 0.22.2
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
     * @since 0.22.2
     */
    public KlingerVolumeOscillatorIndicator(final BarSeries series, final int shortPeriod, final int longPeriod) {
        this(new HighPriceIndicator(series), new LowPriceIndicator(series), new ClosePriceIndicator(series),
                new VolumeIndicator(series), shortPeriod, longPeriod);
    }

    /**
     * Constructor using canonical periods (34, 55).
     *
     * @param highPriceIndicator  high-price indicator
     * @param lowPriceIndicator   low-price indicator
     * @param closePriceIndicator close-price indicator
     * @param volumeIndicator     volume indicator
     * @since 0.22.2
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
     * @since 0.22.2
     */
    public KlingerVolumeOscillatorIndicator(final Indicator<Num> highPriceIndicator,
            final Indicator<Num> lowPriceIndicator, final Indicator<Num> closePriceIndicator,
            final Indicator<Num> volumeIndicator, final int shortPeriod, final int longPeriod) {
        super(IndicatorUtils.requireSameSeries(highPriceIndicator, lowPriceIndicator, closePriceIndicator,
                volumeIndicator));

        validatePeriods(shortPeriod, longPeriod);

        this.shortPeriod = shortPeriod;
        this.longPeriod = longPeriod;
        this.highPriceIndicator = highPriceIndicator;
        this.lowPriceIndicator = lowPriceIndicator;
        this.closePriceIndicator = closePriceIndicator;
        this.volumeIndicator = volumeIndicator;

        this.dailyMeasurementIndicator = new DailyMeasurementIndicator(this.highPriceIndicator, this.lowPriceIndicator);
        this.trendDirectionIndicator = new TrendDirectionIndicator(this.highPriceIndicator, this.lowPriceIndicator,
                this.closePriceIndicator);
        this.cumulativeMeasurementIndicator = new CumulativeMeasurementIndicator(this.dailyMeasurementIndicator,
                this.trendDirectionIndicator);
        this.volumeForceIndicator = new VolumeForceIndicator(this.volumeIndicator, this.dailyMeasurementIndicator,
                this.trendDirectionIndicator, this.cumulativeMeasurementIndicator);

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

    @Override
    public int getCountOfUnstableBars() {
        final int emaUnstableBars = Math.max(shortEmaIndicator.getCountOfUnstableBars(),
                longEmaIndicator.getCountOfUnstableBars());
        return volumeForceIndicator.getCountOfUnstableBars() + emaUnstableBars;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " shortPeriod: " + shortPeriod + " longPeriod: " + longPeriod;
    }

    private static void validatePeriods(final int shortPeriod, final int longPeriod) {
        if (shortPeriod <= 0) {
            throw new IllegalArgumentException("Klinger shortPeriod must be greater than 0");
        }
        if (longPeriod <= shortPeriod) {
            throw new IllegalArgumentException("Klinger longPeriod must be greater than shortPeriod");
        }
    }

    private static boolean isInvalid(final Num value) {
        return Num.isNaNOrNull(value) || Double.isNaN(value.doubleValue());
    }

    private static final class DailyMeasurementIndicator extends CachedIndicator<Num> {

        private final Indicator<Num> highPriceIndicator;
        private final Indicator<Num> lowPriceIndicator;
        private final int unstableBars;

        private DailyMeasurementIndicator(final Indicator<Num> highPriceIndicator,
                final Indicator<Num> lowPriceIndicator) {
            super(highPriceIndicator);
            this.highPriceIndicator = highPriceIndicator;
            this.lowPriceIndicator = lowPriceIndicator;
            this.unstableBars = Math.max(highPriceIndicator.getCountOfUnstableBars(),
                    lowPriceIndicator.getCountOfUnstableBars());
        }

        @Override
        protected Num calculate(final int index) {
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

        private final Indicator<Num> highPriceIndicator;
        private final Indicator<Num> lowPriceIndicator;
        private final Indicator<Num> closePriceIndicator;
        private final Num one;
        private final Num minusOne;
        private final int unstableBars;

        private TrendDirectionIndicator(final Indicator<Num> highPriceIndicator, final Indicator<Num> lowPriceIndicator,
                final Indicator<Num> closePriceIndicator) {
            super(highPriceIndicator);
            this.highPriceIndicator = highPriceIndicator;
            this.lowPriceIndicator = lowPriceIndicator;
            this.closePriceIndicator = closePriceIndicator;
            this.one = getBarSeries().numFactory().one();
            this.minusOne = getBarSeries().numFactory().minusOne();
            this.unstableBars = Math
                    .max(Math.max(highPriceIndicator.getCountOfUnstableBars(),
                            lowPriceIndicator.getCountOfUnstableBars()), closePriceIndicator.getCountOfUnstableBars())
                    + 1;
        }

        @Override
        protected Num calculate(final int index) {
            final int beginIndex = getBarSeries().getBeginIndex();
            if (index <= beginIndex) {
                return one;
            }

            final Num currentBasis = trendBasis(index);
            final Num previousBasis = trendBasis(index - 1);
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
            if (isInvalid(previousTrend)) {
                return NaN;
            }
            return previousTrend;
        }

        @Override
        public int getCountOfUnstableBars() {
            return unstableBars;
        }

        private Num trendBasis(final int index) {
            final Num high = highPriceIndicator.getValue(index);
            final Num low = lowPriceIndicator.getValue(index);
            final Num close = closePriceIndicator.getValue(index);
            if (isInvalid(high) || isInvalid(low) || isInvalid(close)) {
                return NaN;
            }
            return high.plus(low).plus(close);
        }
    }

    private static final class CumulativeMeasurementIndicator extends RecursiveCachedIndicator<Num> {

        private final DailyMeasurementIndicator dailyMeasurementIndicator;
        private final TrendDirectionIndicator trendDirectionIndicator;
        private final int unstableBars;

        private CumulativeMeasurementIndicator(final DailyMeasurementIndicator dailyMeasurementIndicator,
                final TrendDirectionIndicator trendDirectionIndicator) {
            super(dailyMeasurementIndicator);
            this.dailyMeasurementIndicator = dailyMeasurementIndicator;
            this.trendDirectionIndicator = trendDirectionIndicator;
            this.unstableBars = Math.max(dailyMeasurementIndicator.getCountOfUnstableBars(),
                    trendDirectionIndicator.getCountOfUnstableBars());
        }

        @Override
        protected Num calculate(final int index) {
            final int beginIndex = getBarSeries().getBeginIndex();
            final Num dm = dailyMeasurementIndicator.getValue(index);
            if (isInvalid(dm)) {
                return NaN;
            }

            if (index <= beginIndex) {
                return dm;
            }

            final Num previousDm = dailyMeasurementIndicator.getValue(index - 1);
            final Num trend = trendDirectionIndicator.getValue(index);
            final Num previousTrend = trendDirectionIndicator.getValue(index - 1);
            if (isInvalid(previousDm) || isInvalid(trend) || isInvalid(previousTrend)) {
                return NaN;
            }

            if (trend.equals(previousTrend)) {
                final Num previousCm = getValue(index - 1);
                if (isInvalid(previousCm)) {
                    return NaN;
                }
                return previousCm.plus(dm);
            }
            return previousDm.plus(dm);
        }

        @Override
        public int getCountOfUnstableBars() {
            return unstableBars;
        }
    }

    private static final class VolumeForceIndicator extends CachedIndicator<Num> {

        private final Indicator<Num> volumeIndicator;
        private final DailyMeasurementIndicator dailyMeasurementIndicator;
        private final TrendDirectionIndicator trendDirectionIndicator;
        private final CumulativeMeasurementIndicator cumulativeMeasurementIndicator;
        private final Num one;
        private final Num two;
        private final Num hundred;
        private final int unstableBars;

        private VolumeForceIndicator(final Indicator<Num> volumeIndicator,
                final DailyMeasurementIndicator dailyMeasurementIndicator,
                final TrendDirectionIndicator trendDirectionIndicator,
                final CumulativeMeasurementIndicator cumulativeMeasurementIndicator) {
            super(volumeIndicator);
            this.volumeIndicator = volumeIndicator;
            this.dailyMeasurementIndicator = dailyMeasurementIndicator;
            this.trendDirectionIndicator = trendDirectionIndicator;
            this.cumulativeMeasurementIndicator = cumulativeMeasurementIndicator;
            this.one = getBarSeries().numFactory().one();
            this.two = getBarSeries().numFactory().two();
            this.hundred = getBarSeries().numFactory().hundred();
            this.unstableBars = Math.max(volumeIndicator.getCountOfUnstableBars(),
                    Math.max(trendDirectionIndicator.getCountOfUnstableBars(),
                            cumulativeMeasurementIndicator.getCountOfUnstableBars()));
        }

        @Override
        protected Num calculate(final int index) {
            final int beginIndex = getBarSeries().getBeginIndex();
            if (index <= beginIndex) {
                return NaN;
            }

            final Num volume = volumeIndicator.getValue(index);
            final Num dm = dailyMeasurementIndicator.getValue(index);
            final Num cm = cumulativeMeasurementIndicator.getValue(index);
            final Num trend = trendDirectionIndicator.getValue(index);
            if (isInvalid(volume) || isInvalid(dm) || isInvalid(cm) || isInvalid(trend)) {
                return NaN;
            }
            if (volume.isZero() || cm.isZero()) {
                return NaN;
            }

            final Num magnitude = two.multipliedBy(dm.dividedBy(cm).minus(one)).abs();
            if (isInvalid(magnitude)) {
                return NaN;
            }

            return volume.multipliedBy(trend).multipliedBy(magnitude).multipliedBy(hundred);
        }

        @Override
        public int getCountOfUnstableBars() {
            return unstableBars;
        }
    }
}
