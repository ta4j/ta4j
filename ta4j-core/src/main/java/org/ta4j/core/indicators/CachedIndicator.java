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
package org.ta4j.core.indicators;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Cached {@link Indicator indicator}.
 *
 * <p>
 * Caches the calculated results of the indicator to avoid calculating the same
 * index of the indicator twice. The caching drastically speeds up access to
 * indicator values. Caching is especially recommended when indicators calculate
 * their values based on the values of other indicators. Such nested indicators
 * can call {@link #getValue(int)} multiple times without the need to
 * {@link #calculate(int)} again.
 *
 * <p>
 * This implementation uses a ring buffer for O(1) eviction when
 * {@code maximumBarCount} is set, and read-optimized locking for better
 * concurrency on cache hits.
 */
public abstract class CachedIndicator<T> extends AbstractIndicator<T> {

    /** The ring-buffer backed cache. */
    private final CachedBuffer<T> cache;

    /**
     * Should always be the index of the last (calculated) result in the cache.
     * Exposed for subclass access (e.g., RecursiveCachedIndicator).
     */
    protected volatile int highestResultIndex = -1;

    /** Lock protecting the last-bar cache check+compute sequence. */
    private final Object lastBarLock = new Object();

    // Last-bar caching state
    private volatile Bar lastBarRef;
    private volatile long lastBarTradeCount;
    private volatile Num lastBarClosePrice;
    private volatile T lastBarCachedResult;
    private volatile int lastBarCachedIndex = -1;

    // First-available-bar caching state (for indices < removedBarsCount)
    private final Object firstBarLock = new Object();
    private volatile int firstBarCachedRemovedBarsCount = -1;
    private volatile boolean firstBarHasCachedResult;
    private volatile T firstBarCachedResult;

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    protected CachedIndicator(BarSeries series) {
        super(series);
        int limit = series.getMaximumBarCount();
        this.cache = new CachedBuffer<>(limit);
    }

    /**
     * Constructor.
     *
     * @param indicator a related indicator (with a bar series)
     */
    protected CachedIndicator(Indicator<?> indicator) {
        this(indicator.getBarSeries());
    }

    /**
     * @param index the bar index
     * @return the value of the indicator
     */
    protected abstract T calculate(int index);

    @Override
    public T getValue(int index) {
        BarSeries series = getBarSeries();
        if (series == null) {
            // Series is null; the indicator doesn't need cache.
            // (e.g. simple computation of the value)
            // --> Calculating the value
            T result = calculate(index);
            if (log.isTraceEnabled()) {
                log.trace("{}({}): {}", this, index, result);
            }
            return result;
        }

        final int removedBarsCount = series.getRemovedBarsCount();
        final int endIndex = series.getEndIndex();

        T result;
        if (index < removedBarsCount) {
            // Result already removed from cache
            if (log.isTraceEnabled()) {
                log.trace("{}: result from bar {} already removed from cache, use {}-th instead",
                        getClass().getSimpleName(), index, removedBarsCount);
            }
            // Map all pruned indices to zero to avoid recursive backtracking into
            // removed history. calculate(0) for recursive indicators is the base case
            // and does not chase further into negative/removed indexes.
            result = getFirstBarValue(series, removedBarsCount);
        } else if (index == endIndex) {
            // Last bar: use mutation-aware caching
            result = getLastBarValue(index, series);
        } else {
            // Normal case: use the cache
            result = getOrComputeAndCache(index);
        }

        if (log.isTraceEnabled()) {
            log.trace("{}({}): {}", this, index, result);
        }
        return result;
    }

    /**
     * Gets the cached value or computes and caches it.
     *
     * @param index the series index
     * @return the indicator value
     */
    private T getOrComputeAndCache(int index) {
        T value = cache.getOrCompute(index, this::calculate);
        // Take the maximum to maintain the invariant that highestResultIndex
        // represents the highest index ever computed. The last bar uses separate
        // caching (getLastBarValue), so cache.getHighestResultIndex() might not
        // reflect the last bar access.
        highestResultIndex = Math.max(highestResultIndex, cache.getHighestResultIndex());
        return value;
    }

    /**
     * Gets the value for indices before the removed bars count.
     *
     * <p>
     * Bars with indices &lt; {@code removedBarsCount} are no longer available in
     * the series. The series maps such accesses to the first remaining bar. Caching
     * this value must be aware of {@code removedBarsCount} changes; otherwise a
     * cached value for index 0 may become stale when the series window advances.
     */
    private T getFirstBarValue(BarSeries series, int removedBarsCount) {
        if (firstBarHasCachedResult && firstBarCachedRemovedBarsCount == removedBarsCount) {
            return firstBarCachedResult;
        }

        // Compute outside the lock to avoid lock-order deadlocks with the cache lock.
        T computed = calculate(0);

        // If the series window advanced during computation, don't cache this value.
        if (series.getRemovedBarsCount() != removedBarsCount) {
            return computed;
        }

        synchronized (firstBarLock) {
            if (firstBarHasCachedResult && firstBarCachedRemovedBarsCount == removedBarsCount) {
                return firstBarCachedResult;
            }
            firstBarCachedRemovedBarsCount = removedBarsCount;
            firstBarCachedResult = computed;
            firstBarHasCachedResult = true;
            return computed;
        }
    }

    /**
     * Gets the value for the last bar with mutation-aware caching.
     *
     * <p>
     * The last bar (endIndex) is special because it may be mutated (e.g., via
     * {@link Bar#addTrade(Num, Num)} or {@link Bar#addPrice(Num)}). This method
     * caches the result but invalidates it if the bar has been modified since the
     * last computation. TODO: Note that we assume only tradeCount and closePrice
     * can change, if bars are modified to mutate other properties, this method will
     * not invalidate the cache.
     *
     * @param index  the series index (should be endIndex)
     * @param series the bar series
     * @return the indicator value
     */
    private T getLastBarValue(int index, BarSeries series) {
        synchronized (lastBarLock) {
            Bar currentBar = series.getLastBar();
            long currentTradeCount = currentBar.getTrades();
            Num currentClosePrice = currentBar.getClosePrice();

            // Check if we have a valid cached result for this bar
            if (index == lastBarCachedIndex && currentBar == lastBarRef && currentTradeCount == lastBarTradeCount
                    && (currentClosePrice == lastBarClosePrice
                            || (currentClosePrice != null && currentClosePrice.equals(lastBarClosePrice)))) {
                // Bar hasn't changed; return cached result
                return lastBarCachedResult;
            }

            // Bar changed or no cached result; compute new value
            T result = calculate(index);

            // Update last-bar cache state
            lastBarRef = currentBar;
            lastBarTradeCount = currentTradeCount;
            lastBarClosePrice = currentClosePrice;
            lastBarCachedResult = result;
            lastBarCachedIndex = index;

            // Update highestResultIndex to maintain consistency with other code paths
            // This ensures RecursiveCachedIndicator and other code that relies on
            // highestResultIndex behaves correctly when the last bar is accessed first
            highestResultIndex = index;

            return result;
        }
    }

    /**
     * Clears all cached values for this indicator.
     * <p>
     * Intended for indicators whose outputs can change retroactively (e.g., rolling
     * window recomputations). Regular indicators should not need to call this, as
     * cached values are assumed stable.
     */
    protected void invalidateCache() {
        cache.clear();
        highestResultIndex = -1;
        clearLastBarCache();
        clearFirstBarCache();
    }

    /**
     * Clears cached values from the specified index (inclusive) to the end of the
     * cache. Values before the index remain cached.
     *
     * @param index the first index to invalidate; if negative, the entire cache is
     *              cleared
     */
    protected void invalidateFrom(int index) {
        cache.invalidateFrom(index);
        int cacheHighest = cache.getHighestResultIndex();

        // Also clear last-bar cache if affected
        boolean lastBarCacheCleared = lastBarCachedIndex >= index;
        if (lastBarCacheCleared) {
            clearLastBarCache();
        }
        if (index <= 0) {
            clearFirstBarCache();
        }

        // Preserve last-bar cache knowledge when it is still valid. This avoids
        // decreasing highestResultIndex when the primary cache does not contain the
        // last-bar result.
        int lastBarIndex = lastBarCacheCleared ? -1 : lastBarCachedIndex;
        highestResultIndex = Math.max(cacheHighest, lastBarIndex);
    }

    /**
     * Clears the last-bar cache state.
     */
    private void clearLastBarCache() {
        synchronized (lastBarLock) {
            lastBarRef = null;
            lastBarTradeCount = 0;
            lastBarClosePrice = null;
            lastBarCachedResult = null;
            lastBarCachedIndex = -1;
        }
    }

    private void clearFirstBarCache() {
        synchronized (firstBarLock) {
            firstBarCachedRemovedBarsCount = -1;
            firstBarHasCachedResult = false;
            firstBarCachedResult = null;
        }
    }

    /**
     * Returns the underlying cache buffer.
     * <p>
     * For internal use by subclasses (e.g., RecursiveCachedIndicator).
     *
     * @return the cache buffer
     */
    CachedBuffer<T> getCache() {
        return cache;
    }
}
