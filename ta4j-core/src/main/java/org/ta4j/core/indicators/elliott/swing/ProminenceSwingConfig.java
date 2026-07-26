/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.swing;

/**
 * Configuration for causal price-prominence swing detection.
 *
 * <p>
 * Prominence measures how far a candidate peak or trough stands above or below
 * the surrounding local baselines. The search is bounded by
 * {@code lookbackBars}; {@code confirmationBars} controls how much right-side
 * evidence is required before a candidate is reported. Near the start of a
 * series, detection uses the available partial baseline once at least one bar
 * is present on each side of the candidate; {@code lookbackBars} is an upper
 * bound, not a required warm-up window.
 *
 * @param lookbackBars     maximum bars searched on either side for the local
 *                         baseline
 * @param confirmationBars required bars after the candidate
 * @param allowedEqualBars maximum additional equal bars in a plateau
 * @param atrPeriod        ATR period used by the default prominence threshold
 * @param minAtrProminence minimum prominence as an ATR multiple
 * @since 0.23.1
 */
public record ProminenceSwingConfig(int lookbackBars, int confirmationBars, int allowedEqualBars, int atrPeriod,
        double minAtrProminence) {

    private static final int DEFAULT_LOOKBACK_BARS = 20;
    private static final int DEFAULT_CONFIRMATION_BARS = 3;
    private static final int DEFAULT_ALLOWED_EQUAL_BARS = 0;
    private static final int DEFAULT_ATR_PERIOD = 14;
    private static final double DEFAULT_MIN_ATR_PROMINENCE = 1.0;

    public ProminenceSwingConfig {
        if (lookbackBars < 1) {
            throw new IllegalArgumentException("lookbackBars must be positive");
        }
        if (confirmationBars < 1) {
            throw new IllegalArgumentException("confirmationBars must be positive");
        }
        if (allowedEqualBars < 0) {
            throw new IllegalArgumentException("allowedEqualBars must be non-negative");
        }
        if (atrPeriod < 1) {
            throw new IllegalArgumentException("atrPeriod must be positive");
        }
        if (!Double.isFinite(minAtrProminence) || minAtrProminence < 0.0) {
            throw new IllegalArgumentException("minAtrProminence must be finite and non-negative");
        }
    }

    /**
     * Returns the balanced default profile.
     *
     * @return 20-bar bounded search, three confirmation bars, and one-ATR minimum
     *         prominence
     * @since 0.23.1
     */
    public static ProminenceSwingConfig defaults() {
        return new ProminenceSwingConfig(DEFAULT_LOOKBACK_BARS, DEFAULT_CONFIRMATION_BARS, DEFAULT_ALLOWED_EQUAL_BARS,
                DEFAULT_ATR_PERIOD, DEFAULT_MIN_ATR_PROMINENCE);
    }
}
