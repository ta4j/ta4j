/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.pnl;

import org.ta4j.core.num.Num;

/**
 * Analysis criterion that finds the most profitable streak of consecutive
 * positions.
 *
 * <p>
 * The criterion sums profits across positive positions and returns the highest
 * value reached.
 * </p>
 *
 * @since 0.19
 */
public class MaxConsecutiveProfitCriterion extends AbstractConsecutivePnlCriterion {

    @Override
    protected boolean accepts(Num profit) {
        return profit.isPositive();
    }

    @Override
    protected boolean preferForStreak(Num candidate, Num best) {
        return candidate.isGreaterThan(best);
    }

    /**
     * Indicates whether the first profit streak is preferable to the second.
     *
     * @param a the first value to compare
     * @param b the second value to compare
     * @return {@code true} when the first value is higher (a larger gain)
     */
    @Override
    public boolean betterThan(Num a, Num b) {
        return a.isGreaterThan(b);
    }
}
