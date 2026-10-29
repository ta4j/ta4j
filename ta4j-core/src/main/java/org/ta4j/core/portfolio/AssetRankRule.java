/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.serialization.ComponentDescriptor;

/**
 * Selects the first N assets returned by an asset ranker. An asset must have
 * an exact bar at the aligned portfolio time to be selected.
 *
 * @since 0.27.1
 */
public final class AssetRankRule implements PortfolioRule {
    private final AssetRanker ranker;
    private final int topCount;

    /**
     * Creates a top-N selection rule.
     *
     * @param ranker function returning assets in preference order
     * @param topCount maximum number of selected assets, at least one
     * @throws IllegalArgumentException if {@code topCount} is not positive
     * @since 0.27.1
     */
    public AssetRankRule(AssetRanker ranker, int topCount) {
        this.ranker = Objects.requireNonNull(ranker, "ranker");
        if (topCount <= 0) {
            throw new IllegalArgumentException("topCount must be positive");
        }
        this.topCount = topCount;
    }

    /**
     * {@inheritDoc}
     * @since 0.27.1
     */
    @Override
    public boolean isSatisfied(String asset, int index, PortfolioTradingRecord record) {
        PortfolioSeriesView view = Objects.requireNonNull(record, "record").series();
        if (!view.hasBar(asset, index)) {
            return false;
        }
        List<String> ranked = ranker.rank(view, index);
        for (int position = 0; position < Math.min(topCount, ranked.size()); position++) {
            if (asset.equals(ranked.get(position))) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     * @since 0.27.1
     */
    @Override
    public ComponentDescriptor toDescriptor() {
        return ComponentDescriptor.builder().withType(AssetRankRule.class.getName()).withLabel("AssetRankRule")
                .withParameters(Map.of("topCount", topCount)).addComponent(ranker.toDescriptor()).build();
    }
}
