/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.util.Map;
import java.util.Objects;

import org.ta4j.core.serialization.ComponentDescriptor;

/**
 * Entry guard satisfied while open positions plus accepted pending entries are
 * fewer than the configured maximum. It should normally be applied to entry,
 * not exit.
 *
 * @since 0.27.1
 */
public final class MaxOpenPositionsRule implements PortfolioRule {
    private final int maximum;

    /**
     * Creates a position limit.
     *
     * @param maximum maximum open positions; zero disables all entries
     * @throws IllegalArgumentException if {@code maximum} is negative
     * @since 0.27.1
     */
    public MaxOpenPositionsRule(int maximum) {
        if (maximum < 0) {
            throw new IllegalArgumentException("maximum must not be negative");
        }
        this.maximum = maximum;
    }

    /**
     * {@inheritDoc}
     * 
     * @since 0.27.1
     */
    @Override
    public boolean isSatisfied(String asset, int index, PortfolioTradingRecord record) {
        Objects.requireNonNull(record, "record");
        return record.openPositionCount() + record.pendingEntryCount() < maximum;
    }

    /**
     * {@inheritDoc}
     * 
     * @since 0.27.1
     */
    @Override
    public ComponentDescriptor toDescriptor() {
        return ComponentDescriptor.builder()
                .withType(MaxOpenPositionsRule.class.getName())
                .withLabel("MaxOpenPositionsRule")
                .withParameters(Map.of("maximum", maximum))
                .build();
    }
}
