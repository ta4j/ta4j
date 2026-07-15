/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.state;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Log-return state enriched with Bayesian run-length uncertainty.
 *
 * <p>
 * {@link #recentChangeProbability()} is the complete posterior mass assigned to
 * run lengths within the estimator's configured recent-change window. The
 * ordered {@link #topRunLengths()} list retains complete-posterior
 * probabilities and is not renormalized as a subset. Common moments match the
 * first, most likely component.
 *
 * <p>
 * An unstable state retains known consecutive-observation provenance, uses
 * {@link NaN#NaN} for its probability, {@code -1} for its run length, and an
 * empty posterior list.
 *
 * @param moments                 validated log-return moments for the most
 *                                likely component
 * @param recentChangeProbability posterior mass inside the configured recent
 *                                window
 * @param mostLikelyRunLength     maximum-a-posteriori run length
 * @param topRunLengths           probability-descending posterior summaries
 * @since 0.23.1
 */
public record OnlineChangePointForecastState(ReturnMoments moments, Num recentChangeProbability,
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
            RunLengthPosterior previous = null;
            for (RunLengthPosterior posterior : topRunLengths) {
                RunLengthPosterior input = Objects.requireNonNull(posterior, "posterior must not be null");
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
                previous = normalized;
            }
            Num probabilityTolerance = numFactory.numOf(1e-12d);
            if (probabilityTotal.isGreaterThan(numFactory.one().plus(probabilityTolerance))) {
                throw new IllegalArgumentException("posterior summary probabilities must not exceed one");
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
     * @param recentChangeProbability posterior mass in the recent-change window
     * @param mostLikelyRunLength     MAP run length
     * @param topRunLengths           ordered posterior summaries
     * @return stable state
     * @since 0.23.1
     */
    public static OnlineChangePointForecastState stable(ReturnMoments moments, Num recentChangeProbability,
            int mostLikelyRunLength, List<RunLengthPosterior> topRunLengths) {
        ReturnMoments validated = Objects.requireNonNull(moments, "moments must not be null");
        if (!validated.isStable()) {
            throw new IllegalArgumentException("moments must be stable");
        }
        return new OnlineChangePointForecastState(validated, recentChangeProbability, mostLikelyRunLength,
                topRunLengths);
    }

    /**
     * Creates unavailable state while preserving valid-run provenance.
     *
     * @param index            source index
     * @param observationCount consecutive valid observations since the last reset
     * @return unstable log-return change-point state
     * @since 0.23.1
     */
    public static OnlineChangePointForecastState unstable(int index, int observationCount) {
        return new OnlineChangePointForecastState(
                ReturnMoments.unstable(index, observationCount, ReturnRepresentation.LOG), NaN.NaN, -1, List.of());
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
}
