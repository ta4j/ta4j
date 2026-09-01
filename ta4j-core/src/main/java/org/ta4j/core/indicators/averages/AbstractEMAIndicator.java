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
 * value to allow graceful recovery.
 */
public abstract class AbstractEMAIndicator extends RecursiveCachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;
    private final transient Num multiplier;

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
        if (index < 0) {
            return super.getValue(index);
        }

        BarSeries series = getBarSeries();
        while (true) {
            int removedBarsCount = series.getRemovedBarsCount();
            if (index >= removedBarsCount) {
                return super.getValue(index);
            }

            int beginIndex = series.getBeginIndex();
            long firstStableIndex = (long) beginIndex + getCountOfUnstableBars();
            if (firstStableIndex > series.getEndIndex()) {
                if (series.getRemovedBarsCount() == removedBarsCount && firstStableIndex > series.getEndIndex()) {
                    return NaN;
                }
                continue;
            }

            // Do not use CachedIndicator's synthetic first-bar cache: this
            // value depends on a later stable EMA index, whose changes need not
            // advance the retained head.
            Num value = super.getValue((int) firstStableIndex);
            if (series.getRemovedBarsCount() == removedBarsCount) {
                return value;
            }
            // A prune raced the cached read, so resolve the first stable index
            // again against the retained head that remains.
        }
    }

    @Override
    protected Num calculate(int index) {
        int beginIndex = getBarSeries().getBeginIndex();
        long firstStableIndex = (long) beginIndex + getCountOfUnstableBars();
        // CachedIndicator can reach calculate(0) when a prune races the
        // normalized getValue path. Keep that synthetic fallback safe.
        if (index < beginIndex) {
            index = (int) Math.min(firstStableIndex, getBarSeries().getEndIndex());
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
