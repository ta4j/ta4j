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

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Recursive cached {@link Indicator indicator}.
 *
 * <p>
 * Recursive indicators should extend this class.
 *
 * <p>
 * This class prevents StackOverflowError that may be thrown on the first
 * getValue(int) call of a recursive indicator. When an index value is asked and
 * the last cached value is too old/far, the computation of all the values
 * between the last cached and the asked one is executed iteratively using the
 * {@link CachedBuffer#prefillUntil} method.
 */
public abstract class RecursiveCachedIndicator<T> extends CachedIndicator<T> {

    /**
     * The recursion threshold for which an iterative calculation is executed.
     * <p>
     * This threshold determines when to switch from recursive to iterative
     * prefilling to avoid stack overflow.
     */
    private static final int RECURSION_THRESHOLD = 100;

    /**
     * Guards against recursively re-entering prefill for the same indicator.
     */
    private static final ThreadLocal<Map<RecursiveCachedIndicator<?>, Integer>> PREFILL_DEPTH = ThreadLocal
            .withInitial(IdentityHashMap::new);

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    protected RecursiveCachedIndicator(BarSeries series) {
        super(series);
    }

    /**
     * Constructor.
     *
     * @param indicator the indicator (with its bar series)
     */
    protected RecursiveCachedIndicator(Indicator<?> indicator) {
        this(indicator.getBarSeries());
    }

    @Override
    public T getValue(int index) {
        BarSeries series = getBarSeries();
        if (series != null) {
            final int seriesEndIndex = series.getEndIndex();
            if (index <= seriesEndIndex) {
                // We are not after the end of the series
                final int removedBarsCount = series.getRemovedBarsCount();
                int startIndex = Math.max(removedBarsCount, highestResultIndex);
                if (startIndex < 0) {
                    startIndex = Math.max(0, removedBarsCount);
                }
                if (index - startIndex > RECURSION_THRESHOLD) {
                    prefillMissingValues(startIndex, index);
                }
            }
        }

        return super.getValue(index);
    }

    /**
     * Iteratively prefills missing values to avoid stack overflow.
     *
     * <p>
     * Uses the {@link CachedBuffer#prefillUntil} method to compute values
     * iteratively under a single write lock, avoiding the overhead of re-entering
     * locks and series lookups for each index.
     *
     * @param startIndex  the index to start filling from
     * @param targetIndex the target index (exclusive)
     */
    private void prefillMissingValues(int startIndex, int targetIndex) {
        Map<RecursiveCachedIndicator<?>, Integer> depthByIndicator = PREFILL_DEPTH.get();
        Integer depth = depthByIndicator.get(this);
        if (depth != null && depth > 0) {
            return;
        }

        depthByIndicator.put(this, (depth == null ? 0 : depth) + 1);
        try {
            // Use the cache's prefillUntil to compute values iteratively
            // under a single write lock
            getCache().prefillUntil(startIndex, targetIndex, i -> {
                T value = calculate(i);
                if (i > highestResultIndex) {
                    highestResultIndex = i;
                }
                return value;
            });
            // Synchronize highestResultIndex from cache after prefillUntil completes
            // to ensure consistency (cache is source of truth)
            highestResultIndex = getCache().getHighestResultIndex();
        } finally {
            int updatedDepth = depthByIndicator.getOrDefault(this, 1) - 1;
            if (updatedDepth <= 0) {
                depthByIndicator.remove(this);
            } else {
                depthByIndicator.put(this, updatedDepth);
            }
            if (depthByIndicator.isEmpty()) {
                PREFILL_DEPTH.remove();
            }
        }
    }
}
