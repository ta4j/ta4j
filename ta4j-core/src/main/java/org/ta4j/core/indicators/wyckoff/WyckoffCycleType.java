/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.wyckoff;

/**
 * Enumerates the canonical Wyckoff market cycles.
 *
 * @since 0.22.3
 */
public enum WyckoffCycleType {

    /**
     * Represents the accumulation cycle where smart money builds positions quietly
     * before a markup.
     */
    ACCUMULATION,

    /**
     * Represents the distribution cycle where smart money unwinds positions ahead
     * of a markdown.
     */
    DISTRIBUTION,

    /**
     * Represents an unknown or indeterminate cycle when insufficient evidence is
     * available.
     */
    UNKNOWN
}
