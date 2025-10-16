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

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

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

    private static final int RECURSION_THRESHOLD = 100;

    /** List of cached results. */
    private final List<T> results;

    /** Guards against recursively re-entering prefill for the same indicator. */
    private static final ThreadLocal<Map<CachedIndicator<?>, Integer>> PREFILL_DEPTH = ThreadLocal
            .withInitial(IdentityHashMap::new);

    /**
     * Should always be the index of the last (calculated) result in the results
     * list.
     */
    protected int highestResultIndex = -1;

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    protected CachedIndicator(BarSeries series) {
        super(series);
        int limit = series.getMaximumBarCount();
        this.results = limit == Integer.MAX_VALUE ? new ArrayList<>() : new ArrayList<>(limit);
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
            T result = calculate(index);
            if (log.isTraceEnabled()) {
                log.trace("{}({}): {}", this, index, result);
            }
            return result;
        }

        final int removedBarsCount = series.getRemovedBarsCount();
        final int maximumResultCount = series.getMaximumBarCount();
        int normalizedIndex = Math.max(index, 0);

        if (normalizedIndex >= removedBarsCount) {
            prefillIntermediateResults(normalizedIndex, removedBarsCount, maximumResultCount);
        }

        T result;
        if (normalizedIndex < removedBarsCount) {
            if (log.isTraceEnabled()) {
                log.trace("{}: result from bar {} already removed from cache, use {}-th instead",
                        getClass().getSimpleName(), normalizedIndex, removedBarsCount);
            }
            increaseLengthTo(removedBarsCount, maximumResultCount);
            highestResultIndex = removedBarsCount;
            if (results.isEmpty()) {
                results.add(null);
            }
            result = results.get(0);
            if (result == null) {
                result = calculate(0);
                results.set(0, result);
            }
        } else if (normalizedIndex == series.getEndIndex()) {
            result = calculate(normalizedIndex);
        } else {
            result = cacheValue(normalizedIndex, maximumResultCount);
        }

        if (log.isTraceEnabled()) {
            log.trace("{}({}): {}", this, index, result);
        }
        return result;
    }

    private void prefillIntermediateResults(int targetIndex, int removedBarsCount, int maximumResultCount) {
        if (targetIndex <= 0) {
            return;
        }

        BarSeries series = getBarSeries();
        if (series == null) {
            return;
        }

        final int seriesEndIndex = series.getEndIndex();
        if (targetIndex > seriesEndIndex) {
            return;
        }

        Map<CachedIndicator<?>, Integer> prefillDepthByIndicator = PREFILL_DEPTH.get();
        Integer depth = prefillDepthByIndicator.get(this);
        if (depth != null && depth > 0) {
            return;
        }

        int startIndex = Math.max(removedBarsCount, highestResultIndex);
        if (startIndex < 0) {
            startIndex = Math.max(0, removedBarsCount);
        }

        if (targetIndex - startIndex <= RECURSION_THRESHOLD) {
            return;
        }

        prefillDepthByIndicator.put(this, (depth == null ? 0 : depth) + 1);
        try {
            int upperBound = Math.min(targetIndex, seriesEndIndex + 1);
            for (int prefIndex = startIndex; prefIndex < upperBound; prefIndex++) {
                if (prefIndex < removedBarsCount) {
                    continue;
                }
                if (prefIndex == seriesEndIndex) {
                    continue;
                }
                cacheValue(prefIndex, maximumResultCount);
            }
        } finally {
            int updatedDepth = prefillDepthByIndicator.getOrDefault(this, 1) - 1;
            if (updatedDepth <= 0) {
                prefillDepthByIndicator.remove(this);
            } else {
                prefillDepthByIndicator.put(this, updatedDepth);
            }
            if (prefillDepthByIndicator.isEmpty()) {
                PREFILL_DEPTH.remove();
            }
        }
    }

    private T cacheValue(int index, int maximumResultCount) {
        increaseLengthTo(index, maximumResultCount);
        if (index > highestResultIndex) {
            highestResultIndex = index;
            T result = calculate(index);
            if (results.isEmpty()) {
                results.add(result);
            } else {
                results.set(results.size() - 1, result);
            }
            return result;
        }

        int resultInnerIndex = results.size() - 1 - (highestResultIndex - index);
        if (resultInnerIndex < 0 || resultInnerIndex >= results.size()) {
            // Index outside cached window; fall back to earliest cached slot.
            resultInnerIndex = 0;
            index = highestResultIndex - results.size() + 1;
        }
        T result = results.get(resultInnerIndex);
        if (result == null) {
            result = calculate(index);
            results.set(resultInnerIndex, result);
        }
        return result;
    }

    private void increaseLengthTo(int index, int maxLength) {
        if (highestResultIndex > -1) {
            int newResultsCount = Math.min(index - highestResultIndex, maxLength);
            if (newResultsCount == maxLength) {
                results.clear();
                results.addAll(Collections.nCopies(maxLength, null));
            } else if (newResultsCount > 0) {
                results.addAll(Collections.nCopies(newResultsCount, null));
                removeExceedingResults(maxLength);
            }
        } else {
            results.addAll(Collections.nCopies(Math.min(index + 1, maxLength), null));
        }
    }

    private void removeExceedingResults(int maximumResultCount) {
        int resultCount = results.size();
        if (resultCount > maximumResultCount) {
            int nbResultsToRemove = resultCount - maximumResultCount;
            if (nbResultsToRemove == 1) {
                results.remove(0);
            } else {
                results.subList(0, nbResultsToRemove).clear();
            }
        }
    }
}
