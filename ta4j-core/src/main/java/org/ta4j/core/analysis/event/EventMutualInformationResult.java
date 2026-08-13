/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.event;

import java.util.Objects;

import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Immutable outcome of one {@link EventMutualInformationEvaluator} evaluation.
 *
 * <p>
 * Mutual information and entropy are reported in natural logarithms (nats).
 * {@link #normalizedMutualInformation()} is {@code MI / H(Y)}: the fraction of
 * target uncertainty explained by the discretized predictor, naturally in
 * {@code [0, 1]} subject to numeric tolerance. When the target is constant the
 * raw MI is zero and the normalized MI is {@code NaN}, so a no-event target can
 * never become a perfect optimizer score.
 * </p>
 *
 * <p>
 * The evaluation is <em>undefined</em> when the effective sample range is empty
 * or a predictor sample is non-finite: the metric values are {@code NaN} while
 * the diagnostic counts stay factual, so candidate sample counts cannot drift
 * invisibly.
 * </p>
 *
 * @param mutualInformationNats       raw mutual information, {@code NaN} when
 *                                    undefined
 * @param targetEntropyNats           target entropy, {@code NaN} when undefined
 * @param normalizedMutualInformation {@code MI / H(Y)}, {@code NaN} when the
 *                                    target is constant or the result is
 *                                    undefined
 * @param sampleCount                 predictor samples with a fully
 *                                    inside-range target window
 * @param positiveTargetCount         samples whose target window contains at
 *                                    least one event
 * @param positiveTargetRate          event prevalence, {@code NaN} when
 *                                    {@code sampleCount == 0}
 * @param requestedBinCount           configured predictor bin count
 * @param effectiveBinCount           bins actually formed; smaller than the
 *                                    requested count when equal-frequency ties
 *                                    merge or trailing equal-width bins stay
 *                                    empty, or {@code 0} when undefined
 * @param binningStrategy             discretization used
 * @param targetWindowStartBars       inclusive lower bound of the target window
 * @param targetWindowEndBars         inclusive upper bound of the target window
 * @since 0.24.2
 */
public record EventMutualInformationResult(Num mutualInformationNats, Num targetEntropyNats,
        Num normalizedMutualInformation, int sampleCount, int positiveTargetCount, Num positiveTargetRate,
        int requestedBinCount, int effectiveBinCount, BinningStrategy binningStrategy, int targetWindowStartBars,
        int targetWindowEndBars) {

    /**
     * Validates the result.
     *
     * @throws NullPointerException     if a numeric value or the strategy is null
     * @throws IllegalArgumentException if a count is negative, a requested bin
     *                                  count is below 2 or above
     *                                  {@link EventMutualInformationConfig#MAX_PREDICTOR_BIN_COUNT},
     *                                  the target window offsets are inconsistent,
     *                                  an empty sample range carries defined
     *                                  metrics or nonzero effective bins, an
     *                                  undefined result (NaN raw MI or entropy)
     *                                  carries defined normalized MI or formed
     *                                  bins, a nonempty sample range carries a
     *                                  non-finite or count-inconsistent
     *                                  {@code positiveTargetRate}, a defined result
     *                                  carries non-finite or negative raw metrics
     *                                  or zero effective bins, a constant target
     *                                  carries nonzero raw metrics or defined
     *                                  normalized MI, or a non-constant target
     *                                  carries non-positive entropy, a negative MI,
     *                                  a normalized value outside {@code [0, 1]},
     *                                  or one inconsistent with {@code MI / H(Y)}
     */
    public EventMutualInformationResult {
        Objects.requireNonNull(mutualInformationNats, "mutualInformationNats");
        Objects.requireNonNull(targetEntropyNats, "targetEntropyNats");
        Objects.requireNonNull(normalizedMutualInformation, "normalizedMutualInformation");
        Objects.requireNonNull(positiveTargetRate, "positiveTargetRate");
        Objects.requireNonNull(binningStrategy, "binningStrategy");
        if (sampleCount < 0 || positiveTargetCount < 0 || positiveTargetCount > sampleCount) {
            throw new IllegalArgumentException("sample counts are inconsistent");
        }
        if (requestedBinCount < 2 || requestedBinCount > EventMutualInformationConfig.MAX_PREDICTOR_BIN_COUNT
                || effectiveBinCount < 0 || effectiveBinCount > requestedBinCount) {
            // The evaluator never requests more than MAX_PREDICTOR_BIN_COUNT
            // bins, so a direct construction or deserialized value above the
            // ceiling describes a configuration the evaluator rejects.
            throw new IllegalArgumentException("bin counts are inconsistent");
        }
        if (binningStrategy == BinningStrategy.EQUAL_FREQUENCY && effectiveBinCount > sampleCount) {
            // Equal-frequency binning creates at most one bin per nonempty
            // sample group, so the evaluator can never report more effective
            // bins than samples; a direct construction or deserialized value
            // with such a count describes an impossible diagnostic. Equal-width
            // binning divides the value range instead, so requested bins beyond
            // the sample count stay representable there.
            throw new IllegalArgumentException("bin counts are inconsistent");
        }
        if (targetWindowStartBars < 0 || targetWindowEndBars < targetWindowStartBars) {
            throw new IllegalArgumentException("target window offsets are inconsistent");
        }
        if (sampleCount == 0 && (!mutualInformationNats.isNaN() || !targetEntropyNats.isNaN()
                || !normalizedMutualInformation.isNaN() || !positiveTargetRate.isNaN() || effectiveBinCount != 0)) {
            throw new IllegalArgumentException(
                    "an empty sample range must produce an undefined result (NaN metrics, effectiveBinCount 0)");
        }
        if ((mutualInformationNats.isNaN() || targetEntropyNats.isNaN()) && (!mutualInformationNats.isNaN()
                || !targetEntropyNats.isNaN() || !normalizedMutualInformation.isNaN() || effectiveBinCount != 0)) {
            // A non-finite predictor sample makes the result undefined while the
            // diagnostic counts stay factual; a partially defined raw metric,
            // formed bins, or a defined normalized value would contradict that
            // state.
            throw new IllegalArgumentException("an undefined result must carry NaN metrics and effectiveBinCount 0");
        }
        if (sampleCount > 0) {
            // The documented factual prevalence is positiveTargetCount /
            // sampleCount; comparing the rate with exact Num arithmetic
            // rejects any other value, including contradictory finite rates
            // that a rounded decimal comparison would accept (for example
            // 0.500009 for 5 of 10).
            Num expectedRate = positiveTargetRate.getNumFactory()
                    .numOf(positiveTargetCount)
                    .dividedBy(positiveTargetRate.getNumFactory().numOf(sampleCount));
            if (!Double.isFinite(positiveTargetRate.doubleValue()) || positiveTargetRate.compareTo(expectedRate) != 0) {
                throw new IllegalArgumentException(
                        "a nonempty sample range must carry a finite positiveTargetRate consistent with the counts");
            }
        }
        if (sampleCount > 0 && !mutualInformationNats.isNaN() && !targetEntropyNats.isNaN()) {
            if (effectiveBinCount == 0) {
                // The evaluator always forms at least one bin from a nonempty
                // finite sample range; defined metrics alongside zero formed
                // bins is a contradictory state.
                throw new IllegalArgumentException("a defined result must carry at least one effective bin");
            }
            if (effectiveBinCount == 1 && !mutualInformationNats.isZero()) {
                // A single effective bin holds every predictor sample, so the
                // predictor is constant: it cannot carry information about the
                // target, and the raw mutual information must be exactly zero.
                throw new IllegalArgumentException(
                        "a single effective bin is a constant predictor and must carry zero mutual information");
            }
            if (!Double.isFinite(mutualInformationNats.doubleValue())
                    || !Double.isFinite(targetEntropyNats.doubleValue())) {
                // The raw metrics are documented as finite mutual information
                // and entropy; an overflowing histogram sum must not pass as a
                // defined result.
                throw new IllegalArgumentException(
                        "a defined result must carry finite raw mutual information and target entropy");
            }
            if (mutualInformationNats.isNegative() || targetEntropyNats.isNegative()) {
                throw new IllegalArgumentException("raw mutual information and target entropy cannot be negative");
            }
            // A constant target (0 or sampleCount positive samples) has zero
            // target entropy and zero raw mutual information, so the evaluator
            // always pairs it with NaN normalized MI; a non-constant target
            // has positive entropy and a defined normalized value in [0, 1].
            // Any other pairing is contradictory: either a zero-variance
            // target normalized against nothing, a defined rate that is
            // silently dropped, or a normalized value that can never arise
            // from the documented definition.
            boolean constantTarget = positiveTargetCount == 0 || positiveTargetCount == sampleCount;
            if (constantTarget) {
                if (!mutualInformationNats.isZero() || !targetEntropyNats.isZero()) {
                    throw new IllegalArgumentException(
                            "a constant target must carry zero raw mutual information and target entropy");
                }
                if (!normalizedMutualInformation.isNaN()) {
                    throw new IllegalArgumentException(
                            "a constant target must carry NaN normalized mutual information");
                }
            } else {
                if (!targetEntropyNats.isPositive()) {
                    throw new IllegalArgumentException("a non-constant target must carry positive target entropy");
                }
                // A binary target's entropy is fully determined by its positive
                // rate: H(Y) = -p ln p - (1 - p) ln(1 - p) with
                // p = positiveTargetCount / sampleCount. Recomputed with the
                // metric's own Num arithmetic the expression matches the
                // evaluator bit for bit; anything beyond rounding-scale noise
                // is an impossible entropy for the documented counts.
                NumFactory metricFactory = targetEntropyNats.getNumFactory();
                Num positiveProbability = metricFactory.numOf(positiveTargetCount)
                        .dividedBy(metricFactory.numOf(sampleCount));
                Num negativeProbability = metricFactory.one().minus(positiveProbability);
                Num expectedEntropy = positiveProbability.multipliedBy(positiveProbability.log())
                        .plus(negativeProbability.multipliedBy(negativeProbability.log()))
                        .negate();
                Num entropyTolerance = metricFactory.epsilon()
                        .multipliedBy(metricFactory.one().plus(expectedEntropy.abs()));
                if (expectedEntropy.minus(targetEntropyNats).abs().isGreaterThan(entropyTolerance)) {
                    throw new IllegalArgumentException(
                            "target entropy must match the binary entropy of positiveTargetCount / sampleCount");
                }
                double normalized = normalizedMutualInformation.doubleValue();
                // The evaluator clamps its own accumulation-scale roundoff
                // (tiny negative sums to zero, MI above the target entropy to
                // the entropy) before constructing the result, so a value it
                // produces is always inside the mathematical bounds. This
                // record is also directly constructible, and for those values
                // the bound must stay meaningful: a computed ratio can exceed
                // the bounds by rounding noise of the metric's own precision
                // only (for example 1 + 2e-16 in Double or 1 + 1e-7 in
                // Float). Scaling the tolerance by the bin count would let
                // sparse equal-width tables smuggle values far outside
                // [0, 1] (a 1,000,000-bin request would accept 1.05), so the
                // tolerance stays at the factory epsilon.
                double roundingTolerance = normalizedMutualInformation.getNumFactory().epsilon().doubleValue();
                if (!Double.isFinite(normalized) || normalized < -roundingTolerance
                        || normalized > 1.0 + roundingTolerance) {
                    throw new IllegalArgumentException(
                            "a non-constant target must carry a finite normalized mutual information in [0, 1]");
                }
                NumFactory normalizedFactory = normalizedMutualInformation.getNumFactory();
                Num expectedNormalized = normalizedFactory.numOf(mutualInformationNats.getDelegate())
                        .dividedBy(normalizedFactory.numOf(targetEntropyNats.getDelegate()));
                if (!Num.isFinite(expectedNormalized)) {
                    throw new IllegalArgumentException(
                            "a non-constant target must carry normalized mutual information equal to MI / H(Y)");
                }
                // The recomputed ratio amplifies the rounding error of the raw
                // metrics by 1 / H(Y), which a sparse event stream can make
                // arbitrarily large, so scale the tolerance by the expected
                // ratio's magnitude instead of comparing against an absolute
                // epsilon.
                Num normalizedDifference = normalizedMutualInformation.minus(expectedNormalized).abs();
                Num normalizedTolerance = normalizedFactory.epsilon()
                        .multipliedBy(normalizedFactory.one().plus(expectedNormalized.abs()));
                if (normalizedDifference.compareTo(normalizedTolerance) > 0) {
                    throw new IllegalArgumentException(
                            "a non-constant target must carry normalized mutual information equal to MI / H(Y)");
                }
            }
        }
    }
}
