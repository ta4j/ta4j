/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.event;

import java.util.Objects;

import org.ta4j.core.num.Num;

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
 *                                    merge, or {@code 0} when undefined
 * @param binningStrategy             discretization used
 * @param targetWindowStartBars       inclusive lower bound of the target window
 * @param targetWindowEndBars         inclusive upper bound of the target window
 * @since 0.24.1
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
     *                                  effective bins, or an undefined result (NaN
     *                                  raw MI or entropy) carries defined
     *                                  normalized MI or formed bins
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
        if ((mutualInformationNats.isNaN() || targetEntropyNats.isNaN())
                && (!normalizedMutualInformation.isNaN() || effectiveBinCount != 0)) {
            // A non-finite predictor sample makes the result undefined while the
            // diagnostic counts stay factual; formed bins or a defined normalized
            // value would contradict that state.
            throw new IllegalArgumentException("an undefined result must carry NaN metrics and effectiveBinCount 0");
        }
    }
}
