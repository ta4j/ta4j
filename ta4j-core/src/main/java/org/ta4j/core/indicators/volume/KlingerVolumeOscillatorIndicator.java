/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
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
}
