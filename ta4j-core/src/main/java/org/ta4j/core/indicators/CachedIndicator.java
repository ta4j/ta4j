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

    // Last-bar caching state
    private volatile Bar lastBarRef;
    private volatile long lastBarTradeCount;
    private volatile Num lastBarClosePrice;
    private volatile T lastBarCachedResult;
    private volatile int lastBarCachedIndex = -1;

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
            // Return the result for the first available bar
            result = getOrComputeAndCache(removedBarsCount);
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
        return cache.getOrCompute(index, i -> {
            T computed = calculate(i);
            updateHighestResultIndex(i);
            return computed;
        });
    }

    /**
     * Gets the value for the last bar with mutation-aware caching.
     *
     * <p>
     * The last bar (endIndex) is special because it may be mutated (e.g., via
     * {@link Bar#addTrade(Num, Num)} or {@link Bar#addPrice(Num)}). This method
     * caches the result but invalidates it if the bar has been modified since the
     * last computation.
     *
     * @param index  the series index (should be endIndex)
     * @param series the bar series
     * @return the indicator value
     */
    private T getLastBarValue(int index, BarSeries series) {
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

        return result;
    }

    /**
     * Updates the highest result index if the given index is higher.
     *
     * @param index the computed index
     */
    private void updateHighestResultIndex(int index) {
        if (index > highestResultIndex) {
            highestResultIndex = index;
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
        int newHighest = cache.getHighestResultIndex();
        highestResultIndex = newHighest;

        // Also clear last-bar cache if affected
        if (lastBarCachedIndex >= index) {
            clearLastBarCache();
        }
    }

    /**
     * Clears the last-bar cache state.
     */
    private void clearLastBarCache() {
        lastBarRef = null;
        lastBarTradeCount = 0;
        lastBarClosePrice = null;
        lastBarCachedResult = null;
        lastBarCachedIndex = -1;
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
