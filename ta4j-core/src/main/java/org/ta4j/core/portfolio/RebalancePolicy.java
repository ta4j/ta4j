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
 * The MVP policies are deliberately index based. Calendar-aware policies can be
 * implemented by callers using the aligned end time available through
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
     * @param series           aligned portfolio series
     * @param index            aligned portfolio index
     * @param previousSnapshot previous snapshot, or {@code null} at the first bar
     * @return true if the executor should rebalance
     * @since 0.22.9
     */
    boolean shouldRebalance(AlignedPortfolioSeries series, int index, PortfolioSnapshot previousSnapshot);

    /**
     * Rebalances only on the first aligned bar.
     *
     * @return first-bar policy
     * @since 0.22.9
     */
    static RebalancePolicy atStart() {
        return (series, index, previousSnapshot) -> index == 0;
    }

    /**
     * Rebalances on every aligned bar.
     *
     * @return every-bar policy
     * @since 0.22.9
     */
    static RebalancePolicy everyBar() {
        return (series, index, previousSnapshot) -> true;
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
        return (series, index, previousSnapshot) -> rebalanceIndexes.contains(index);
    }
}
