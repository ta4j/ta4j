/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import java.util.concurrent.locks.ReentrantLock;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.num.DecimalNum;
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
    private final transient ReentrantLock rollingLock = new ReentrantLock();
    private transient volatile int rollingIndex = -1;
    private transient volatile int rollingStart = -1;
    private transient volatile long rollingRevision = -1;
    private transient Num rollingWeighted;
    private transient Num rollingSquared;
    private transient Num rollingVolume;
    private transient int rollingUpdates;

    /**
     * Creates a new AbstractVWAPIndicator instance.
     */
    protected AbstractVWAPIndicator(Indicator<Num> priceIndicator, Indicator<Num> volumeIndicator) {
        super(priceIndicator, volumeIndicator);
        this.priceIndicator = priceIndicator;
        this.volumeIndicator = volumeIndicator;
    }

    abstract AbstractVWAPIndicator copy();

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
    @SuppressFBWarnings(value = "AT_STALE_THREAD_WRITE_OF_PRIMITIVE", justification = "Rolling aggregate state is read and written only while holding rollingLock.")
    protected final VWAPValues collectValues(int startIndex, int endIndex) {
        NumFactory factory = getBarSeries().numFactory();
        boolean supported = IndicatorUtils.isSameSeries(priceIndicator.getBarSeries(), volumeIndicator.getBarSeries())
                && (!(factory.zero() instanceof DecimalNum decimal) || decimal.getMathContext().getPrecision() >= 16);
        var snapshot = supported ? synchronizeCacheWithSeries(getBarSeries()) : null;
        boolean terminal = snapshot != null && endIndex == snapshot.endIndex();
        if (terminal && endIndex > startIndex) {
            getValue(endIndex - 1);
        }
        int checkpoint = rollingIndex;
        int previousStart = rollingStart;
        long revision = rollingRevision;
        if (snapshot != null && checkpoint == endIndex - 1 && previousStart >= getBarSeries().getBeginIndex()
                && startIndex >= previousStart && startIndex <= previousStart + 1 && isCached(checkpoint)) {
            var changes = getBarSeries().getBarSeriesChangeSnapshot(revision);
            Num price = priceIndicator.getValue(endIndex);
            Num volume = volumeIndicator.getValue(endIndex);
            Num oldPrice = startIndex == previousStart ? null : priceIndicator.getValue(previousStart);
            Num oldVolume = startIndex == previousStart ? null : volumeIndicator.getValue(previousStart);
            VWAPValues result = advance(checkpoint, previousStart, revision, changes, startIndex, endIndex, price,
                    volume, oldPrice, oldVolume, terminal, factory);
            if (result != null) {
                return result;
            }
        }
        Num weightedPriceSum = factory.zero();
        Num weightedSquareSum = factory.zero();
        Num volumeSum = factory.zero();
        boolean hasContribution = false;

        for (int i = startIndex; i <= endIndex; i++) {
            Num price = priceIndicator.getValue(i);
            Num volume = volumeIndicator.getValue(i);
            if (!Num.isFinite(price) || !Num.isFinite(volume) || volume.isNegative()) {
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
        if (snapshot != null && !terminal && snapshot.revision() >= 0 && Num.isFinite(weightedPriceSum)
                && Num.isFinite(weightedSquareSum) && Num.isFinite(volumeSum) && rollingLock.tryLock()) {
            try {
                rollingWeighted = weightedPriceSum;
                rollingSquared = weightedSquareSum;
                rollingVolume = volumeSum;
                rollingUpdates = 0;
                rollingStart = startIndex;
                rollingIndex = endIndex;
                rollingRevision = snapshot.revision();
            } finally {
                rollingLock.unlock();
            }
        }

        return VWAPValues.valid(weightedPriceSum, weightedSquareSum, volumeSum, factory);
    }

    private VWAPValues advance(int expected, int previousStart, long expectedRevision,
            BarSeries.BarSeriesChangeSnapshot changes, int start, int end, Num price, Num volume, Num oldPrice,
            Num oldVolume, boolean terminal, NumFactory factory) {
        if (expectedRevision < 0 || changes.revision() < 0 || !Num.isFinite(price) || !Num.isFinite(volume)
                || volume.isNegative()
                || (oldPrice != null && (!Num.isFinite(oldPrice) || !Num.isFinite(oldVolume) || oldVolume.isNegative()))
                || !rollingLock.tryLock()) {
            return null;
        }
        try {
            if (rollingIndex != expected || rollingStart != previousStart || rollingRevision != expectedRevision
                    || end != expected + 1 || rollingUpdates >= end - start + 1
                    || (changes.earliestChangedIndex() >= 0 && changes.earliestChangedIndex() <= expected)
                    || changes.removedThroughIndex() >= previousStart) {
                return null;
            }
            Num weighted = rollingWeighted;
            Num squared = rollingSquared;
            Num totalVolume = rollingVolume;
            if (oldPrice != null && !oldVolume.isZero()) {
                weighted = weighted.minus(oldPrice.multipliedBy(oldVolume));
                squared = squared.minus(oldPrice.multipliedBy(oldPrice).multipliedBy(oldVolume));
                totalVolume = totalVolume.minus(oldVolume);
            }
            if (!volume.isZero()) {
                weighted = weighted.plus(price.multipliedBy(volume));
                squared = squared.plus(price.multipliedBy(price).multipliedBy(volume));
                totalVolume = totalVolume.plus(volume);
            }
            Num threshold = factory.numOf(100000000);
            if (!Num.isFinite(weighted) || !Num.isFinite(squared) || !Num.isFinite(totalVolume)
                    || totalVolume.isNegative() || squared.isNegative()
                    || (oldPrice != null && ((!rollingWeighted.isZero()
                            && weighted.abs().isLessThan(rollingWeighted.abs().dividedBy(threshold)))
                            || (!rollingVolume.isZero()
                                    && totalVolume.abs().isLessThan(rollingVolume.abs().dividedBy(threshold)))
                            || (!rollingSquared.isZero()
                                    && squared.abs().isLessThan(rollingSquared.abs().dividedBy(threshold)))))) {
                return null;
            }
            if (!totalVolume.isZero()) {
                Num mean = weighted.dividedBy(totalVolume);
                Num squareMean = squared.dividedBy(totalVolume);
                Num variance = squareMean.minus(mean.multipliedBy(mean));
                if (!Num.isFinite(variance)
                        || variance.abs().isLessThanOrEqual(squareMean.abs().dividedBy(threshold))) {
                    return null;
                }
            }
            if (!terminal) {
                rollingWeighted = weighted;
                rollingSquared = squared;
                rollingVolume = totalVolume;
                rollingStart = start;
                rollingUpdates++;
                rollingIndex = end;
            }
            rollingRevision = changes.revision();
            return totalVolume.isZero() ? VWAPValues.empty(factory)
                    : VWAPValues.valid(weighted, squared, totalVolume, factory);
        } catch (ArithmeticException e) {
            return null;
        } finally {
            rollingLock.unlock();
        }
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
