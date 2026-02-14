/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import static org.ta4j.core.num.NaN.NaN;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.Num;

/**
 * Ease of Movement (EMV) indicator.
 *
 * <p>
 * This implementation computes one-period EMV and smooths it with a simple
 * moving average:
 *
 * <pre>
 * distanceMoved(i) = ((high(i) + low(i)) / 2) - ((high(i-1) + low(i-1)) / 2)
 * boxRatio(i)      = (volume(i) / volumeDivisor) / (high(i) - low(i))
 * emv1(i)          = distanceMoved(i) / boxRatio(i)
 * EMV(i)           = SMA(emv1, N)
 * </pre>
 *
 * <p>
 * Higher positive values indicate easier upward movement, while lower negative
 * values indicate easier downward movement.
 *
 * @see <a href=
 *      "https://chartschool.stockcharts.com/table-of-contents/technical-indicators-and-overlays/technical-indicators/ease-of-movement-emv">StockCharts:
 *      Ease of Movement</a>
 * @see <a href=
 *      "https://www.investopedia.com/terms/e/easeofmovement.asp">Investopedia:
 *      Ease of Movement</a>
 * @since 0.22.2
 */
public class EaseOfMovementIndicator extends CachedIndicator<Num> {

    /** Default smoothing period. */
    public static final int DEFAULT_BAR_COUNT = 14;

    /** Default volume divisor used by common charting packages. */
    public static final long DEFAULT_VOLUME_DIVISOR = 100_000_000L;

    private final int barCount;
    private final Num volumeDivisor;

    @SuppressWarnings("unused")
    private final Indicator<Num> highPriceIndicator;

    @SuppressWarnings("unused")
    private final Indicator<Num> lowPriceIndicator;

    @SuppressWarnings("unused")
    private final Indicator<Num> volumeIndicator;

    private final transient RawEaseOfMovementIndicator rawEaseOfMovementIndicator;

    /**
     * Constructor using canonical settings (14-period SMA, volume divisor
     * 100,000,000).
     *
     * @param series the bar series
     * @since 0.22.2
     */
    public EaseOfMovementIndicator(final BarSeries series) {
        this(series, DEFAULT_BAR_COUNT, DEFAULT_VOLUME_DIVISOR);
    }

    /**
     * Constructor using the default volume divisor (100,000,000).
     *
     * @param series   the bar series
     * @param barCount smoothing period (must be greater than 0)
     * @since 0.22.2
     */
    public EaseOfMovementIndicator(final BarSeries series, final int barCount) {
        this(series, barCount, DEFAULT_VOLUME_DIVISOR);
    }

    /**
     * Constructor.
     *
     * @param series        the bar series
     * @param barCount      smoothing period (must be greater than 0)
     * @param volumeDivisor scaling divisor used in the box-ratio term (must be
     *                      greater than 0)
     * @since 0.22.2
     */
    public EaseOfMovementIndicator(final BarSeries series, final int barCount, final Number volumeDivisor) {
        this(new HighPriceIndicator(series), new LowPriceIndicator(series), new VolumeIndicator(series), barCount,
                volumeDivisor);
    }

    /**
     * Constructor using canonical settings (14-period SMA, volume divisor
     * 100,000,000).
     *
     * @param highPriceIndicator high-price indicator
     * @param lowPriceIndicator  low-price indicator
     * @param volumeIndicator    volume indicator
     * @since 0.22.2
     */
    public EaseOfMovementIndicator(final Indicator<Num> highPriceIndicator, final Indicator<Num> lowPriceIndicator,
            final Indicator<Num> volumeIndicator) {
        this(highPriceIndicator, lowPriceIndicator, volumeIndicator, DEFAULT_BAR_COUNT, DEFAULT_VOLUME_DIVISOR);
    }

    /**
     * Constructor using the default volume divisor (100,000,000).
     *
     * @param highPriceIndicator high-price indicator
     * @param lowPriceIndicator  low-price indicator
     * @param volumeIndicator    volume indicator
     * @param barCount           smoothing period (must be greater than 0)
     * @since 0.22.2
     */
    public EaseOfMovementIndicator(final Indicator<Num> highPriceIndicator, final Indicator<Num> lowPriceIndicator,
            final Indicator<Num> volumeIndicator, final int barCount) {
        this(highPriceIndicator, lowPriceIndicator, volumeIndicator, barCount, DEFAULT_VOLUME_DIVISOR);
    }

    /**
     * Constructor.
     *
     * @param highPriceIndicator high-price indicator
     * @param lowPriceIndicator  low-price indicator
     * @param volumeIndicator    volume indicator
     * @param barCount           smoothing period (must be greater than 0)
     * @param volumeDivisor      scaling divisor used in the box-ratio term (must be
     *                           greater than 0)
     * @since 0.22.2
     */
    public EaseOfMovementIndicator(final Indicator<Num> highPriceIndicator, final Indicator<Num> lowPriceIndicator,
            final Indicator<Num> volumeIndicator, final int barCount, final Number volumeDivisor) {
        super(IndicatorUtils.requireSameSeries(highPriceIndicator, lowPriceIndicator, volumeIndicator));
        validateBarCount(barCount);

        this.barCount = barCount;
        final Number validatedDivisor = Objects.requireNonNull(volumeDivisor, "volumeDivisor must not be null");
        this.volumeDivisor = getBarSeries().numFactory().numOf(validatedDivisor);
        if (this.volumeDivisor.isLessThanOrEqual(getBarSeries().numFactory().zero())) {
            throw new IllegalArgumentException("Ease of Movement volumeDivisor must be greater than 0");
        }

        this.highPriceIndicator = highPriceIndicator;
        this.lowPriceIndicator = lowPriceIndicator;
        this.volumeIndicator = volumeIndicator;

        this.rawEaseOfMovementIndicator = new RawEaseOfMovementIndicator(this.highPriceIndicator,
                this.lowPriceIndicator, this.volumeIndicator, this.volumeDivisor);
    }

    @Override
    protected Num calculate(final int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }

        final int windowStartIndex = index - barCount + 1;
        Num sum = getBarSeries().numFactory().zero();
        for (int i = windowStartIndex; i <= index; i++) {
            final Num rawValue = rawEaseOfMovementIndicator.getValue(i);
            if (isInvalid(rawValue)) {
                return NaN;
            }
            sum = sum.plus(rawValue);
        }

        return sum.dividedBy(getBarSeries().numFactory().numOf(barCount));
    }

    /**
     * Returns the first stable index for smoothed EMV values.
     *
     * @return unstable bar count
     */
    @Override
    public int getCountOfUnstableBars() {
        return rawEaseOfMovementIndicator.getCountOfUnstableBars() + barCount - 1;
    }

    /**
     * Returns the indicator label including configured smoothing settings.
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount + " volumeDivisor: " + volumeDivisor;
    }

    private static void validateBarCount(final int barCount) {
        if (barCount <= 0) {
            throw new IllegalArgumentException("Ease of Movement barCount must be greater than 0");
        }
    }

    private static boolean isInvalid(final Num value) {
        return Num.isNaNOrNull(value);
    }

    private static final class RawEaseOfMovementIndicator extends CachedIndicator<Num> {

        private final Indicator<Num> highPriceIndicator;
        private final Indicator<Num> lowPriceIndicator;
        private final Indicator<Num> volumeIndicator;
        private final Num volumeDivisor;
        private final Num two;
        private final int unstableBars;

        private RawEaseOfMovementIndicator(final Indicator<Num> highPriceIndicator,
                final Indicator<Num> lowPriceIndicator, final Indicator<Num> volumeIndicator, final Num volumeDivisor) {
            super(highPriceIndicator);
            this.highPriceIndicator = highPriceIndicator;
            this.lowPriceIndicator = lowPriceIndicator;
            this.volumeIndicator = volumeIndicator;
            this.volumeDivisor = volumeDivisor;
            this.two = getBarSeries().numFactory().two();
            this.unstableBars = Math.max(
                    Math.max(highPriceIndicator.getCountOfUnstableBars(), lowPriceIndicator.getCountOfUnstableBars()),
                    volumeIndicator.getCountOfUnstableBars()) + 1;
        }

        @Override
        protected Num calculate(final int index) {
            final int beginIndex = getBarSeries().getBeginIndex();
            if (beginIndex < 0 || index <= beginIndex) {
                // Keep the seed deterministic so windowed smoothing can warm up
                // without NaN contamination from the first bar.
                return getBarSeries().numFactory().zero();
            }

            final Num high = highPriceIndicator.getValue(index);
            final Num low = lowPriceIndicator.getValue(index);
            final Num previousHigh = highPriceIndicator.getValue(index - 1);
            final Num previousLow = lowPriceIndicator.getValue(index - 1);
            final Num volume = volumeIndicator.getValue(index);
            if (isInvalid(high) || isInvalid(low) || isInvalid(previousHigh) || isInvalid(previousLow)
                    || isInvalid(volume)) {
                return NaN;
            }
            if (volume.isZero()) {
                return NaN;
            }

            final Num range = high.minus(low);
            if (isInvalid(range) || range.isZero()) {
                return NaN;
            }

            final Num midpoint = high.plus(low).dividedBy(two);
            final Num previousMidpoint = previousHigh.plus(previousLow).dividedBy(two);
            final Num distanceMoved = midpoint.minus(previousMidpoint);

            final Num boxRatio = volume.dividedBy(volumeDivisor).dividedBy(range);
            if (isInvalid(boxRatio) || boxRatio.isZero()) {
                return NaN;
            }

            return distanceMoved.dividedBy(boxRatio);
        }

        /**
         * Returns the first stable index for one-period EMV values.
         *
         * @return unstable bar count
         */
        @Override
        public int getCountOfUnstableBars() {
            return unstableBars;
        }
    }
}
