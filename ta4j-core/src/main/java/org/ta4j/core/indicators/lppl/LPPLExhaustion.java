/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.lppl;

import java.util.List;

import org.ta4j.core.num.Num;

/**
 * Rich Log-Periodic Power Law (LPPL) exhaustion result returned by
 * {@link LPPLExhaustionIndicator}.
 *
 * @param status         aggregate status
 * @param side           dominant exhaustion side by actionable-fit count
 * @param score          bounded score in {@code [-1, 1]}; positive values are
 *                       crash exhaustion and negative values are bubble
 *                       exhaustion
 * @param fitQuality     weighted fit quality used to scale the score
 * @param dominantFit    strongest actionable fit; neutral results retain the
 *                       strongest converged diagnostic fit when available
 * @param fits           per-window fit attempts
 * @param attemptedFits  number of windows attempted
 * @param actionableFits number of fits that pass the configured LPPL filters
 * @param crashFits      actionable crash-exhaustion fits
 * @param bubbleFits     actionable bubble-exhaustion fits
 * @since 0.22.9
 */
public record LPPLExhaustion(LPPLExhaustionStatus status, LPPLExhaustionSide side, Num score, Num fitQuality,
        LPPLFit dominantFit, List<LPPLFit> fits, int attemptedFits, int actionableFits, int crashFits, int bubbleFits) {

    /**
     * Creates a validated immutable exhaustion result.
     *
     * @since 0.22.9
     */
    public LPPLExhaustion {
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
        if (attemptedFits != fits.size()) {
            throw new IllegalArgumentException("attemptedFits must equal fits.size()");
        }
        if (actionableFits < 0 || crashFits < 0 || bubbleFits < 0) {
            throw new IllegalArgumentException("fit counts must be non-negative");
        }
        if (actionableFits > attemptedFits) {
            throw new IllegalArgumentException("actionableFits must not exceed attemptedFits");
        }
        if (crashFits + bubbleFits != actionableFits) {
            throw new IllegalArgumentException("crashFits + bubbleFits must equal actionableFits");
        }
        if (!Num.isFinite(score) || score.doubleValue() < -1.0 || score.doubleValue() > 1.0) {
            throw new IllegalArgumentException("score must be finite and between -1 and 1");
        }
        if ((side == LPPLExhaustionSide.NONE && !score.isZero())
                || (side == LPPLExhaustionSide.CRASH_EXHAUSTION && !score.isPositive())
                || (side == LPPLExhaustionSide.BUBBLE_EXHAUSTION && !score.isNegative())) {
            throw new IllegalArgumentException("score direction must match side");
        }
        if ((side == LPPLExhaustionSide.NONE && crashFits != bubbleFits)
                || (side == LPPLExhaustionSide.CRASH_EXHAUSTION && crashFits <= bubbleFits)
                || (side == LPPLExhaustionSide.BUBBLE_EXHAUSTION && bubbleFits <= crashFits)) {
            throw new IllegalArgumentException("side must match the crashFits/bubbleFits majority");
        }
    }

    /**
     * @return {@code true} when at least one actionable LPPL fit supports the
     *         returned score
     * @since 0.22.9
     */
    public boolean isActionable() {
        return status == LPPLExhaustionStatus.VALID && side != LPPLExhaustionSide.NONE && actionableFits > 0;
    }
}
