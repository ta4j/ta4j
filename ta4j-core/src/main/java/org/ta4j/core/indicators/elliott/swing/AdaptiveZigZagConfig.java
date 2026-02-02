/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.swing;

/**
 * Configuration for adaptive ZigZag swing detection.
 *
 * @param atrPeriod       ATR lookback period
 * @param atrMultiplier   multiplier applied to ATR values
 * @param minThreshold    minimum reversal threshold (price units, 0 to disable)
 * @param maxThreshold    maximum reversal threshold (price units, 0 to disable)
 * @param smoothingPeriod smoothing window for ATR values (1 for none)
 * @since 0.22.2
 */
public record AdaptiveZigZagConfig(int atrPeriod, double atrMultiplier, double minThreshold, double maxThreshold,
        int smoothingPeriod) {

    public AdaptiveZigZagConfig {
        if (atrPeriod < 1) {
            throw new IllegalArgumentException("atrPeriod must be positive");
        }
        if (atrMultiplier <= 0.0) {
            throw new IllegalArgumentException("atrMultiplier must be positive");
        }
        if (minThreshold < 0.0) {
            throw new IllegalArgumentException("minThreshold must be non-negative");
        }
        if (maxThreshold < 0.0) {
            throw new IllegalArgumentException("maxThreshold must be non-negative");
        }
        if (maxThreshold > 0.0 && minThreshold > maxThreshold) {
            throw new IllegalArgumentException("minThreshold cannot exceed maxThreshold");
        }
        if (smoothingPeriod < 1) {
            throw new IllegalArgumentException("smoothingPeriod must be at least 1");
        }
    }

    /**
     * @return true when a minimum clamp is enabled
     * @since 0.22.2
     */
    public boolean hasMinClamp() {
        return minThreshold > 0.0;
    }

    /**
     * @return true when a maximum clamp is enabled
     * @since 0.22.2
     */
    public boolean hasMaxClamp() {
        return maxThreshold > 0.0;
    }
}
