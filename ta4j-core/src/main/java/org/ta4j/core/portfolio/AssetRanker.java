/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.util.List;

import org.ta4j.core.serialization.ComponentDescriptor;

/**
 * Ranks eligible assets at an aligned portfolio index, best first.
 *
 * @since 0.27.1
 */
@FunctionalInterface
public interface AssetRanker {

    /**
     * Ranks assets at a portfolio time. Implementations define eligibility and
     * ordering and should return each eligible asset at most once.
     *
     * @param view  aligned portfolio series view
     * @param index portfolio timeline index
     * @return assets in descending preference order
     * @since 0.27.1
     */
    List<String> rank(PortfolioSeriesView view, int index);

    /**
     * Returns the descriptor of a reconstructible ranker.
     *
     * @return ranker descriptor
     * @throws UnsupportedOperationException if this ranker is opaque
     * @since 0.27.1
     */
    default ComponentDescriptor toDescriptor() {
        throw new UnsupportedOperationException("Asset ranker is not descriptor-backed: " + getClass().getName());
    }
}
