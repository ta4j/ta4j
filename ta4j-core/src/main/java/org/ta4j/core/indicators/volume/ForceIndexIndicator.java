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
 * @since 0.22.2
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
     * @since 0.22.2
     */
    public ForceIndexIndicator(final BarSeries series) {
        this(series, DEFAULT_BAR_COUNT);
    }

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount smoothing period (must be greater than 0)
     * @since 0.22.2
     */
    public ForceIndexIndicator(final BarSeries series, final int barCount) {
        this(new ClosePriceIndicator(series), new VolumeIndicator(series), barCount);
    }

    /**
     * Constructor using the canonical 13-period smoothing.
     *
     * @param closePriceIndicator close-price indicator
     * @param volumeIndicator     volume indicator
     * @since 0.22.2
     */
    public ForceIndexIndicator(final Indicator<Num> closePriceIndicator, final Indicator<Num> volumeIndicator) {
        this(closePriceIndicator, volumeIndicator, DEFAULT_BAR_COUNT);
    }

    /**
     * Constructor.
     *
     * @param closePriceIndicator close-price indicator
     * @param volumeIndicator     volume indicator
     * @param barCount            smoothing period (must be greater than 0)
     * @since 0.22.2
     */
    public ForceIndexIndicator(final Indicator<Num> closePriceIndicator, final Indicator<Num> volumeIndicator,
            final int barCount) {
        super(IndicatorUtils.requireSameSeries(closePriceIndicator, volumeIndicator));
        validateBarCount(barCount);

        this.barCount = barCount;
        this.closePriceIndicator = closePriceIndicator;
        this.volumeIndicator = volumeIndicator;

        this.closePriceDifferenceIndicator = new DifferenceIndicator(this.closePriceIndicator);
        this.rawForceIndexIndicator = BinaryOperationIndicator.product(this.closePriceDifferenceIndicator,
                this.volumeIndicator);
        this.smoothedForceIndexIndicator = new EMAIndicator(this.rawForceIndexIndicator, barCount);
    }

    @Override
    protected Num calculate(final int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }

        final Num forceIndexValue = smoothedForceIndexIndicator.getValue(index);
        if (isInvalid(forceIndexValue)) {
            return NaN;
        }

        return forceIndexValue;
    }

    @Override
    public int getCountOfUnstableBars() {
        final int rawUnstableBars = Math.max(closePriceDifferenceIndicator.getCountOfUnstableBars(),
                volumeIndicator.getCountOfUnstableBars());
        return rawUnstableBars + smoothedForceIndexIndicator.getCountOfUnstableBars();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }

    private static void validateBarCount(final int barCount) {
        if (barCount <= 0) {
            throw new IllegalArgumentException("Force Index barCount must be greater than 0");
        }
    }

    private static boolean isInvalid(final Num value) {
        return Num.isNaNOrNull(value) || Double.isNaN(value.doubleValue());
    }
}
