/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.util.Objects;

import org.ta4j.core.num.Num;

/**
 * Threshold profile for MACD-V momentum-state classification.
 *
 * <p>
 * The thresholds must satisfy:
 * <ul>
 * <li>{@code positiveRiskThreshold > positiveRangeThreshold >= 0}</li>
 * <li>{@code negativeRiskThreshold < negativeRangeThreshold <= 0}</li>
 * </ul>
 *
 * @param positiveRangeThreshold lower bound of positive momentum range
 * @param positiveRiskThreshold  lower bound of positive risk range
 * @param negativeRangeThreshold upper bound of negative momentum range
 * @param negativeRiskThreshold  upper bound of negative risk range
 * @since 0.22.3
 * @deprecated use {@link org.ta4j.core.indicators.macd.MACDVMomentumProfile}
 */
@Deprecated(since = "0.22.3", forRemoval = false)
public record MACDVMomentumProfile(Number positiveRangeThreshold, Number positiveRiskThreshold,
        Number negativeRangeThreshold, Number negativeRiskThreshold) {

    private static final double DEFAULT_POSITIVE_RANGE_THRESHOLD = 50D;
    private static final double DEFAULT_POSITIVE_RISK_THRESHOLD = 150D;
    private static final double DEFAULT_NEGATIVE_RANGE_THRESHOLD = -50D;
    private static final double DEFAULT_NEGATIVE_RISK_THRESHOLD = -150D;

    /**
     * Creates a momentum profile with default thresholds.
     *
     * @return default momentum profile ({@code +50/+150/-50/-150})
     * @since 0.22.3
     */
    public static MACDVMomentumProfile defaultProfile() {
        return new MACDVMomentumProfile(DEFAULT_POSITIVE_RANGE_THRESHOLD, DEFAULT_POSITIVE_RISK_THRESHOLD,
                DEFAULT_NEGATIVE_RANGE_THRESHOLD, DEFAULT_NEGATIVE_RISK_THRESHOLD);
    }

    /**
     * Canonical constructor.
     *
     * @param positiveRangeThreshold lower bound of positive momentum range
     * @param positiveRiskThreshold  lower bound of positive risk range
     * @param negativeRangeThreshold upper bound of negative momentum range
     * @param negativeRiskThreshold  upper bound of negative risk range
     * @since 0.22.3
     */
    public MACDVMomentumProfile {
        Objects.requireNonNull(positiveRangeThreshold, "positiveRangeThreshold");
        Objects.requireNonNull(positiveRiskThreshold, "positiveRiskThreshold");
        Objects.requireNonNull(negativeRangeThreshold, "negativeRangeThreshold");
        Objects.requireNonNull(negativeRiskThreshold, "negativeRiskThreshold");

        double positiveRange = positiveRangeThreshold.doubleValue();
        double positiveRisk = positiveRiskThreshold.doubleValue();
        double negativeRange = negativeRangeThreshold.doubleValue();
        double negativeRisk = negativeRiskThreshold.doubleValue();

        if (Double.isNaN(positiveRange) || Double.isInfinite(positiveRange)) {
            throw new IllegalArgumentException("positiveRangeThreshold must be a finite number");
        }
        if (Double.isNaN(positiveRisk) || Double.isInfinite(positiveRisk)) {
            throw new IllegalArgumentException("positiveRiskThreshold must be a finite number");
        }
        if (Double.isNaN(negativeRange) || Double.isInfinite(negativeRange)) {
            throw new IllegalArgumentException("negativeRangeThreshold must be a finite number");
        }
        if (Double.isNaN(negativeRisk) || Double.isInfinite(negativeRisk)) {
            throw new IllegalArgumentException("negativeRiskThreshold must be a finite number");
        }
        if (positiveRange < 0D) {
            throw new IllegalArgumentException("positiveRangeThreshold must be greater than or equal to 0");
        }
        if (positiveRisk <= positiveRange) {
            throw new IllegalArgumentException("positiveRiskThreshold must be greater than positiveRangeThreshold");
        }
        if (negativeRange > 0D) {
            throw new IllegalArgumentException("negativeRangeThreshold must be less than or equal to 0");
        }
        if (negativeRisk >= negativeRange) {
            throw new IllegalArgumentException("negativeRiskThreshold must be less than negativeRangeThreshold");
        }
    }

    /**
     * Classifies the provided MACD-V value using this profile.
     *
     * @param macdV MACD-V value
     * @return momentum state
     * @since 0.22.3
     */
    public MACDVMomentumState classify(Num macdV) {
        if (Num.isNaNOrNull(macdV)) {
            return MACDVMomentumState.UNDEFINED;
        }
        Num positiveRange = macdV.getNumFactory().numOf(positiveRangeThreshold);
        Num positiveRisk = macdV.getNumFactory().numOf(positiveRiskThreshold);
        Num negativeRange = macdV.getNumFactory().numOf(negativeRangeThreshold);
        Num negativeRisk = macdV.getNumFactory().numOf(negativeRiskThreshold);

        if (macdV.isGreaterThan(positiveRisk)) {
            return MACDVMomentumState.HIGH_RISK;
        }
        if (macdV.isGreaterThanOrEqual(positiveRange)) {
            return MACDVMomentumState.RALLYING_OR_RETRACING;
        }
        if (macdV.isGreaterThanOrEqual(negativeRange)) {
            return MACDVMomentumState.RANGING;
        }
        if (macdV.isGreaterThanOrEqual(negativeRisk)) {
            return MACDVMomentumState.REBOUNDING_OR_REVERSING;
        }
        return MACDVMomentumState.LOW_RISK;
    }
}
