/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.lppl;

import java.util.List;

import org.ta4j.core.num.Num;

/**
 * Rich LPPL exhaustion result returned by {@link LpplExhaustionIndicator}.
 *
 * @param status        aggregate status
 * @param side          dominant exhaustion side
 * @param score         bounded score in {@code [-1, 1]}; positive values are
 *                      crash exhaustion and negative values are bubble
 *                      exhaustion
 * @param fitQuality    weighted fit quality used to scale the score
 * @param dominantFit   strongest actionable fit, or an invalid fit when none
 *                      exists
 * @param fits          per-window fit attempts
 * @param attemptedFits number of windows attempted
 * @param validFits     number of actionable fits
 * @param crashFits     actionable crash-exhaustion fits
 * @param bubbleFits    actionable bubble-exhaustion fits
 * @since 0.22.7
 */
public record LpplExhaustion(LpplExhaustionStatus status, LpplExhaustionSide side, Num score, Num fitQuality,
        LpplFit dominantFit, List<LpplFit> fits, int attemptedFits, int validFits, int crashFits, int bubbleFits) {

    /**
     * Creates a validated immutable exhaustion result.
     *
     * @since 0.22.7
     */
    public LpplExhaustion {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (side == null) {
            throw new IllegalArgumentException("side must not be null");
        }
        if (score == null) {
            throw new IllegalArgumentException("score must not be null");
        }
        if (fitQuality == null) {
            throw new IllegalArgumentException("fitQuality must not be null");
        }
        if (dominantFit == null) {
            throw new IllegalArgumentException("dominantFit must not be null");
        }
        fits = fits == null ? List.of() : List.copyOf(fits);
        if (attemptedFits < 0 || validFits < 0 || crashFits < 0 || bubbleFits < 0) {
            throw new IllegalArgumentException("fit counts must be non-negative");
        }
        if (validFits > attemptedFits) {
            throw new IllegalArgumentException("validFits must not exceed attemptedFits");
        }
        if (crashFits + bubbleFits != validFits) {
            throw new IllegalArgumentException("crashFits + bubbleFits must equal validFits");
        }
    }

    /**
     * @return {@code true} when at least one actionable LPPL fit supports the
     *         returned score
     * @since 0.22.7
     */
    public boolean isValid() {
        return status == LpplExhaustionStatus.VALID && side != LpplExhaustionSide.NONE && validFits > 0;
    }
}
