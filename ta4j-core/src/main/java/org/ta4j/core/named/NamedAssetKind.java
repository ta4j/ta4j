/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.named;

/**
 * Describes the ta4j component family that owns a named shorthand alias.
 * <p>
 * Alias namespaces are intentionally kind-specific. For example, {@code SMA(7)}
 * can name a single indicator while {@code SMA(7,21)} can name a strategy macro
 * without forcing indicators to support multi-output values.
 *
 * @since 0.22.7
 */
public enum NamedAssetKind {

    /** Indicator shorthand, such as {@code SMA(7)}. */
    INDICATOR,

    /** Rule shorthand, such as {@code CrossedUp(SMA(7),SMA(21))}. */
    RULE,

    /** Strategy shorthand, such as {@code SMA(7,21)}. */
    STRATEGY,

    /** Analysis criterion shorthand, such as {@code NetProfit}. */
    ANALYSIS_CRITERION
}
