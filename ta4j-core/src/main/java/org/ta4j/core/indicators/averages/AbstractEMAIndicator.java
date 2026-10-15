/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

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
 *
 * <p>
 * On a bounded (moving) series the recurrence continues across head advances:
 * retained cached values are full-history values, and each appended bar extends
 * the chain from its cached predecessor, so results match an unbounded series
 * at a per-bar cost independent of the maximum bar count. The recursion is
 * re-seeded at the retained head only when no cached predecessor exists (for
 * example a fresh indicator, or a read after the cache was invalidated); in
 * that case the declared unstable bars after that head are warm-up values
 * ({@code NaN}), exactly as on an unbounded series starting there. Warm-up is
 * counted from where the chain was seeded, so a continued chain keeps its
 * full-history warm-up even when the window is shorter than the warm-up.
 */
public abstract class AbstractEMAIndicator extends RecursiveCachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;
    private final transient Num multiplier;
    /**
     * First index of the live recurrence: the retained head at which the chain was
     * last seeded because no cached predecessor existed. Warm-up is measured from
     * here, so a chain continued across head advances keeps full-history warm-up.
     */
    private volatile transient int chainStartIndex = getBarSeries().getBeginIndex();

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
    protected Num calculate(int index) {
        int beginIndex = getBarSeries().getBeginIndex();
        if (beginIndex < 0) {
            // Empty series: there is no bar to seed from or recurse to.
            return NaN;
        }
        // CachedIndicator maps unavailable history to calculate(0). Serve the
        // retained head's seed value without re-seeding the live chain, which may
        // have started before the head.
        boolean mappedFromRemovedHistory = index < beginIndex;
        if (mappedFromRemovedHistory) {
            index = Math.min(beginIndex, getBarSeries().getEndIndex());
        }

        if (index == beginIndex) {
            // No retained predecessor: the chain is (re)seeded at the retained head.
            if (!mappedFromRemovedHistory) {
                chainStartIndex = beginIndex;
            }
            if (getCountOfUnstableBars() > 0) {
                return NaN;
            }
            Num current = indicator.getValue(index);
            // Seed the recursion through the initialValue extension point instead
            // of chasing getValue(index - 1) into removed history or self-recursion.
            return Num.isFinite(current) ? initialValue(index, current) : NaN;
        }

        // Resolve the predecessor first: when it is not cached the recursion walks
        // back to the retained head and re-seeds there, which updates the chain
        // start used by the warm-up check below.
        Num prevValue = getValue(index - 1);
        if (index < (long) chainStartIndex + getCountOfUnstableBars()) {
            // Warm-up is counted from where the chain was seeded, not from the
            // current head: a chain carried across head advances keeps its
            // full-history warm-up even when the window is shorter than it.
            return NaN;
        }

        Num current = indicator.getValue(index);

        // Check for NaN in current value
        if (!Num.isFinite(current)) {
            return NaN;
        }

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
