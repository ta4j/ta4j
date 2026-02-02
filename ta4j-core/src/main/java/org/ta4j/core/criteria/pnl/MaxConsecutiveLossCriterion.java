/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.pnl;

import org.ta4j.core.num.Num;

/**
 * Analysis criterion that finds the largest string of consecutive losing
 * positions.
 *
 * <p>
 * The criterion sums the losses of each losing streak and returns the lowest
 * value.
 * </p>
 *
 * @since 0.19
 */
public class MaxConsecutiveLossCriterion extends AbstractConsecutivePnlCriterion {

    @Override
    protected boolean accepts(Num profit) {
        return profit.isNegative();
    }

    @Override
    protected boolean preferForStreak(Num candidate, Num best) {
        return candidate.isLessThan(best);
    }

    /**
     * Indicates whether the first loss streak is preferable to the second.
     *
     * @param a the first value to compare
     * @param b the second value to compare
     * @return {@code true} when the first value is higher (a smaller loss)
     */
    @Override
    public boolean betterThan(Num a, Num b) {
        return a.isGreaterThan(b);
    }
}
