/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.supportresistance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Base class for support and resistance indicators that tally bounce-offs at
 * turning points.
 *
 * <p>
 * A bounce is recorded whenever the price direction flips (up-to-down or
 * down-to-up). The class groups these turning-point prices into configurable
 * buckets and returns the representative price of the most frequent bucket.
 *
 * @since 0.22.3
 */
public abstract class AbstractBounceCountIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> priceIndicator;
    private final int lookbackCount;
    private final Num bucketSize;
    private transient Map<Integer, Integer> bounceIndexCache;
    private transient int lastPrunedCacheBeginIndex;

    /**
     * Constructor using {@link ClosePriceIndicator}.
     *
     * @param series     the backing bar series
     * @param bucketSize the absolute bucket size for grouping bounce prices
     * @since 0.22.3
     */
    protected AbstractBounceCountIndicator(BarSeries series, Num bucketSize) {
        this(new ClosePriceIndicator(series), 0, bucketSize);
    }

    /**
     * Constructor using a custom price indicator.
     *
     * @param priceIndicator the price indicator to analyse
     * @param bucketSize     the absolute bucket size for grouping bounce prices
     * @since 0.22.3
     */
    protected AbstractBounceCountIndicator(Indicator<Num> priceIndicator, Num bucketSize) {
        this(priceIndicator, 0, bucketSize);
    }

    /**
     * Constructor with full configuration.
     *
     * @param priceIndicator the price indicator to analyse
     * @param lookbackCount  the number of bars to evaluate (non-positive for the
     *                       full history)
     * @param bucketSize     the absolute bucket size for grouping bounce prices
     * @since 0.22.3
     */
    protected AbstractBounceCountIndicator(Indicator<Num> priceIndicator, int lookbackCount, Num bucketSize) {
        super(priceIndicator);
        this.priceIndicator = Objects.requireNonNull(priceIndicator, "priceIndicator must not be null");
        this.lookbackCount = lookbackCount;
        this.bucketSize = Objects.requireNonNull(bucketSize, "bucketSize must not be null");
        BarSeries series = Objects.requireNonNull(priceIndicator.getBarSeries(),
                "indicator must reference a bar series");
        if (isInvalid(bucketSize) || bucketSize.isLessThan(series.numFactory().zero())) {
            throw new IllegalArgumentException("bucketSize must be greater than or equal to zero");
        }
        this.bounceIndexCache = new ConcurrentHashMap<>();
        this.lastPrunedCacheBeginIndex = series.getBeginIndex() - 1;
    }

    /**
     * Calculates the indicator value at the requested index.
     */
    @Override
    protected Num calculate(int index) {
        BarSeries series = getBarSeries();
        if (series == null || index < series.getBeginIndex()) {
            bounceIndexCache().put(index, -1);
            return NaN;
        }
        pruneBounceIndexCache(series);
        if (index < series.getBeginIndex() + getCountOfUnstableBars()) {
            bounceIndexCache().put(index, -1);
            return NaN;
        }

        int startIndex = computeStartIndex(index, series);
        List<PriceBucket> buckets = tallyBounces(startIndex, index);
        PriceBucket bestBucket = selectBestBucket(buckets);

        if (bestBucket == null) {
            bounceIndexCache().put(index, -1);
            return NaN;
        }

        bounceIndexCache().put(index, bestBucket.getLastIndex());
        return bestBucket.getRepresentativePrice();
    }

    /**
     * Returns the unstable warmup covering source indicator warmup plus the
     * configured look-back window.
     *
     * @return number of unstable bars
     * @since 0.22.3
     */
    @Override
    public int getCountOfUnstableBars() {
        int componentUnstableBars = priceIndicator.getCountOfUnstableBars();
        return componentUnstableBars + Math.max(0, lookbackCount - 1);
    }

    /**
     * Returns the index of the most recent bounce that belongs to the dominant
     * bucket at the requested bar.
     *
     * @param index the bar index
     * @return the most recent bounce index or {@code -1} when no bounce is
     *         available
     * @since 0.22.3
     */
    public int getBounceIndex(int index) {
        getValue(index);
        return bounceIndexCache().getOrDefault(index, -1);
    }

    /**
     * Computes start index.
     */
    private int computeStartIndex(int index, BarSeries series) {
        if (lookbackCount <= 0) {
            return series.getBeginIndex();
        }
        int desiredStart = index - lookbackCount + 1;
        return Math.max(series.getBeginIndex(), desiredStart);
    }

    /**
     * Implements tally bounces.
     */
    private List<PriceBucket> tallyBounces(int startIndex, int endIndex) {
        List<PriceBucket> buckets = new ArrayList<>();
        Num zero = getBarSeries().numFactory().zero();

        int seededIndex = seedPreviousIndex(startIndex);
        Num previousValue = seededIndex >= 0 ? priceIndicator.getValue(seededIndex) : null;
        int previousIndex = seededIndex;
        Integer previousDirection = null;

        for (int i = startIndex; i <= endIndex; i++) {
            Num value = priceIndicator.getValue(i);
            if (isInvalid(value)) {
                continue;
            }

            if (isInvalid(previousValue)) {
                previousValue = value;
                previousIndex = i;
                continue;
            }

            Num delta = value.minus(previousValue);
            if (delta.isZero()) {
                previousValue = value;
                previousIndex = i;
                continue;
            }

            int direction = delta.isGreaterThan(zero) ? 1 : -1;
            if (previousDirection == null) {
                previousDirection = direction;
                previousValue = value;
                previousIndex = i;
                continue;
            }

            if (direction != previousDirection && previousIndex >= startIndex
                    && shouldRecordBounce(previousDirection, direction)) {
                registerBounce(buckets, previousValue, previousIndex);
            }

            previousDirection = direction;
            previousValue = value;
            previousIndex = i;
        }

        return buckets;
    }

    /**
     * Seeds previous index.
     */
    private int seedPreviousIndex(int startIndex) {
        for (int i = startIndex - 1; i >= getBarSeries().getBeginIndex(); i--) {
            Num value = priceIndicator.getValue(i);
            if (!isInvalid(value)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Registers bounce.
     */
    private void registerBounce(List<PriceBucket> buckets, Num bouncePrice, int bounceIndex) {
        for (PriceBucket bucket : buckets) {
            if (bucket.tryInclude(bouncePrice, bounceIndex, bucketSize)) {
                return;
            }
        }
        buckets.add(new PriceBucket(bouncePrice, bounceIndex, getBarSeries().numFactory()));
    }

    /**
     * Selects best bucket.
     */
    private PriceBucket selectBestBucket(List<PriceBucket> buckets) {
        PriceBucket best = null;
        for (PriceBucket bucket : buckets) {
            if (best == null || bucket.getCount() > best.getCount()) {
                best = bucket;
                continue;
            }
            if (bucket.getCount() == best.getCount()) {
                Num bucketPrice = bucket.getRepresentativePrice();
                Num bestPrice = best.getRepresentativePrice();
                if (preferLowerPriceOnTie()) {
                    if (bucketPrice.isLessThan(bestPrice)) {
                        best = bucket;
                        continue;
                    }
                } else {
                    if (bucketPrice.isGreaterThan(bestPrice)) {
                        best = bucket;
                        continue;
                    }
                }
                if (bucketPrice.isEqual(bestPrice) && bucket.getLastIndex() > best.getLastIndex()) {
                    best = bucket;
                }
            }
        }
        return best;
    }

    /**
     * Indicates whether tied buckets should favour the lower price.
     *
     * @return {@code true} to prefer lower prices, {@code false} to prefer higher
     *         prices
     * @since 0.22.3
     */
    protected abstract boolean preferLowerPriceOnTie();

    /**
     * Indicates whether a direction change should be tallied as a bounce for this
     * indicator.
     *
     * @param previousDirection the direction before the flip ({@code 1} for up,
     *                          {@code -1} for down)
     * @param newDirection      the direction after the flip ({@code 1} for up,
     *                          {@code -1} for down)
     * @return {@code true} when the flip counts as a bounce, {@code false}
     *         otherwise
     * @since 0.22.3
     */
    protected abstract boolean shouldRecordBounce(int previousDirection, int newDirection);

    /**
     * Returns whether invalid.
     */
    private static boolean isInvalid(Num value) {
        return Num.isNaNOrNull(value) || Double.isNaN(value.doubleValue());
    }

    private static final class PriceBucket {
        private final Num anchorPrice;
        private Num representativePrice;
        private int count;
        private int lastIndex;
        private final NumFactory numFactory;

        /**
         * Implements price bucket.
         */
        private PriceBucket(Num initialPrice, int initialIndex, NumFactory numFactory) {
            this.anchorPrice = initialPrice;
            this.representativePrice = initialPrice;
            this.count = 1;
            this.lastIndex = initialIndex;
            this.numFactory = numFactory;
        }

        /**
         * Implements try include.
         */
        private boolean tryInclude(Num value, int index, Num bucketSize) {
            if (value.minus(anchorPrice).abs().isLessThanOrEqual(bucketSize)) {
                Num scaledCurrent = representativePrice.multipliedBy(numFactory.numOf(count));
                representativePrice = scaledCurrent.plus(value).dividedBy(numFactory.numOf(count + 1));
                count++;
                lastIndex = index;
                return true;
            }
            return false;
        }

        /**
         * Returns the count.
         */
        private int getCount() {
            return count;
        }

        /**
         * Returns the representative price.
         */
        private Num getRepresentativePrice() {
            return representativePrice;
        }

        /**
         * Returns the last index.
         */
        private int getLastIndex() {
            return lastIndex;
        }
    }

    /**
     * Implements bounce index cache.
     */
    private Map<Integer, Integer> bounceIndexCache() {
        if (bounceIndexCache == null) {
            bounceIndexCache = new ConcurrentHashMap<>();
        }
        return bounceIndexCache;
    }

    /**
     * Prunes bounce index cache.
     */
    private void pruneBounceIndexCache(BarSeries series) {
        int beginIndex = series.getBeginIndex();
        if (beginIndex <= lastPrunedCacheBeginIndex) {
            return;
        }
        bounceIndexCache().keySet().removeIf(cacheIndex -> cacheIndex < beginIndex);
        lastPrunedCacheBeginIndex = beginIndex;
    }
}
