/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.util.Objects;
import java.util.Set;

/**
 * Decides which aligned portfolio bars should trigger a rebalance.
 *
 * <p>
 * The MVP policies are deliberately index based. Calendar-aware policies can
 * capture an {@link AlignedPortfolioSeries} and inspect
 * {@link AlignedPortfolioSeries#endTimes()}.
 * </p>
 *
 * @since 0.22.9
 */
@FunctionalInterface
public interface RebalancePolicy {

    /**
     * Returns {@code true} when the portfolio should be rebalanced at
     * {@code index}.
     *
     * @param index aligned portfolio index
     * @return true if the executor should rebalance
     * @since 0.22.9
     */
    boolean shouldRebalance(int index);

    /**
     * Rebalances only on the first aligned bar.
     *
     * @return first-bar policy
     * @since 0.22.9
     */
    static RebalancePolicy atStart() {
        return index -> index == 0;
    }

    /**
     * Rebalances on every aligned bar.
     *
     * @return every-bar policy
     * @since 0.22.9
     */
    static RebalancePolicy everyBar() {
        return index -> true;
    }

    /**
     * Rebalances on explicit aligned indexes.
     *
     * @param indexes indexes that trigger rebalancing
     * @return explicit-index policy
     * @since 0.22.9
     */
    static RebalancePolicy onIndexes(Set<Integer> indexes) {
        Objects.requireNonNull(indexes, "indexes");
        Set<Integer> rebalanceIndexes = Set.copyOf(indexes);
        return rebalanceIndexes::contains;
    }
}
