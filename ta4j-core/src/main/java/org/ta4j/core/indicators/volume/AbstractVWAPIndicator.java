/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Abstract base class for indicators that rely on the volume-weighted average
 * price (VWAP) window.
 * <p>
 * Implementations define the window start index (for example a rolling window
 * or an anchor that resets after notable events) and this class performs the
 * shared weighted aggregation with appropriate NaN propagation.
 *
 * @since 0.19
 */
public abstract class AbstractVWAPIndicator extends CachedIndicator<Num> {

    protected final Indicator<Num> priceIndicator;
    protected final Indicator<Num> volumeIndicator;

    /**
     * Creates a new AbstractVWAPIndicator instance.
     */
    protected AbstractVWAPIndicator(Indicator<Num> priceIndicator, Indicator<Num> volumeIndicator) {
        super(IndicatorUtils.requireSameSeries(priceIndicator, volumeIndicator));
        this.priceIndicator = priceIndicator;
        this.volumeIndicator = volumeIndicator;
    }

    /**
     * Calculates the indicator value at the requested index.
     */
    @Override
    protected final Num calculate(int index) {
        if (index < getBarSeries().getBeginIndex() + getCountOfUnstableBars()) {
            return NaN.NaN;
        }
        int startIndex = getWindowStartIndex(index);
        if (startIndex > index) {
            return NaN.NaN;
        }
        VWAPValues values = collectValues(startIndex, index);
        if (!values.isValid()) {
            return values.asNaN();
        }
        return map(values);
    }

    /**
     * Maps the aggregated VWAP values to the indicator output.
     *
     * @param values aggregated VWAP values
     * @return indicator value for the aggregation
     *
     * @since 0.19
     */
    protected Num map(VWAPValues values) {
        return values.mean();
    }

    /**
     * Returns the inclusive window start index for the given index.
     *
     * @param index the current bar index
     * @return the inclusive start index of the VWAP window
     *
     * @since 0.19
     */
    public final int getWindowStartIndex(int index) {
        int beginIndex = getBarSeries().getBeginIndex();
        int resolved = resolveWindowStartIndex(index);
        if (resolved < beginIndex) {
            return beginIndex;
        }
        return resolved;
    }

    /**
     * Implementations must resolve the inclusive window start index for the
     * provided bar index. The returned index may be prior to the bar series begin
     * index; the {@link #getWindowStartIndex(int)} helper will clamp it as needed.
     *
     * @param index the current bar index
     * @return the inclusive start index of the VWAP window before clamping
     *
     * @since 0.19
     */
    protected abstract int resolveWindowStartIndex(int index);

    /**
     * Collects the weighted aggregation for the requested range.
     *
     * @param startIndex inclusive window start
     * @param endIndex   inclusive window end
     * @return aggregation data
     *
     * @since 0.19
     */
    protected final VWAPValues collectValues(int startIndex, int endIndex) {
        NumFactory factory = getBarSeries().numFactory();
        Num weightedPriceSum = factory.zero();
        Num weightedSquareSum = factory.zero();
        Num volumeSum = factory.zero();
        boolean hasContribution = false;

        for (int i = startIndex; i <= endIndex; i++) {
            Num price = priceIndicator.getValue(i);
            Num volume = volumeIndicator.getValue(i);
            if (IndicatorUtils.isInvalid(price) || IndicatorUtils.isInvalid(volume) || volume.isNegative()) {
                return VWAPValues.invalid(factory);
            }
            if (volume.isZero()) {
                continue;
            }
            hasContribution = true;
            Num weightedPrice = price.multipliedBy(volume);
            weightedPriceSum = weightedPriceSum.plus(weightedPrice);
            weightedSquareSum = weightedSquareSum.plus(price.multipliedBy(price).multipliedBy(volume));
            volumeSum = volumeSum.plus(volume);
        }

        if (!hasContribution || volumeSum.isZero()) {
            return VWAPValues.empty(factory);
        }

        return VWAPValues.valid(weightedPriceSum, weightedSquareSum, volumeSum, factory);
    }

    /**
     * Aggregated VWAP values used by derived indicators.
     */
    protected static final class VWAPValues {

        private final Num weightedPriceSum;
        private final Num weightedSquareSum;
        private final Num volumeSum;
        private final NumFactory factory;
        private final boolean valid;

        /**
         * Implements invalid.
         */
        private static VWAPValues invalid(NumFactory factory) {
            return new VWAPValues(NaN.NaN, NaN.NaN, NaN.NaN, factory, false);
        }

        /**
         * Implements empty.
         */
        private static VWAPValues empty(NumFactory factory) {
            return new VWAPValues(NaN.NaN, NaN.NaN, NaN.NaN, factory, false);
        }

        private static VWAPValues valid(Num weightedPriceSum, Num weightedSquareSum, Num volumeSum,
                NumFactory factory) {
            return new VWAPValues(weightedPriceSum, weightedSquareSum, volumeSum, factory, true);
        }

        private VWAPValues(Num weightedPriceSum, Num weightedSquareSum, Num volumeSum, NumFactory factory,
                boolean valid) {
            this.weightedPriceSum = weightedPriceSum;
            this.weightedSquareSum = weightedSquareSum;
            this.volumeSum = volumeSum;
            this.factory = factory;
            this.valid = valid;
        }

        /**
         * Returns whether valid.
         */
        public boolean isValid() {
            return valid;
        }

        /**
         * Implements as na n.
         */
        public Num asNaN() {
            return NaN.NaN;
        }

        /**
         * Implements mean.
         */
        public Num mean() {
            return weightedPriceSum.dividedBy(volumeSum);
        }

        /**
         * Implements weighted square mean.
         */
        public Num weightedSquareMean() {
            return weightedSquareSum.dividedBy(volumeSum);
        }

        /**
         * Returns the volume sum.
         */
        public Num getVolumeSum() {
            return volumeSum;
        }

        /**
         * Returns the factory.
         */
        public NumFactory getFactory() {
            return factory;
        }
    }

}
