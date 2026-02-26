/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.SMMAIndicator;
import org.ta4j.core.indicators.helpers.MedianPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Bill Williams Alligator line indicator.
 * <p>
 * The Alligator consists of three displaced smoothed moving averages built from
 * median price:
 * <ul>
 * <li>jaw: SMMA(13) shifted by 8 bars</li>
 * <li>teeth: SMMA(8) shifted by 5 bars</li>
 * <li>lips: SMMA(5) shifted by 3 bars</li>
 * </ul>
 * This class represents one line. Use the static factories
 * {@link #jaw(BarSeries)}, {@link #teeth(BarSeries)}, and
 * {@link #lips(BarSeries)} for the canonical setup.
 * <p>
 * Displacement is implemented without look-ahead bias: value at index {@code i}
 * is read from the smoothed value at {@code i - shift}. Bars before warm-up
 * return {@code NaN}.
 * <p>
 * Interaction note: Bill Williams' fractal breakout logic commonly checks
 * whether confirmed fractals ({@link FractalHighIndicator},
 * {@link FractalLowIndicator}) form outside the alligator teeth.
 *
 * @see GatorOscillatorIndicator
 * @see FractalHighIndicator
 * @see FractalLowIndicator
 * @see <a href=
 *      "https://www.investopedia.com/terms/a/alligatorindicator.asp">Investopedia:
 *      Alligator Indicator</a>
 * @since 0.22.3
 */
public class AlligatorIndicator extends CachedIndicator<Num> {

    public static final int JAW_BAR_COUNT = 13;
    public static final int JAW_SHIFT = 8;
    public static final int TEETH_BAR_COUNT = 8;
    public static final int TEETH_SHIFT = 5;
    public static final int LIPS_BAR_COUNT = 5;
    public static final int LIPS_SHIFT = 3;

    private final int barCount;
    private final int shift;
    private final Indicator<Num> indicator;
    private final transient SMMAIndicator smoothedIndicator;

    /**
     * Constructor.
     *
     * @param indicator source indicator (commonly median price)
     * @param barCount  smoothing window
     * @param shift     forward displacement (rendered as {@code i - shift})
     * @throws IllegalArgumentException if {@code indicator} is {@code null},
     *                                  {@code barCount < 1}, or {@code shift < 0}
     * @since 0.22.3
     */
    public AlligatorIndicator(Indicator<Num> indicator, int barCount, int shift) {
        super(IndicatorUtils.requireIndicator(indicator, "indicator"));
        if (barCount < 1) {
            throw new IllegalArgumentException("barCount must be greater than 0");
        }
        if (shift < 0) {
            throw new IllegalArgumentException("shift must be 0 or greater");
        }
        this.barCount = barCount;
        this.shift = shift;
        this.indicator = indicator;
        this.smoothedIndicator = new SMMAIndicator(indicator, barCount);
    }

    /**
     * Constructor with series median price.
     *
     * @param series   the series
     * @param barCount smoothing window
     * @param shift    forward displacement
     * @since 0.22.3
     */
    public AlligatorIndicator(BarSeries series, int barCount, int shift) {
        this(new MedianPriceIndicator(series), barCount, shift);
    }

    /**
     * Creates the default Alligator jaw line.
     *
     * @param indicator source indicator
     * @return jaw line
     * @since 0.22.3
     */
    public static AlligatorIndicator jaw(Indicator<Num> indicator) {
        return new AlligatorIndicator(indicator, JAW_BAR_COUNT, JAW_SHIFT);
    }

    /**
     * Creates the default Alligator jaw line from series median price.
     *
     * @param series the series
     * @return jaw line
     * @since 0.22.3
     */
    public static AlligatorIndicator jaw(BarSeries series) {
        return jaw(new MedianPriceIndicator(series));
    }

    /**
     * Creates the default Alligator teeth line.
     *
     * @param indicator source indicator
     * @return teeth line
     * @since 0.22.3
     */
    public static AlligatorIndicator teeth(Indicator<Num> indicator) {
        return new AlligatorIndicator(indicator, TEETH_BAR_COUNT, TEETH_SHIFT);
    }

    /**
     * Creates the default Alligator teeth line from series median price.
     *
     * @param series the series
     * @return teeth line
     * @since 0.22.3
     */
    public static AlligatorIndicator teeth(BarSeries series) {
        return teeth(new MedianPriceIndicator(series));
    }

    /**
     * Creates the default Alligator lips line.
     *
     * @param indicator source indicator
     * @return lips line
     * @since 0.22.3
     */
    public static AlligatorIndicator lips(Indicator<Num> indicator) {
        return new AlligatorIndicator(indicator, LIPS_BAR_COUNT, LIPS_SHIFT);
    }

    /**
     * Creates the default Alligator lips line from series median price.
     *
     * @param series the series
     * @return lips line
     * @since 0.22.3
     */
    public static AlligatorIndicator lips(BarSeries series) {
        return lips(new MedianPriceIndicator(series));
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }
        final int displacedIndex = index - shift;
        if (displacedIndex < getBarSeries().getBeginIndex()) {
            return NaN;
        }
        final Num value = smoothedIndicator.getValue(displacedIndex);
        return IndicatorUtils.isInvalid(value) ? NaN : value;
    }

    /**
     * @return source indicator used before smoothing/displacement
     * @since 0.22.3
     */
    public Indicator<Num> getPriceIndicator() {
        return indicator;
    }

    /**
     * @return smoothing window
     * @since 0.22.3
     */
    public int getBarCount() {
        return barCount;
    }

    /**
     * @return displacement amount
     * @since 0.22.3
     */
    public int getShift() {
        return shift;
    }

    @Override
    public int getCountOfUnstableBars() {
        return smoothedIndicator.getCountOfUnstableBars() + shift;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount + " shift: " + shift;
    }

}
