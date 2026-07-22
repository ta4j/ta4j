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
import org.ta4j.core.indicators.helpers.DifferenceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.indicators.numeric.BinaryOperationIndicator;
import org.ta4j.core.num.Num;

/**
 * Force Index indicator.
 *
 * <p>
 * The Force Index combines price momentum and traded volume:
 *
 * <pre>
 * rawForce(i) = (close(i) - close(i - 1)) * volume(i)
 * FI(i)       = EMA(rawForce, N)
 * </pre>
 *
 * <p>
 * Positive values indicate buying pressure and negative values indicate selling
 * pressure.
 *
 * @see <a href=
 *      "https://chartschool.stockcharts.com/table-of-contents/technical-indicators-and-overlays/technical-indicators/force-index">StockCharts:
 *      Force Index</a>
 * @see <a href=
 *      "https://www.investopedia.com/terms/f/force-index.asp">Investopedia:
 *      Force Index</a>
 * @since 0.22.4
 */
public class ForceIndexIndicator extends CachedIndicator<Num> {

    /** Default EMA period. */
    public static final int DEFAULT_BAR_COUNT = 13;

    private final int barCount;

    @SuppressWarnings("unused")
    private final Indicator<Num> closePriceIndicator;

    @SuppressWarnings("unused")
    private final Indicator<Num> volumeIndicator;

    private final transient DifferenceIndicator closePriceDifferenceIndicator;
    private final transient Indicator<Num> rawForceIndexIndicator;
    private final transient EMAIndicator smoothedForceIndexIndicator;

    /**
     * Constructor using the canonical 13-period smoothing.
     *
     * @param series the bar series
     * @since 0.22.4
     */
    public ForceIndexIndicator(final BarSeries series) {
        this(validatedConfig(series, DEFAULT_BAR_COUNT));
    }

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount smoothing period (must be greater than 0)
     * @since 0.22.4
     */
    public ForceIndexIndicator(final BarSeries series, final int barCount) {
        this(validatedConfig(series, barCount));
    }

    /**
     * Constructor using the canonical 13-period smoothing.
     *
     * @param closePriceIndicator close-price indicator
     * @param volumeIndicator     volume indicator
     * @since 0.22.4
     */
    public ForceIndexIndicator(final Indicator<Num> closePriceIndicator, final Indicator<Num> volumeIndicator) {
        this(validatedConfig(closePriceIndicator, volumeIndicator, DEFAULT_BAR_COUNT));
    }

    /**
     * Constructor.
     *
     * @param closePriceIndicator close-price indicator
     * @param volumeIndicator     volume indicator
     * @param barCount            smoothing period (must be greater than 0)
     * @since 0.22.4
     */
    public ForceIndexIndicator(final Indicator<Num> closePriceIndicator, final Indicator<Num> volumeIndicator,
            final int barCount) {
        this(validatedConfig(closePriceIndicator, volumeIndicator, barCount));
    }

    private ForceIndexIndicator(Config config) {
        super(config.series());
        this.barCount = config.barCount();
        this.closePriceIndicator = config.closePriceIndicator();
        this.volumeIndicator = config.volumeIndicator();
        this.closePriceDifferenceIndicator = config.closePriceDifferenceIndicator();
        this.rawForceIndexIndicator = config.rawForceIndexIndicator();
        this.smoothedForceIndexIndicator = config.smoothedForceIndexIndicator();
    }

    private static Config validatedConfig(final BarSeries series, final int barCount) {
        return validatedConfig(new ClosePriceIndicator(series), new VolumeIndicator(series), barCount);
    }

    private static Config validatedConfig(final Indicator<Num> closePriceIndicator,
            final Indicator<Num> volumeIndicator, final int barCount) {
        BarSeries series = IndicatorUtils.requireSameSeries(closePriceIndicator, volumeIndicator);
        validateBarCount(barCount);
        DifferenceIndicator closePriceDifferenceIndicator = new DifferenceIndicator(closePriceIndicator);
        Indicator<Num> rawForceIndexIndicator = BinaryOperationIndicator.product(closePriceDifferenceIndicator,
                volumeIndicator);
        EMAIndicator smoothedForceIndexIndicator = new EMAIndicator(rawForceIndexIndicator, barCount);
        return new Config(series, closePriceIndicator, volumeIndicator, barCount, closePriceDifferenceIndicator,
                rawForceIndexIndicator, smoothedForceIndexIndicator);
    }

    @Override
    protected Num calculate(final int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }

        final Num forceIndexValue = smoothedForceIndexIndicator.getValue(index);
        if (Num.isNaNOrNull(forceIndexValue)) {
            return NaN;
        }

        return forceIndexValue;
    }

    /**
     * Returns the first stable index for Force Index values.
     *
     * @return unstable bar count
     */
    @Override
    public int getCountOfUnstableBars() {
        final int rawUnstableBars = Math.max(closePriceDifferenceIndicator.getCountOfUnstableBars(),
                volumeIndicator.getCountOfUnstableBars());
        return rawUnstableBars + smoothedForceIndexIndicator.getCountOfUnstableBars();
    }

    /**
     * Returns the indicator label including configured period.
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }

    private static void validateBarCount(final int barCount) {
        if (barCount <= 0) {
            throw new IllegalArgumentException("Force Index barCount must be greater than 0");
        }
    }

    private record Config(BarSeries series, Indicator<Num> closePriceIndicator, Indicator<Num> volumeIndicator,
            int barCount, DifferenceIndicator closePriceDifferenceIndicator, Indicator<Num> rawForceIndexIndicator,
            EMAIndicator smoothedForceIndexIndicator) {
    }
}
