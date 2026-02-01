/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import java.util.Objects;

import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Captures confidence metrics for an Elliott wave interpretation.
 *
 * <p>
 * Confidence is computed as a weighted combination of multiple factors:
 * <ul>
 * <li>Fibonacci proximity (35%): How closely swing ratios match canonical
 * levels</li>
 * <li>Time proportions (20%): Whether wave durations follow expected
 * relationships</li>
 * <li>Alternation quality (15%): Degree of pattern/depth alternation between
 * waves 2 and 4</li>
 * <li>Channel adherence (15%): Price staying within projected channel
 * bounds</li>
 * <li>Structure completeness (15%): How many expected waves are confirmed</li>
 * </ul>
 *
 * @param overall             aggregate confidence score (0.0 - 1.0)
 * @param fibonacciScore      how closely ratios match canonical Fib levels (0.0
 *                            - 1.0)
 * @param timeProportionScore wave duration ratio conformance (0.0 - 1.0)
 * @param alternationScore    wave 2/4 alternation quality (0.0 - 1.0)
 * @param channelScore        price adherence to projected channel (0.0 - 1.0)
 * @param completenessScore   structure completeness (0.0 - 1.0)
 * @param primaryReason       human-readable description of dominant factor
 * @since 0.22.0
 */
public record ElliottConfidence(Num overall, Num fibonacciScore, Num timeProportionScore, Num alternationScore,
        Num channelScore, Num completenessScore, String primaryReason) {

    /** Default threshold above which a confidence is considered high. */
    public static final double HIGH_CONFIDENCE_THRESHOLD = 0.7;

    /** Default threshold below which a confidence is considered low. */
    public static final double LOW_CONFIDENCE_THRESHOLD = 0.3;

    public ElliottConfidence {
        Objects.requireNonNull(overall, "overall");
        Objects.requireNonNull(fibonacciScore, "fibonacciScore");
        Objects.requireNonNull(timeProportionScore, "timeProportionScore");
        Objects.requireNonNull(alternationScore, "alternationScore");
        Objects.requireNonNull(channelScore, "channelScore");
        Objects.requireNonNull(completenessScore, "completenessScore");
    }

    /**
     * Creates a zero-confidence instance representing an invalid or uncomputable
     * scenario.
     *
     * @param numFactory factory for creating numeric values
     * @return confidence with all scores set to zero
     * @since 0.22.0
     */
    public static ElliottConfidence zero(final NumFactory numFactory) {
        Objects.requireNonNull(numFactory, "numFactory");
        final Num zero = numFactory.zero();
        return new ElliottConfidence(zero, zero, zero, zero, zero, zero, "No valid structure");
    }

    /**
     * @return {@code true} when the overall confidence meets or exceeds the high
     *         confidence threshold (0.7)
     * @since 0.22.0
     */
    public boolean isHighConfidence() {
        return isAboveThreshold(HIGH_CONFIDENCE_THRESHOLD);
    }

    /**
     * @return {@code true} when the overall confidence is below the low confidence
     *         threshold (0.3)
     * @since 0.22.0
     */
    public boolean isLowConfidence() {
        return !isAboveThreshold(LOW_CONFIDENCE_THRESHOLD);
    }

    /**
     * Checks whether the overall confidence meets or exceeds a custom threshold.
     *
     * @param threshold threshold value (0.0 - 1.0)
     * @return {@code true} when overall >= threshold
     * @since 0.22.0
     */
    public boolean isAboveThreshold(final double threshold) {
        if (Num.isNaNOrNull(overall)) {
            return false;
        }
        return overall.doubleValue() >= threshold;
    }

    /**
     * @return {@code true} when the overall score is valid (non-null and not NaN)
     * @since 0.22.0
     */
    public boolean isValid() {
        return Num.isValid(overall);
    }

    /**
     * Returns the overall confidence as a percentage (0-100).
     *
     * @return confidence percentage, or NaN if invalid
     * @since 0.22.0
     */
    public double asPercentage() {
        if (!isValid()) {
            return Double.NaN;
        }
        return overall.doubleValue() * 100.0;
    }

    /**
     * Identifies the weakest scoring factor that is most limiting overall
     * confidence.
     *
     * @return description of the weakest factor
     * @since 0.22.0
     */
    public String weakestFactor() {
        if (!isValid()) {
            return "Invalid confidence";
        }

        Num weakest = fibonacciScore;
        String factor = "Fibonacci proximity";

        if (safeCompare(timeProportionScore, weakest) < 0) {
            weakest = timeProportionScore;
            factor = "Time proportions";
        }
        if (safeCompare(alternationScore, weakest) < 0) {
            weakest = alternationScore;
            factor = "Wave alternation";
        }
        if (safeCompare(channelScore, weakest) < 0) {
            weakest = channelScore;
            factor = "Channel adherence";
        }
        if (safeCompare(completenessScore, weakest) < 0) {
            factor = "Structure completeness";
        }

        return factor;
    }

    private int safeCompare(final Num a, final Num b) {
        if (Num.isNaNOrNull(a)) {
            return -1;
        }
        if (Num.isNaNOrNull(b)) {
            return 1;
        }
        return a.compareTo(b);
    }
}
