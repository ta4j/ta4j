/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Base class for Exponential Moving Average implementations.
 *
 * <p>
 * Provides robust NaN handling to prevent contamination of future values when
 * invalid data is encountered. When a NaN value is detected, it is returned
 * immediately. If a previous value is NaN, the indicator resets to the current
 * value to allow graceful recovery. When a bounded series removes its prefix,
 * the retained recursive cache reanchors at the new head.
 */
public abstract class AbstractEMAIndicator extends RecursiveCachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;
    private final transient Num multiplier;
    private volatile transient int observedRemovedBarsCount = getBarSeries().getRemovedBarsCount();

    /**
     * Constructor.
     *
     * @param indicator  the {@link Indicator}
     * @param barCount   the time frame
     * @param multiplier the multiplier
     */
    protected AbstractEMAIndicator(Indicator<Num> indicator, int barCount, double multiplier) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
        this.multiplier = getBarSeries().numFactory().numOf(multiplier);
    }

    @Override
    public Num getValue(int index) {
        BarSeries series = getBarSeries();
        while (true) {
            int removedBarsCount = series.getRemovedBarsCount();
            if (removedBarsCount != observedRemovedBarsCount) {
                resetForRetainedHead(removedBarsCount);
            }
            Num value = super.getValue(index);
            if (series.getRemovedBarsCount() == removedBarsCount) {
                return value;
            }
        }
    }

    private synchronized void resetForRetainedHead(int removedBarsCount) {
        if (removedBarsCount != observedRemovedBarsCount && !isCacheWriteLockedByCurrentThread()) {
            // A recursive EMA's retained values depend on its discarded prefix.
            // Invalidate first, then publish the count so a concurrent reader
            // never observes a new head with an old recurrence cache. When the
            // cache write lock is held by this thread the call is recursive
            // from an in-flight calculate(), so defer to the top-level read.
            invalidateCache();
            observedRemovedBarsCount = removedBarsCount;
        }
    }

    @Override
    protected Num calculate(int index) {
        int beginIndex = getBarSeries().getBeginIndex();
        long firstStableIndex = (long) beginIndex + getCountOfUnstableBars();
        // CachedIndicator maps unavailable history to calculate(0). Preserve the
        // retained head's warm-up value instead of looking ahead to a stable EMA.
        if (index < beginIndex) {
            index = Math.min(beginIndex, getBarSeries().getEndIndex());
        }
        if (index < firstStableIndex) {
            return NaN;
        }

        Num current = indicator.getValue(index);

        // Check for NaN in current value
        if (!Num.isFinite(current)) {
            return NaN;
        }

        // No previous value exists at the first addressable bar (unstableBars == 0);
        // seed the recursion through the initialValue extension point instead of
        // chasing getValue(index - 1) into removed history or self-recursion.
        if (index == beginIndex) {
            return initialValue(index, current);
        }

        // Get previous value and check for NaN
        Num prevValue = getValue(index - 1);
        if (!Num.isFinite(prevValue)) {
            // Graceful recovery: reset to current value when previous is NaN
            // This prevents contamination of future values
            return initialValue(index, current);
        }

        // Standard EMA calculation
        return prevValue.plus(current.minus(prevValue).multipliedBy(multiplier));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }

    public int getBarCount() {
        return barCount;
    }

    /**
     * Returns the first value used after warm-up or after a NaN reset.
     *
     * @param index   current index
     * @param current current source value
     * @return initial EMA value
     * @since 0.22.9
     */
    protected Num initialValue(int index, Num current) {
        return current;
    }
}
