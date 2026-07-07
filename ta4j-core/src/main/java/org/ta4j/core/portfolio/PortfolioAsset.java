/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.util.Objects;

/**
 * Stable identifier for one asset in a portfolio backtest.
 *
 * <p>
 * Portfolio APIs use this value as the map key for target weights, holdings,
 * prices, and result snapshots. The identifier is intentionally small and does
 * not encode exchange, currency, or instrument metadata so applications can map
 * their own naming scheme into ta4j without another registry layer.
 * </p>
 *
 * @param id non-blank user supplied asset id
 * @since 0.22.9
 */
public record PortfolioAsset(String id) implements Comparable<PortfolioAsset> {

    /**
     * Creates a portfolio asset id.
     *
     * @param id non-blank user supplied asset id
     * @since 0.22.9
     */
    public PortfolioAsset {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        id = id.trim();
    }

    /**
     * Factory method for readable portfolio setup code.
     *
     * @param id non-blank user supplied asset id
     * @return asset id
     * @since 0.22.9
     */
    public static PortfolioAsset of(String id) {
        return new PortfolioAsset(id);
    }

    @Override
    public int compareTo(PortfolioAsset other) {
        return id.compareTo(other.id());
    }

    @Override
    public String toString() {
        return id;
    }
}
