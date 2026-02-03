/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.supportresistance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
 * @since 0.19
 */
public abstract class AbstractBounceCountIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> priceIndicator;
    private final int lookbackLength;
    private final Num bucketSize;
    private final transient Map<Integer, Integer> bounceIndexCache = new HashMap<>();

    /**
     * Constructor using {@link ClosePriceIndicator}.
     *
     * @param series      the backing bar series
     * @param bucketSize  the absolute bucket size for grouping bounce prices
     * @since 0.19
     */
    protected AbstractBounceCountIndicator(BarSeries series, Num bucketSize) {
        this(new ClosePriceIndicator(series), 0, bucketSize);
    }

    /**
     * Constructor using a custom price indicator.
     *
     * @param priceIndicator the price indicator to analyse
     * @param bucketSize     the absolute bucket size for grouping bounce prices
     * @since 0.19
     */
    protected AbstractBounceCountIndicator(Indicator<Num> priceIndicator, Num bucketSize) {
        this(priceIndicator, 0, bucketSize);
    }

    /**
     * Constructor with full configuration.
     *
     * @param priceIndicator the price indicator to analyse
     * @param lookbackLength the number of bars to evaluate (non-positive for the
     *                       full history)
     * @param bucketSize     the absolute bucket size for grouping bounce prices
     * @since 0.19
     */
    protected AbstractBounceCountIndicator(Indicator<Num> priceIndicator, int lookbackLength, Num bucketSize) {
        super(priceIndicator);
        this.priceIndicator = Objects.requireNonNull(priceIndicator, "priceIndicator must not be null");
        this.lookbackLength = lookbackLength;
        this.bucketSize = Objects.requireNonNull(bucketSize, "bucketSize must not be null");
        BarSeries series = Objects.requireNonNull(priceIndicator.getBarSeries(), "indicator must reference a bar series");
        if (bucketSize.isLessThan(series.numFactory().zero())) {
            throw new IllegalArgumentException("bucketSize must be greater than or equal to zero");
        }
    }

    @Override
    protected Num calculate(int index) {
        BarSeries series = getBarSeries();
        if (series == null || index < series.getBeginIndex()) {
            bounceIndexCache.put(index, -1);
            return NaN;
        }

        int startIndex = computeStartIndex(index, series);
        List<PriceBucket> buckets = tallyBounces(startIndex, index);
        PriceBucket bestBucket = selectBestBucket(buckets);

        if (bestBucket == null) {
            bounceIndexCache.put(index, -1);
            return NaN;
        }

        bounceIndexCache.put(index, bestBucket.getLastIndex());
        return bestBucket.getRepresentativePrice();
    }

    /**
     * Returns the index of the most recent bounce that belongs to the dominant
     * bucket at the requested bar.
     *
     * @param index the bar index
     * @return the most recent bounce index or {@code -1} when no bounce is
     *         available
     * @since 0.19
     */
    public int getBounceIndex(int index) {
        getValue(index);
        return bounceIndexCache.getOrDefault(index, -1);
    }

    private int computeStartIndex(int index, BarSeries series) {
        if (lookbackLength <= 0) {
            return series.getBeginIndex();
        }
        int desiredStart = index - lookbackLength + 1;
        return Math.max(series.getBeginIndex(), desiredStart);
    }

    private List<PriceBucket> tallyBounces(int startIndex, int endIndex) {
        List<PriceBucket> buckets = new ArrayList<>();
        Num zero = getBarSeries().numFactory().zero();

        int seededIndex = seedPreviousIndex(startIndex);
        Num previousValue = seededIndex >= 0 ? priceIndicator.getValue(seededIndex) : null;
        int previousIndex = seededIndex;
        Integer previousDirection = null;

        for (int i = startIndex; i <= endIndex; i++) {
            Num value = priceIndicator.getValue(i);
            if (Num.isNaNOrNull(value)) {
                continue;
            }

            if (Num.isNaNOrNull(previousValue)) {
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

    private int seedPreviousIndex(int startIndex) {
        for (int i = startIndex - 1; i >= getBarSeries().getBeginIndex(); i--) {
            Num value = priceIndicator.getValue(i);
            if (!Num.isNaNOrNull(value)) {
                return i;
            }
        }
        return -1;
    }

    private void registerBounce(List<PriceBucket> buckets, Num bouncePrice, int bounceIndex) {
        for (PriceBucket bucket : buckets) {
            if (bucket.tryInclude(bouncePrice, bounceIndex, bucketSize)) {
                return;
            }
        }
        buckets.add(new PriceBucket(bouncePrice, bounceIndex, getBarSeries().numFactory()));
    }

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
     * @since 0.19
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
     * @since 0.19
     */
    protected abstract boolean shouldRecordBounce(int previousDirection, int newDirection);

    private static final class PriceBucket {
        private Num representativePrice;
        private int count;
        private int lastIndex;
        private final NumFactory numFactory;

        private PriceBucket(Num initialPrice, int initialIndex, NumFactory numFactory) {
            this.representativePrice = initialPrice;
            this.count = 1;
            this.lastIndex = initialIndex;
            this.numFactory = numFactory;
        }

        private boolean tryInclude(Num value, int index, Num bucketSize) {
            if (value.minus(representativePrice).abs().isLessThanOrEqual(bucketSize)) {
                Num scaledCurrent = representativePrice.multipliedBy(numFactory.numOf(count));
                representativePrice = scaledCurrent.plus(value).dividedBy(numFactory.numOf(count + 1));
                count++;
                lastIndex = index;
                return true;
            }
            return false;
        }

        private int getCount() {
            return count;
        }

        private Num getRepresentativePrice() {
            return representativePrice;
        }

        private int getLastIndex() {
            return lastIndex;
        }
    }
}
