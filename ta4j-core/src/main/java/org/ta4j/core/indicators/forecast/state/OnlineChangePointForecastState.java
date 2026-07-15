/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.state;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Log-return state enriched with Bayesian run-length uncertainty.
 *
 * <p>
 * {@link #recentChangeProbability()} is the complete posterior mass assigned to
 * run lengths from zero through {@link #recentChangeWindow()}, inclusive. The
 * ordered {@link #topRunLengths()} list retains complete-posterior
 * probabilities and is not renormalized as a subset. Common moments match the
 * first, most likely component. Posterior-mass invariants account for the
 * owning {@link NumFactory}'s decimal quantization without accepting
 * differences larger than that representation can explain.
 *
 * <p>
 * An unstable state retains known consecutive-observation provenance, uses
 * {@link NaN#NaN} for its probability, {@code -1} for its run length, and an
 * empty posterior list.
 *
 * @param moments                 validated log-return moments for the most
 *                                likely component
 * @param recentChangeWindow      inclusive run-length boundary represented by
 *                                the recent-change probability
 * @param recentChangeProbability posterior mass inside the recent-change window
 * @param mostLikelyRunLength     maximum-a-posteriori run length
 * @param topRunLengths           probability-descending posterior summaries
 * @since 0.23.1
 */
public record OnlineChangePointForecastState(ReturnMoments moments, int recentChangeWindow, Num recentChangeProbability,
        int mostLikelyRunLength, List<RunLengthPosterior> topRunLengths) implements ReturnMomentState {

    /**
     * Creates validated change-point state with defensive numeric normalization.
     *
     * @since 0.23.1
     */
    public OnlineChangePointForecastState {
        moments = Objects.requireNonNull(moments, "moments must not be null");
        if (moments.representation() != ReturnRepresentation.LOG) {
            throw new IllegalArgumentException("change-point moments must use ReturnRepresentation.LOG");
        }
        if (recentChangeWindow < 1) {
            throw new IllegalArgumentException("recentChangeWindow must be >= 1");
        }
        recentChangeProbability = Objects.requireNonNull(recentChangeProbability,
                "recentChangeProbability must not be null");
        topRunLengths = List.copyOf(Objects.requireNonNull(topRunLengths, "topRunLengths must not be null"));

        if (!moments.isStable()) {
            if (!recentChangeProbability.isNaN() || mostLikelyRunLength != -1 || !topRunLengths.isEmpty()) {
                throw new IllegalArgumentException(
                        "unstable change-point state must use NaN probability, run length -1, and no posteriors");
            }
        } else {
            if (mostLikelyRunLength < 0) {
                throw new IllegalArgumentException("mostLikelyRunLength must be >= 0");
            }
            if (!moments.drift().isEqual(moments.mean())) {
                throw new IllegalArgumentException("change-point drift must equal the most likely component mean");
            }
            NumFactory numFactory = moments.variance().getNumFactory();
            recentChangeProbability = normalize(recentChangeProbability, numFactory, "recentChangeProbability");
            if (recentChangeProbability.isNegative() || recentChangeProbability.isGreaterThan(numFactory.one())) {
                throw new IllegalArgumentException("recentChangeProbability must be in [0, 1]");
            }
            if (topRunLengths.isEmpty()) {
                throw new IllegalArgumentException("stable change-point state must include posterior summaries");
            }

            List<RunLengthPosterior> normalizedPosteriors = new ArrayList<>(topRunLengths.size());
            Set<Integer> seenRunLengths = new HashSet<>();
            Num probabilityTotal = numFactory.zero();
            Num listedRecentProbability = numFactory.zero();
            Num listedOlderProbability = numFactory.zero();
            Num baseTolerance = numFactory.numOf(1e-12d);
            Num probabilityTotalTolerance = baseTolerance;
            Num listedRecentTolerance = baseTolerance.plus(quantizationTolerance(recentChangeProbability, numFactory));
            Num listedOlderTolerance = listedRecentTolerance;
            RunLengthPosterior previous = null;
            for (RunLengthPosterior posterior : topRunLengths) {
                RunLengthPosterior input = Objects.requireNonNull(posterior, "posterior must not be null");
                if (input.runLength() > moments.observationCount()) {
                    throw new IllegalArgumentException("posterior run length must not exceed observation count");
                }
                RunLengthPosterior normalized = new RunLengthPosterior(input.runLength(),
                        normalize(input.probability(), numFactory, "posterior probability"),
                        normalize(input.mean(), numFactory, "posterior mean"),
                        normalize(input.variance(), numFactory, "posterior variance"));
                if (!seenRunLengths.add(normalized.runLength())) {
                    throw new IllegalArgumentException("posterior run lengths must be unique");
                }
                if (previous != null && !orderedAfter(previous, normalized)) {
                    throw new IllegalArgumentException(
                            "posteriors must be ordered by probability descending and run length ascending");
                }
                normalizedPosteriors.add(normalized);
                probabilityTotal = probabilityTotal.plus(normalized.probability());
                Num componentTolerance = quantizationTolerance(normalized.probability(), numFactory);
                probabilityTotalTolerance = probabilityTotalTolerance.plus(componentTolerance)
                        .plus(quantizationTolerance(probabilityTotal, numFactory));
                if (normalized.runLength() <= recentChangeWindow) {
                    listedRecentProbability = listedRecentProbability.plus(normalized.probability());
                    listedRecentTolerance = listedRecentTolerance.plus(componentTolerance)
                            .plus(quantizationTolerance(listedRecentProbability, numFactory));
                } else {
                    listedOlderProbability = listedOlderProbability.plus(normalized.probability());
                    listedOlderTolerance = listedOlderTolerance.plus(componentTolerance)
                            .plus(quantizationTolerance(listedOlderProbability, numFactory));
                }
                previous = normalized;
            }
            Num completeSupportTolerance = baseTolerance
                    .plus(quantizationTolerance(recentChangeProbability, numFactory));
            if (recentChangeWindow >= moments.observationCount()
                    && recentChangeProbability.isLessThan(numFactory.one().minus(completeSupportTolerance))) {
                throw new IllegalArgumentException(
                        "recentChangeProbability must be one when the window contains every possible run length");
            }
            if (probabilityTotal.isGreaterThan(numFactory.one().plus(probabilityTotalTolerance))) {
                throw new IllegalArgumentException("posterior summary probabilities must not exceed one");
            }
            if (listedRecentProbability.isGreaterThan(recentChangeProbability.plus(listedRecentTolerance))) {
                throw new IllegalArgumentException(
                        "listed recent posterior mass must not exceed recentChangeProbability");
            }
            Num olderProbability = numFactory.one().minus(recentChangeProbability);
            if (listedOlderProbability.isGreaterThan(olderProbability.plus(listedOlderTolerance))) {
                throw new IllegalArgumentException(
                        "listed older posterior mass must not exceed one minus recentChangeProbability");
            }
            RunLengthPosterior mostLikely = normalizedPosteriors.get(0);
            if (mostLikely.runLength() != mostLikelyRunLength) {
                throw new IllegalArgumentException("mostLikelyRunLength must match the first posterior");
            }
            if (!mostLikely.mean().isEqual(moments.mean()) || !mostLikely.variance().isEqual(moments.variance())) {
                throw new IllegalArgumentException("common moments must match the first posterior");
            }
            topRunLengths = List.copyOf(normalizedPosteriors);
        }
    }

    /**
     * Creates stable online change-point state.
     *
     * @param moments                 stable MAP component moments
     * @param recentChangeWindow      inclusive recent-change run-length boundary
     * @param recentChangeProbability posterior mass in the recent-change window
     * @param mostLikelyRunLength     MAP run length
     * @param topRunLengths           ordered posterior summaries
     * @return stable state
     * @since 0.23.1
     */
    public static OnlineChangePointForecastState stable(ReturnMoments moments, int recentChangeWindow,
            Num recentChangeProbability, int mostLikelyRunLength, List<RunLengthPosterior> topRunLengths) {
        ReturnMoments validated = Objects.requireNonNull(moments, "moments must not be null");
        if (!validated.isStable()) {
            throw new IllegalArgumentException("moments must be stable");
        }
        return new OnlineChangePointForecastState(validated, recentChangeWindow, recentChangeProbability,
                mostLikelyRunLength, topRunLengths);
    }

    /**
     * Creates unavailable state while preserving valid-run provenance.
     *
     * @param index              source index
     * @param observationCount   consecutive valid observations since the last reset
     * @param recentChangeWindow inclusive recent-change run-length boundary
     * @return unstable log-return change-point state
     * @since 0.23.1
     */
    public static OnlineChangePointForecastState unstable(int index, int observationCount, int recentChangeWindow) {
        return new OnlineChangePointForecastState(
                ReturnMoments.unstable(index, observationCount, ReturnRepresentation.LOG), recentChangeWindow, NaN.NaN,
                -1, List.of());
    }

    private static boolean orderedAfter(RunLengthPosterior previous, RunLengthPosterior current) {
        if (current.probability().isLessThan(previous.probability())) {
            return true;
        }
        return current.probability().isEqual(previous.probability()) && current.runLength() > previous.runLength();
    }

    private static Num normalize(Num value, NumFactory numFactory, String fieldName) {
        Num input = Objects.requireNonNull(value, fieldName + " must not be null");
        if (!Num.isFinite(input)) {
            throw new IllegalArgumentException(fieldName + " must be finite");
        }
        Num normalized = numFactory.numOf(input.bigDecimalValue());
        if (!Num.isFinite(normalized)) {
            throw new IllegalArgumentException(fieldName + " must be finite in the moments factory");
        }
        if (normalized.isZero() && !input.isZero()) {
            throw new IllegalArgumentException(fieldName + " underflows the moments factory");
        }
        return normalized;
    }

    private static Num quantizationTolerance(Num value, NumFactory numFactory) {
        if (!(value instanceof DecimalNum decimal) || value.isZero()) {
            return numFactory.zero();
        }
        BigDecimal magnitude = decimal.bigDecimalValue().abs();
        int unitExponent = magnitude.precision() - magnitude.scale() - decimal.getMathContext().getPrecision();
        return numFactory.numOf(BigDecimal.ONE.scaleByPowerOfTen(unitExponent));
    }
}
