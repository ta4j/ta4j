/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import java.util.Objects;

import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Validates Elliott swing amplitudes against common Fibonacci retracement and
 * extension ranges.
 *
 * <p>
 * Use this validator when you need to score how closely swing relationships
 * align with canonical Elliott ratios. It is used internally by
 * {@link ElliottPhaseIndicator} and {@link ElliottScenarioGenerator}, and can
 * be customized with a tighter or looser tolerance depending on your market and
 * timeframe.
 *
 * @since 0.22.0
 */
public class ElliottFibonacciValidator {

    private final Num tolerance;

    private final Num waveTwoMinRetracement;
    private final Num waveTwoMaxRetracement;
    private final Num waveThreeMinExtension;
    private final Num waveThreeMaxExtension;
    private final Num waveFourMinRetracement;
    private final Num waveFourMaxRetracement;
    private final Num waveFiveMinProjection;
    private final Num waveFiveMaxProjection;
    private final Num waveBMinRetracement;
    private final Num waveBMaxRetracement;
    private final Num waveBFlatMinRetracement;
    private final Num waveCMinExtension;
    private final Num waveCMaxExtension;

    /**
     * Builds a validator with the default {@code 0.05} tolerance.
     *
     * @param numFactory series factory used for all ratio math
     * @since 0.22.0
     */
    public ElliottFibonacciValidator(final NumFactory numFactory) {
        this(numFactory, numFactory.numOf(0.05));
    }

    /**
     * Builds a validator with a caller supplied tolerance.
     *
     * @param numFactory series factory used for all ratio math
     * @param tolerance  symmetric tolerance applied to each Fibonacci band
     * @since 0.22.0
     */
    public ElliottFibonacciValidator(final NumFactory numFactory, final Num tolerance) {
        Objects.requireNonNull(numFactory, "numFactory");
        this.tolerance = Objects.requireNonNull(tolerance, "tolerance");
        this.waveTwoMinRetracement = numFactory.numOf(0.382);
        this.waveTwoMaxRetracement = numFactory.numOf(0.786);
        this.waveThreeMinExtension = numFactory.numOf(1.0);
        this.waveThreeMaxExtension = numFactory.numOf(2.618);
        this.waveFourMinRetracement = numFactory.numOf(0.236);
        this.waveFourMaxRetracement = numFactory.numOf(0.786);
        this.waveFiveMinProjection = numFactory.numOf(0.618);
        this.waveFiveMaxProjection = numFactory.numOf(1.618);
        this.waveBMinRetracement = numFactory.numOf(0.382);
        this.waveBMaxRetracement = numFactory.numOf(0.886);
        this.waveBFlatMinRetracement = numFactory.numOf(0.786);
        this.waveCMinExtension = numFactory.numOf(1.0);
        this.waveCMaxExtension = numFactory.numOf(1.618);
    }

    /**
     * @param wave1 first impulse swing
     * @param wave2 second impulse swing retracement
     * @return {@code true} when wave two retraces the first swing within the
     *         canonical Fibonacci band
     * @since 0.22.0
     */
    public boolean isWaveTwoRetracementValid(final ElliottSwing wave1, final ElliottSwing wave2) {
        return ratioBetween(wave2.amplitude(), wave1.amplitude(), waveTwoMinRetracement, waveTwoMaxRetracement);
    }

    /**
     * @param wave1 first impulse swing
     * @param wave3 third impulse swing
     * @return {@code true} when wave three extends the first swing by at least one
     *         full unit and stays below common blow-off extremes
     * @since 0.22.0
     */
    public boolean isWaveThreeExtensionValid(final ElliottSwing wave1, final ElliottSwing wave3) {
        return ratioBetween(wave3.amplitude(), wave1.amplitude(), waveThreeMinExtension, waveThreeMaxExtension);
    }

    /**
     * @param wave3 third impulse swing
     * @param wave4 fourth impulse swing
     * @return {@code true} when wave four retraces the third swing within tolerance
     * @since 0.22.0
     */
    public boolean isWaveFourRetracementValid(final ElliottSwing wave3, final ElliottSwing wave4) {
        return ratioBetween(wave4.amplitude(), wave3.amplitude(), waveFourMinRetracement, waveFourMaxRetracement);
    }

    /**
     * @param wave1 first impulse swing
     * @param wave5 final impulse swing
     * @return {@code true} when wave five projects a typical Fibonacci expansion of
     *         wave one
     * @since 0.22.0
     */
    public boolean isWaveFiveProjectionValid(final ElliottSwing wave1, final ElliottSwing wave5) {
        return ratioBetween(wave5.amplitude(), wave1.amplitude(), waveFiveMinProjection, waveFiveMaxProjection);
    }

    /**
     * @param waveA first corrective swing
     * @param waveB second corrective swing
     * @return {@code true} when wave B retraces wave A within the common range
     * @since 0.22.0
     */
    public boolean isWaveBRetracementValid(final ElliottSwing waveA, final ElliottSwing waveB) {
        return ratioBetween(waveB.amplitude(), waveA.amplitude(), waveBMinRetracement, waveBMaxRetracement);
    }

    /**
     * Validates wave B retracement for flat corrective patterns.
     *
     * <p>
     * Flat patterns require wave B to retrace at least 78.6% of wave A, which is
     * stricter than the general wave B retracement requirement.
     *
     * @param waveA first corrective swing
     * @param waveB second corrective swing
     * @return {@code true} when wave B retraces at least 78.6% of wave A and stays
     *         within the maximum retracement bound
     * @since 0.22.0
     */
    public boolean isWaveBFlatRetracementValid(final ElliottSwing waveA, final ElliottSwing waveB) {
        return ratioBetween(waveB.amplitude(), waveA.amplitude(), waveBFlatMinRetracement, waveBMaxRetracement);
    }

    /**
     * @param waveA first corrective swing
     * @param waveC third corrective swing
     * @return {@code true} when wave C extends beyond wave A by a common Fibonacci
     *         factor
     * @since 0.22.0
     */
    public boolean isWaveCExtensionValid(final ElliottSwing waveA, final ElliottSwing waveC) {
        return ratioBetween(waveC.amplitude(), waveA.amplitude(), waveCMinExtension, waveCMaxExtension);
    }

    /**
     * Calculates a continuous proximity score for wave two retracement (0.0 - 1.0).
     *
     * <p>
     * Returns 1.0 when the ratio is exactly at the ideal level (0.618), with scores
     * decreasing as the ratio moves away from the ideal within the valid range.
     *
     * @param wave1 first impulse swing
     * @param wave2 second impulse swing retracement
     * @return proximity score (0.0 - 1.0), or 0.0 if outside valid range
     * @since 0.22.0
     */
    public Num waveTwoProximityScore(final ElliottSwing wave1, final ElliottSwing wave2) {
        return ratioProximityScore(wave2.amplitude(), wave1.amplitude(), waveTwoMinRetracement, waveTwoMaxRetracement,
                waveTwoMinRetracement.getNumFactory().numOf(0.618));
    }

    /**
     * Calculates a continuous proximity score for wave three extension (0.0 - 1.0).
     *
     * @param wave1 first impulse swing
     * @param wave3 third impulse swing
     * @return proximity score (0.0 - 1.0), or 0.0 if outside valid range
     * @since 0.22.0
     */
    public Num waveThreeProximityScore(final ElliottSwing wave1, final ElliottSwing wave3) {
        return ratioProximityScore(wave3.amplitude(), wave1.amplitude(), waveThreeMinExtension, waveThreeMaxExtension,
                waveThreeMinExtension.getNumFactory().numOf(1.618));
    }

    /**
     * Calculates a continuous proximity score for wave four retracement (0.0 -
     * 1.0).
     *
     * @param wave3 third impulse swing
     * @param wave4 fourth impulse swing
     * @return proximity score (0.0 - 1.0), or 0.0 if outside valid range
     * @since 0.22.0
     */
    public Num waveFourProximityScore(final ElliottSwing wave3, final ElliottSwing wave4) {
        return ratioProximityScore(wave4.amplitude(), wave3.amplitude(), waveFourMinRetracement, waveFourMaxRetracement,
                waveFourMinRetracement.getNumFactory().numOf(0.382));
    }

    /**
     * Calculates a continuous proximity score for wave five projection (0.0 - 1.0).
     *
     * @param wave1 first impulse swing
     * @param wave5 final impulse swing
     * @return proximity score (0.0 - 1.0), or 0.0 if outside valid range
     * @since 0.22.0
     */
    public Num waveFiveProximityScore(final ElliottSwing wave1, final ElliottSwing wave5) {
        return ratioProximityScore(wave5.amplitude(), wave1.amplitude(), waveFiveMinProjection, waveFiveMaxProjection,
                waveFiveMinProjection.getNumFactory().numOf(1.0));
    }

    /**
     * Calculates a continuous proximity score for wave B retracement (0.0 - 1.0).
     *
     * @param waveA first corrective swing
     * @param waveB second corrective swing
     * @return proximity score (0.0 - 1.0), or 0.0 if outside valid range
     * @since 0.22.0
     */
    public Num waveBProximityScore(final ElliottSwing waveA, final ElliottSwing waveB) {
        return ratioProximityScore(waveB.amplitude(), waveA.amplitude(), waveBMinRetracement, waveBMaxRetracement,
                waveBMinRetracement.getNumFactory().numOf(0.618));
    }

    /**
     * Calculates a continuous proximity score for wave C extension (0.0 - 1.0).
     *
     * @param waveA first corrective swing
     * @param waveC third corrective swing
     * @return proximity score (0.0 - 1.0), or 0.0 if outside valid range
     * @since 0.22.0
     */
    public Num waveCProximityScore(final ElliottSwing waveA, final ElliottSwing waveC) {
        return ratioProximityScore(waveC.amplitude(), waveA.amplitude(), waveCMinExtension, waveCMaxExtension,
                waveCMinExtension.getNumFactory().numOf(1.0));
    }

    /**
     * Calculates a generic proximity score for any swing ratio (0.0 - 1.0).
     *
     * <p>
     * The score is 1.0 when the ratio equals the ideal value, decreasing linearly
     * toward the bounds. Ratios outside the extended tolerance range receive 0.0.
     *
     * @param numerator   numerator swing amplitude
     * @param denominator denominator swing amplitude
     * @param lower       lower bound of valid range
     * @param upper       upper bound of valid range
     * @param ideal       ideal ratio value (for maximum score)
     * @return proximity score (0.0 - 1.0)
     * @since 0.22.0
     */
    public Num ratioProximityScore(final Num numerator, final Num denominator, final Num lower, final Num upper,
            final Num ideal) {
        final NumFactory factory = lower.getNumFactory();

        if (!Num.isValid(numerator) || !Num.isValid(denominator)) {
            return factory.zero();
        }
        if (denominator.isZero()) {
            return factory.zero();
        }

        final Num ratio = numerator.dividedBy(denominator).abs();
        final Num lowerBound = lower.minus(tolerance);
        final Num upperBound = upper.plus(tolerance);

        // Outside extended range = 0.0
        if (ratio.isLessThan(lowerBound) || ratio.isGreaterThan(upperBound)) {
            return factory.zero();
        }

        // Within range: score based on distance from ideal
        final Num distanceFromIdeal = ratio.minus(ideal).abs();
        final Num rangeHalf = upper.minus(lower).dividedBy(factory.numOf(2));

        if (rangeHalf.isZero()) {
            return factory.one();
        }

        // Score = 1.0 - (distance from ideal / range half) * 0.5, clamped to [0.0, 1.0]
        final Num normalizedDistance = distanceFromIdeal.dividedBy(rangeHalf);
        final Num baseScore = factory.one().minus(normalizedDistance.multipliedBy(factory.numOf(0.5)));
        // Clamp to [0.0, 1.0]
        if (baseScore.isLessThan(factory.zero())) {
            return factory.zero();
        }
        if (baseScore.isGreaterThan(factory.one())) {
            return factory.one();
        }

        return baseScore;
    }

    private boolean ratioBetween(final Num numerator, final Num denominator, final Num lower, final Num upper) {
        if (!Num.isValid(numerator) || !Num.isValid(denominator)) {
            return false;
        }
        if (denominator.isZero()) {
            return false;
        }
        final Num ratio = numerator.dividedBy(denominator).abs();
        final Num lowerBound = lower.minus(tolerance);
        final Num upperBound = upper.plus(tolerance);
        return !ratio.isLessThan(lowerBound) && !ratio.isGreaterThan(upperBound);
    }
}
