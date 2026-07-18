/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.swing;

/**
 * Configuration for causal rolling-slope swing detection.
 *
 * @param window           bars in each regression window
 * @param confirmationBars consecutive post-turn windows that must keep the new
 *                         slope direction
 * @param atrPeriod        ATR period used for magnitude filtering
 * @param minSlopeChange   minimum absolute change between pre/post slopes in
 *                         price units per bar
 * @param minAtrReversal   minimum pivot displacement as an ATR multiple
 * @since 0.22.9
 */
public record SlopeChangeConfig(int window, int confirmationBars, int atrPeriod, double minSlopeChange,
        double minAtrReversal) {

    private static final int DEFAULT_CONFIRMATION_BARS = 2;
    private static final int DEFAULT_ATR_PERIOD = 14;
    private static final double DEFAULT_MIN_SLOPE_CHANGE = 0.0;
    private static final double DEFAULT_MIN_ATR_REVERSAL = 0.5;

    public SlopeChangeConfig {
        if (window < 2) {
            throw new IllegalArgumentException("window must be at least 2");
        }
        if (confirmationBars < 1) {
            throw new IllegalArgumentException("confirmationBars must be positive");
        }
        if (atrPeriod < 1) {
            throw new IllegalArgumentException("atrPeriod must be positive");
        }
        if (!Double.isFinite(minSlopeChange) || minSlopeChange < 0.0) {
            throw new IllegalArgumentException("minSlopeChange must be finite and non-negative");
        }
        if (!Double.isFinite(minAtrReversal) || minAtrReversal < 0.0) {
            throw new IllegalArgumentException("minAtrReversal must be finite and non-negative");
        }
    }

    /**
     * Creates the balanced default profile for the supplied regression window. The
     * profile requires two confirming windows and a half-ATR pivot reversal while
     * leaving the absolute slope-change filter disabled.
     *
     * @param window bars in each regression window
     * @return balanced slope-change configuration
     * @since 0.22.9
     */
    public static SlopeChangeConfig defaults(final int window) {
        return new SlopeChangeConfig(window, DEFAULT_CONFIRMATION_BARS, DEFAULT_ATR_PERIOD, DEFAULT_MIN_SLOPE_CHANGE,
                DEFAULT_MIN_ATR_REVERSAL);
    }
}
