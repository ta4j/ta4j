/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Momentum lifecycle classification for MACD-V values.
 *
 * <p>
 * The default thresholds are:
 * <ul>
 * <li>{@code macdV > +150}: {@link #HIGH_RISK}</li>
 * <li>{@code +50 <= macdV <= +150}: {@link #RALLYING_OR_RETRACING}</li>
 * <li>{@code -50 <= macdV <= +50}: {@link #RANGING}</li>
 * <li>{@code -150 <= macdV < -50}: {@link #REBOUNDING_OR_REVERSING}</li>
 * <li>{@code macdV < -150}: {@link #LOW_RISK}</li>
 * </ul>
 *
 * <p>
 * Undefined values (for example {@code NaN}) are classified as
 * {@link #UNDEFINED}.
 *
 * @since 0.22.2
 */
public enum MACDVMomentumState {

    /** Undefined momentum state (for example warm-up or NaN values). */
    UNDEFINED,
    /** Positive-extreme momentum zone. */
    HIGH_RISK,
    /** Positive momentum zone above range but below extreme risk. */
    RALLYING_OR_RETRACING,
    /** Neutral momentum zone centered around zero. */
    RANGING,
    /** Negative momentum zone below range but above extreme risk. */
    REBOUNDING_OR_REVERSING,
    /** Negative-extreme momentum zone. */
    LOW_RISK;

    /**
     * Classifies a MACD-V value using default lifecycle thresholds.
     *
     * @param macdV      MACD-V value
     * @param numFactory numeric factory used to create threshold values
     * @return momentum state for the provided value
     *
     *         Returns {@link #UNDEFINED} when {@code macdV} or {@code numFactory}
     *         is null/NaN.
     * @since 0.22.2
     */
    public static MACDVMomentumState fromMacdV(Num macdV, NumFactory numFactory) {
        if (Num.isNaNOrNull(macdV) || numFactory == null) {
            return UNDEFINED;
        }
        MACDVMomentumProfile defaultProfile = MACDVMomentumProfile.defaultProfile();
        Num positiveRangeThreshold = numFactory.numOf(defaultProfile.positiveRangeThreshold());
        Num positiveRiskThreshold = numFactory.numOf(defaultProfile.positiveRiskThreshold());
        Num negativeRangeThreshold = numFactory.numOf(defaultProfile.negativeRangeThreshold());
        Num negativeRiskThreshold = numFactory.numOf(defaultProfile.negativeRiskThreshold());

        if (macdV.isGreaterThan(positiveRiskThreshold)) {
            return HIGH_RISK;
        }
        if (macdV.isGreaterThanOrEqual(positiveRangeThreshold)) {
            return RALLYING_OR_RETRACING;
        }
        if (macdV.isGreaterThanOrEqual(negativeRangeThreshold)) {
            return RANGING;
        }
        if (macdV.isGreaterThanOrEqual(negativeRiskThreshold)) {
            return REBOUNDING_OR_REVERSING;
        }
        return LOW_RISK;
    }

    /**
     * Classifies a MACD-V value using a custom momentum profile.
     *
     * @param macdV           MACD-V value
     * @param momentumProfile momentum thresholds profile
     * @return momentum state for the provided value
     * @since 0.22.2
     */
    public static MACDVMomentumState fromMacdV(Num macdV, MACDVMomentumProfile momentumProfile) {
        if (momentumProfile == null) {
            return UNDEFINED;
        }
        return momentumProfile.classify(macdV);
    }
}
