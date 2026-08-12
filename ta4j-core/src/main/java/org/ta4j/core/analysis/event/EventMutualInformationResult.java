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
     * @throws IllegalArgumentException if a count is negative, the target window
     *                                  offsets are inconsistent, an empty sample
     *                                  range carries defined metrics or nonzero
     *                                  effective bins, an undefined result (NaN raw
     *                                  MI or entropy) carries defined normalized MI
     *                                  or formed bins, a nonempty sample range
     *                                  carries a non-finite or count-inconsistent
     *                                  {@code positiveTargetRate}, a defined result
     *                                  carries non-finite raw metrics, a constant
     *                                  target carries nonzero raw metrics or
     *                                  defined normalized MI, or a non-constant
     *                                  target carries a normalized value outside
     *                                  {@code [0, 1]} or inconsistent with
     *                                  {@code MI / H(Y)}
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
        if (requestedBinCount < 2 || effectiveBinCount < 0 || effectiveBinCount > requestedBinCount) {
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
            if (!Double.isFinite(mutualInformationNats.doubleValue())
                    || !Double.isFinite(targetEntropyNats.doubleValue())) {
                // The raw metrics are documented as finite mutual information
                // and entropy; an overflowing histogram sum must not pass as a
                // defined result.
                throw new IllegalArgumentException(
                        "a defined result must carry finite raw mutual information and target entropy");
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
                double normalized = normalizedMutualInformation.doubleValue();
                // Computed ratios can exceed the mathematical bounds by
                // rounding noise only (for example 1 + 2e-16); anything beyond
                // a small tolerance is not a valid normalized value.
                if (!Double.isFinite(normalized) || normalized < -1.0e-12 || normalized > 1.0 + 1.0e-12) {
                    throw new IllegalArgumentException(
                            "a non-constant target must carry a finite normalized mutual information in [0, 1]");
                }
                NumFactory normalizedFactory = normalizedMutualInformation.getNumFactory();
                Num expectedNormalized = normalizedFactory.numOf(mutualInformationNats.getDelegate())
                        .dividedBy(normalizedFactory.numOf(targetEntropyNats.getDelegate()));
                Num normalizedDifference = normalizedMutualInformation.minus(expectedNormalized).abs();
                if (!Num.isFinite(expectedNormalized)
                        || normalizedDifference.compareTo(normalizedFactory.epsilon()) > 0) {
                    throw new IllegalArgumentException(
                            "a non-constant target must carry normalized mutual information equal to MI / H(Y)");
                }
            }
        }
    }
}
