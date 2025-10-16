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

import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;

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
 */
public abstract class CachedIndicator<T> extends AbstractIndicator<T> {

    private static final int RETAINED_HISTORICAL_RESULTS = 1;
    private static final int RECURSION_THRESHOLD = 100;

    /** Thread-safe map of cached results. */
    private final ConcurrentMap<Integer, T> results;

    /** Lock protecting structural cache changes. */
    private final ReentrantLock cacheLock = new ReentrantLock();

    /** Guards against recursive prefill loops on the same thread. */
    private static final ThreadLocal<Integer> PREFILL_DEPTH = ThreadLocal.withInitial(() -> 0);

    /**
     * Should always be the index of the last (calculated) result in
     * {@link #results}.
     */
    protected volatile int highestResultIndex = -1;

    /** Lowest index currently cached. */
    private volatile int lowestResultIndex = Integer.MAX_VALUE;

    /** Removed bars count already processed. */
    private volatile int lastRemovalCount = -1;

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    protected CachedIndicator(BarSeries series) {
        super(series);
        this.results = new ConcurrentHashMap<>();
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
    public synchronized T getValue(int index) {
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

        // Series is not null

        final int removedBarsCount = series.getRemovedBarsCount();
        final int maximumResultCount = series.getMaximumBarCount();

        evictExpiredResults(removedBarsCount, maximumResultCount);

        int normalizedIndex = normalizeIndex(index);
        if (lowestResultIndex != Integer.MAX_VALUE && normalizedIndex < lowestResultIndex
                && normalizedIndex < removedBarsCount) {
            normalizedIndex = lowestResultIndex;
        }

        prefillIntermediateResults(normalizedIndex, removedBarsCount, maximumResultCount);

        T result;
        if (normalizedIndex == series.getEndIndex()) {
            // Don't cache result if last bar
            result = calculate(normalizedIndex);
        } else {
            result = results.get(normalizedIndex);
            if (result == null) {
                result = computeAndCacheValue(normalizedIndex, removedBarsCount, maximumResultCount);
            }
        }
        if (log.isTraceEnabled()) {
            log.trace("{}({}): {}", this, index, result);
        }
        return result;
    }

    private int normalizeIndex(int index) {
        return Math.max(index, 0);
    }

    private void prefillIntermediateResults(int targetIndex, int removedBarsCount, int maximumResultCount) {
        if (targetIndex <= 0 || PREFILL_DEPTH.get() > 0) {
            return;
        }

        int startIndex = Math.max(removedBarsCount, highestResultIndex);
        if (startIndex < 0) {
            startIndex = Math.max(0, removedBarsCount);
        }

        if (targetIndex - startIndex <= RECURSION_THRESHOLD) {
            return;
        }

        PREFILL_DEPTH.set(PREFILL_DEPTH.get() + 1);
        try {
            for (int index = startIndex; index < targetIndex; index++) {
                if (index < 0) {
                    continue;
                }
                if (results.containsKey(index)) {
                    continue;
                }
                computeAndCacheValue(index, removedBarsCount, maximumResultCount);
            }
        } finally {
            int depth = PREFILL_DEPTH.get() - 1;
            if (depth <= 0) {
                PREFILL_DEPTH.remove();
            } else {
                PREFILL_DEPTH.set(depth);
            }
        }
    }

    private T computeAndCacheValue(int index, int removedBarsCount, int maximumResultCount) {
        cacheLock.lock();
        try {
            T computedValue = results.get(index);
            if (computedValue != null) {
                return computedValue;
            }

            computedValue = calculate(index);
            results.put(index, computedValue);
            highestResultIndex = Math.max(highestResultIndex, index);
            lowestResultIndex = Math.min(lowestResultIndex, index);

            enforceMaximumSize(removedBarsCount, maximumResultCount);

            return computedValue;
        } finally {
            cacheLock.unlock();
        }
    }

    private void evictExpiredResults(int removedBarsCount, int maximumResultCount) {
        if (results.isEmpty()) {
            if (removedBarsCount > lastRemovalCount) {
                lastRemovalCount = removedBarsCount;
            }
            return;
        }

        boolean shouldProcessRemovals = removedBarsCount > lastRemovalCount;
        boolean shouldTrim = maximumResultCount != Integer.MAX_VALUE && highestResultIndex >= 0
                && lowestResultIndex != Integer.MAX_VALUE
                && highestResultIndex - Math.max(lowestResultIndex, removedBarsCount) + 1 > maximumResultCount;

        if (!shouldProcessRemovals && !shouldTrim) {
            return;
        }

        cacheLock.lock();
        try {
            if (shouldProcessRemovals) {
                removeResultsBefore(removedBarsCount);
                lastRemovalCount = removedBarsCount;
            }

            if (maximumResultCount != Integer.MAX_VALUE) {
                int threshold = highestResultIndex - maximumResultCount + 1;
                if (threshold > Integer.MIN_VALUE) {
                    removeResultsBefore(Math.max(removedBarsCount, threshold));
                }
            }
        } finally {
            cacheLock.unlock();
        }
    }

    private void enforceMaximumSize(int removedBarsCount, int maximumResultCount) {
        if (maximumResultCount == Integer.MAX_VALUE) {
            return;
        }
        int threshold = highestResultIndex - maximumResultCount + 1;
        if (threshold > Integer.MIN_VALUE) {
            removeResultsBefore(Math.max(removedBarsCount, threshold));
        }
    }

    private void removeResultsBefore(int threshold) {
        final int retentionFloor = Math.max(0, threshold - RETAINED_HISTORICAL_RESULTS);

        if (results.isEmpty()) {
            lowestResultIndex = Integer.MAX_VALUE;
            if (highestResultIndex < retentionFloor - 1) {
                highestResultIndex = retentionFloor - 1;
            }
            return;
        }

        results.keySet().removeIf(key -> key < retentionFloor);

        if (results.isEmpty()) {
            lowestResultIndex = Integer.MAX_VALUE;
            if (highestResultIndex < retentionFloor - 1) {
                highestResultIndex = retentionFloor - 1;
            }
            return;
        }

        lowestResultIndex = results.keySet().stream().min(Comparator.naturalOrder()).orElse(Integer.MAX_VALUE);
        highestResultIndex = results.keySet().stream().max(Comparator.naturalOrder()).orElse(retentionFloor - 1);
    }
}
