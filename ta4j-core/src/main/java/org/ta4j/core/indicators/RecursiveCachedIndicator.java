/*
 * SPDX-License-Identifier: MIT
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
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This class is thread-safe. Unlike previous versions, the
 * {@link #getValue(int)} method is no longer {@code synchronized}. Thread
 * safety is achieved through the underlying {@link CachedBuffer}'s locking
 * mechanism. Code that relied on external synchronization using indicator
 * instances must be updated.
 *
 * @param <T> cached result type
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
    private static final ThreadLocal<Map<Object, Integer>> PREFILL_DEPTH = ThreadLocal
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
     * Creates a deterministic recursive indicator that shares cache state with
     * equivalent instances.
     *
     * @param series   the bar series
     * @param identity the complete immutable constructor inputs
     * @since 0.23.1
     */
    protected RecursiveCachedIndicator(BarSeries series, IndicatorIdentity identity) {
        super(series, identity);
    }

    /**
     * Constructor.
     *
     * @param indicator the indicator (with its bar series)
     */
    protected RecursiveCachedIndicator(Indicator<?> indicator) {
        this(indicator.getBarSeries());
    }

    /**
     * Creates a deterministic recursive indicator that shares cache state with
     * equivalent instances.
     *
     * @param indicator a related indicator
     * @param identity  the complete immutable constructor inputs
     * @since 0.23.1
     */
    protected RecursiveCachedIndicator(Indicator<?> indicator, IndicatorIdentity identity) {
        super(indicator, identity);
    }

    @Override
    public T getValue(int index) {
        while (true) {
            try {
                BarSeries series = getBarSeries();
                if (series != null) {
                    final int seriesEndIndex = series.getEndIndex();
                    if (index <= seriesEndIndex) {
                        // We are not after the end of the series
                        final int removedBarsCount = series.getRemovedBarsCount();
                        int startIndex = Math.max(removedBarsCount, sharedHighestResultIndex());
                        if (startIndex < 0) {
                            startIndex = Math.max(0, removedBarsCount);
                        }
                        if (index - startIndex > RECURSION_THRESHOLD) {
                            prefillMissingValues(startIndex, index, series);
                        }
                    }
                }
                return super.getValue(index);
            } catch (HistoryEpochChangedException changed) {
                // A historical mutation raced with iterative prefill. Retry after the
                // shared state observes the new epoch.
                if (getCache().isWriteLockedByCurrentThread()) {
                    throw changed;
                }
            }
        }
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
    private void prefillMissingValues(int startIndex, int targetIndex, BarSeries series) {
        Map<Object, Integer> depthByIndicator = PREFILL_DEPTH.get();
        Object stateIdentity = sharedStateIdentity();
        Integer depth = depthByIndicator.get(stateIdentity);
        if (depth != null && depth > 0) {
            // Already in a prefill for this indicator on this thread; skip to avoid
            // infinite recursion
            return;
        }

        // Increment depth first, then wrap ALL subsequent operations in try-finally
        // to guarantee cleanup even if prefillUntil throws an exception
        int newDepth = (depth == null ? 0 : depth) + 1;
        depthByIndicator.put(stateIdentity, newDepth);
        try {
            // Use the cache's prefillUntil to compute values iteratively
            // under a single write lock
            long historyEpoch = series.getBarHistoryEpoch();
            getCache().prefillUntil(startIndex, targetIndex, this::calculate, this::updateHighestResultIndex, series,
                    historyEpoch);
        } finally {
            // Cleanup: decrement depth and remove if zero
            cleanupPrefillDepth(depthByIndicator, stateIdentity);
        }
    }

    /**
     * Cleans up the prefill depth tracking for this indicator on the current
     * thread. Removes the ThreadLocal value entirely when no indicators have active
     * prefills.
     */
    private void cleanupPrefillDepth(Map<Object, Integer> depthByIndicator, Object stateIdentity) {
        Integer currentDepth = depthByIndicator.get(stateIdentity);
        if (currentDepth == null || currentDepth <= 1) {
            depthByIndicator.remove(stateIdentity);
        } else {
            depthByIndicator.put(stateIdentity, currentDepth - 1);
        }

        // Clean up ThreadLocal entirely when no indicators have active prefills
        // to prevent memory leaks in long-lived threads
        if (depthByIndicator.isEmpty()) {
            PREFILL_DEPTH.remove();
        }
    }
}
