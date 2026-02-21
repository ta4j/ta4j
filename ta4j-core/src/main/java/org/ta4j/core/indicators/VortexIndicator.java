/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.RunningTotalIndicator;
import org.ta4j.core.indicators.helpers.TRIndicator;
import org.ta4j.core.num.Num;

/**
 * Vortex indicator.
 *
 * <p>
 * This implementation exposes all three Vortex series:
 * <ul>
 * <li>{@code +VI} through {@link #getPositiveValue(int)}</li>
 * <li>{@code -VI} through {@link #getNegativeValue(int)}</li>
 * <li>Vortex oscillator ({@code +VI - -VI}) through {@link #getValue(int)}</li>
 * </ul>
 *
 * <p>
 * Formulas:
 *
 * <pre>
 * VM+ = |high(i) - low(i-1)|
 * VM- = |low(i) - high(i-1)|
 * TR  = max(high(i), close(i-1)) - min(low(i), close(i-1))
 *
 * +VI = SUM(VM+, N) / SUM(TR, N)
 * -VI = SUM(VM-, N) / SUM(TR, N)
 * OSC = +VI - -VI
 * </pre>
 *
 * @see <a href="https://en.wikipedia.org/wiki/Vortex_indicator">Wikipedia:
 *      Vortex Indicator</a>
 * @since 0.22.2
 */
public class VortexIndicator extends CachedIndicator<Num> {

    /** Default look-back period. */
    public static final int DEFAULT_BAR_COUNT = 14;

    private final int barCount;

    @SuppressWarnings("unused")
    private final Indicator<Num> highPriceIndicator;

    @SuppressWarnings("unused")
    private final Indicator<Num> lowPriceIndicator;

    @SuppressWarnings("unused")
    private final Indicator<Num> closePriceIndicator;

    private final transient RunningTotalIndicator positiveMovementSumIndicator;
    private final transient RunningTotalIndicator negativeMovementSumIndicator;
    private final transient RunningTotalIndicator trueRangeSumIndicator;

    /**
     * Constructor using the common 14-period look-back.
     *
     * @param series the bar series
     * @since 0.22.2
     */
    public VortexIndicator(BarSeries series) {
        this(series, DEFAULT_BAR_COUNT);
    }

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount look-back period (must be greater than 1)
     * @since 0.22.2
     */
    public VortexIndicator(BarSeries series, int barCount) {
        this(new HighPriceIndicator(series), new LowPriceIndicator(series), new ClosePriceIndicator(series), barCount);
    }

    /**
     * Constructor.
     *
     * @param highPriceIndicator  high-price indicator
     * @param lowPriceIndicator   low-price indicator
     * @param closePriceIndicator close-price indicator
     * @param barCount            look-back period (must be greater than 1)
     * @since 0.22.2
     */
    public VortexIndicator(Indicator<Num> highPriceIndicator, Indicator<Num> lowPriceIndicator,
            Indicator<Num> closePriceIndicator, int barCount) {
        super(IndicatorUtils.requireSameSeries(highPriceIndicator, lowPriceIndicator, closePriceIndicator));
        this.highPriceIndicator = highPriceIndicator;
        this.lowPriceIndicator = lowPriceIndicator;
        this.closePriceIndicator = closePriceIndicator;

        validateBarCount(barCount);
        this.barCount = barCount;

        VortexMovementIndicator positiveMovementIndicator = new VortexMovementIndicator(this.highPriceIndicator,
                this.lowPriceIndicator);
        VortexMovementIndicator negativeMovementIndicator = new VortexMovementIndicator(this.lowPriceIndicator,
                this.highPriceIndicator);
        TRIndicator trueRangeIndicator = new TRIndicator(this.highPriceIndicator, this.lowPriceIndicator,
                this.closePriceIndicator);

        this.positiveMovementSumIndicator = new RunningTotalIndicator(positiveMovementIndicator, barCount);
        this.negativeMovementSumIndicator = new RunningTotalIndicator(negativeMovementIndicator, barCount);
        this.trueRangeSumIndicator = new RunningTotalIndicator(trueRangeIndicator, barCount);
    }

    /**
     * Returns {@code +VI} at the requested index.
     *
     * @param index bar index
     * @return positive Vortex line ({@code +VI})
     * @since 0.22.2
     */
    public Num getPositiveValue(int index) {
        return calculateLineValue(index, positiveMovementSumIndicator);
    }

    /**
     * Returns {@code -VI} at the requested index.
     *
     * @param index bar index
     * @return negative Vortex line ({@code -VI})
     * @since 0.22.2
     */
    public Num getNegativeValue(int index) {
        return calculateLineValue(index, negativeMovementSumIndicator);
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }

        Num positive = getPositiveValue(index);
        Num negative = getNegativeValue(index);
        if (isInvalid(positive) || isInvalid(negative)) {
            return NaN;
        }
        return positive.minus(negative);
    }

    private Num calculateLineValue(int index, RunningTotalIndicator movementSumIndicator) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }

        Num totalTrueRange = trueRangeSumIndicator.getValue(index);
        if (isInvalid(totalTrueRange) || totalTrueRange.isZero()) {
            return NaN;
        }

        Num totalMovement = movementSumIndicator.getValue(index);
        if (isInvalid(totalMovement)) {
            return NaN;
        }

        return totalMovement.dividedBy(totalTrueRange);
    }

    @Override
    public int getCountOfUnstableBars() {
        int movementUnstableBars = Math.max(positiveMovementSumIndicator.getCountOfUnstableBars(),
                negativeMovementSumIndicator.getCountOfUnstableBars());
        return Math.max(movementUnstableBars, trueRangeSumIndicator.getCountOfUnstableBars());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }

    private static void validateBarCount(int barCount) {
        if (barCount <= 1) {
            throw new IllegalArgumentException("Vortex look-back length must be greater than 1");
        }
    }

    private static boolean isInvalid(Num value) {
        return Num.isNaNOrNull(value) || Double.isNaN(value.doubleValue());
    }

    private static final class VortexMovementIndicator extends CachedIndicator<Num> {

        private final Indicator<Num> currentIndicator;
        private final Indicator<Num> previousIndicator;
        private final int unstableBars;

        private VortexMovementIndicator(Indicator<Num> currentIndicator, Indicator<Num> previousIndicator) {
            super(currentIndicator);
            this.currentIndicator = currentIndicator;
            this.previousIndicator = previousIndicator;
            this.unstableBars = Math.max(currentIndicator.getCountOfUnstableBars(),
                    previousIndicator.getCountOfUnstableBars()) + 1;
        }

        @Override
        protected Num calculate(int index) {
            int beginIndex = getBarSeries().getBeginIndex();
            if (index <= beginIndex) {
                return NaN;
            }

            Num current = currentIndicator.getValue(index);
            Num previous = previousIndicator.getValue(index - 1);
            if (isInvalid(current) || isInvalid(previous)) {
                return NaN;
            }

            return current.minus(previous).abs();
        }

        @Override
        public int getCountOfUnstableBars() {
            return unstableBars;
        }
    }
}
