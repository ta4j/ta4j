/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import org.ta4j.core.indicators.statistics.DynamicTimeWarpingDistanceIndicator.Config;
import org.ta4j.core.indicators.statistics.DynamicTimeWarpingDistanceIndicator.LocalDistance;
import org.ta4j.core.indicators.statistics.DynamicTimeWarpingDistanceIndicator.PathCostNormalization;
import org.ta4j.core.indicators.statistics.DynamicTimeWarpingDistanceIndicator.SequenceNormalization;
import org.ta4j.core.indicators.statistics.DynamicTimeWarpingDistanceIndicator.WarpingWindow;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Two-row dynamic-programming core of
 * {@link DynamicTimeWarpingDistanceIndicator}.
 *
 * <p>
 * Computes the minimum-cost monotonic alignment between two finite sequences of
 * equal length. The recurrence is
 * {@code D(i,j) = localCost(i,j) + min(D(i-1,j), D(i,j-1), D(i-1,j-1))} inside
 * the configured alignment band. Only the previous row of costs and path
 * lengths is retained, so memory stays {@code O(W)} for window size {@code W}.
 * </p>
 *
 * <p>
 * Among equal-cost predecessors the shorter path length wins first, so the
 * path-length-normalized distance does not depend on the scan direction; paths
 * that tie on both cost and length are resolved deterministically in the order
 * diagonal, vertical, horizontal. The path length is tracked alongside the cost
 * so path-length normalization is exact.
 * </p>
 *
 * <p>
 * Complexity is {@code O(W * min(W, 2r + 1))} for a Sakoe–Chiba radius
 * {@code r} and {@code O(W^2)} for unconstrained warping.
 * </p>
 */
final class DynamicTimeWarpingSupport {

    private DynamicTimeWarpingSupport() {
    }

    /**
     * Computes the warped distance between two finite, equally long sequences.
     *
     * @param numFactory factory producing the result
     * @param first      first sequence
     * @param second     second sequence
     * @param config     normalization, local distance, band, and cost normalization
     * @return the warped distance, or {@code NaN} when the sequences contain
     *         non-finite values, z-score normalization is numerically undefined
     *         (mean or variance overflow), or the result is not finite
     */
    static Num distance(NumFactory numFactory, Num[] first, Num[] second, Config config) {
        int sampleCount = first.length;
        Num[] firstSequence = normalize(numFactory, first, config.normalization());
        Num[] secondSequence = normalize(numFactory, second, config.normalization());
        if (firstSequence == null || secondSequence == null) {
            return NaN.NaN;
        }

        Num[] previousCost = new Num[sampleCount];
        int[] previousLength = new int[sampleCount];
        Num[] currentCost = new Num[sampleCount];
        int[] currentLength = new int[sampleCount];
        // Path-length-normalized distances are accumulated as a running mean
        // over each cell's best path instead of a raw sum divided at the end:
        // a path whose local costs are each finite but sum to infinity (for
        // example two costs of 1e308) would otherwise overflow the accumulator
        // even though the mean is finite. With non-negative costs every
        // intermediate mean lies between the smallest and largest cost seen,
        // so the incremental form stays finite whenever each cost is.
        boolean byPathLength = config.pathCostNormalization() == PathCostNormalization.BY_PATH_LENGTH;
        Num[] previousMeanCost = byPathLength ? new Num[sampleCount] : null;
        Num[] currentMeanCost = byPathLength ? new Num[sampleCount] : null;

        for (int i = 0; i < sampleCount; i++) {
            int columnMin = columnMin(i, config.warpingWindow(), sampleCount);
            int columnMax = columnMax(i, config.warpingWindow(), sampleCount);
            // Clear the range this row can read (horizontal predecessor at
            // columnMin - 1) or write, so stale cells from two rows back can
            // never be misread as reachable predecessors. Each row clears a
            // slice of its own band, keeping total clearing work O(W * band).
            for (int k = Math.max(0, columnMin - 1); k <= columnMax; k++) {
                currentCost[k] = null;
                currentLength[k] = 0;
                if (byPathLength) {
                    currentMeanCost[k] = null;
                }
            }
            for (int j = columnMin; j <= columnMax; j++) {
                Num localCost = localCost(firstSequence[i], secondSequence[j], config.localDistance());

                // Predecessor tie-break order: shorter path length first, then
                // diagonal, vertical, horizontal.
                Num bestCost = null;
                Num bestMeanCost = null;
                int bestLength = 0;
                if (i > 0 && j > 0) {
                    bestCost = previousCost[j - 1];
                    bestMeanCost = byPathLength ? previousMeanCost[j - 1] : null;
                    bestLength = previousLength[j - 1];
                }
                if (byPathLength) {
                    // Order predecessors by their total cost mean * length,
                    // compared overflow-safely: the raw accumulated totals
                    // saturate at infinity (for example sums of 1e308-scale
                    // squared costs), which would turn every overflowing
                    // competitor into an equal-cost tie and let the length
                    // tie-break select a suboptimal path. bestCost still
                    // tracks the chosen predecessor's raw total so the
                    // accumulator stays consistent, but it is never consulted
                    // for ordering or the result in this mode.
                    Num vertical = previousMeanCost[j];
                    if (vertical != null
                            && betterNormalized(vertical, previousLength[j], bestMeanCost, bestLength, numFactory)) {
                        bestCost = previousCost[j];
                        bestMeanCost = vertical;
                        bestLength = previousLength[j];
                    }
                    if (j > 0) {
                        Num horizontal = currentMeanCost[j - 1];
                        if (horizontal != null && betterNormalized(horizontal, currentLength[j - 1], bestMeanCost,
                                bestLength, numFactory)) {
                            bestCost = currentCost[j - 1];
                            bestMeanCost = horizontal;
                            bestLength = currentLength[j - 1];
                        }
                    }
                } else {
                    Num vertical = previousCost[j];
                    if (vertical != null && better(vertical, previousLength[j], bestCost, bestLength)) {
                        bestCost = vertical;
                        bestLength = previousLength[j];
                    }
                    if (j > 0) {
                        Num horizontal = currentCost[j - 1];
                        if (horizontal != null && better(horizontal, currentLength[j - 1], bestCost, bestLength)) {
                            bestCost = horizontal;
                            bestLength = currentLength[j - 1];
                        }
                    }
                }
                if (bestCost == null) {
                    // Start cell: no predecessor.
                    bestCost = numFactory.zero();
                    bestLength = 0;
                }
                currentCost[j] = localCost.plus(bestCost);
                currentLength[j] = bestLength + 1;
                if (byPathLength) {
                    if (bestLength == 0) {
                        currentMeanCost[j] = localCost;
                    } else if (bestMeanCost == null) {
                        // A nonzero path must carry a running mean. Preserve an
                        // undefined result rather than dereferencing an absent one.
                        currentMeanCost[j] = NaN.NaN;
                    } else {
                        Num runningMean = bestMeanCost
                                .plus(localCost.minus(bestMeanCost).dividedBy(numFactory.numOf(bestLength + 1)));
                        if (runningMean.isZero() && (localCost.isPositive() || bestMeanCost.isPositive())) {
                            // A positive path cost whose mean underflows to zero
                            // would violate the zero-means-identical contract.
                            currentMeanCost[j] = NaN.NaN;
                        } else {
                            currentMeanCost[j] = runningMean;
                        }
                    }
                }
            }

            Num[] swapCost = previousCost;
            previousCost = currentCost;
            currentCost = swapCost;
            int[] swapLength = previousLength;
            previousLength = currentLength;
            currentLength = swapLength;
            if (byPathLength) {
                Num[] swapMeanCost = previousMeanCost;
                previousMeanCost = currentMeanCost;
                currentMeanCost = swapMeanCost;
            }
        }

        if (byPathLength) {
            // The running mean is already normalized by path length.
            Num mean = previousMeanCost[sampleCount - 1];
            return CorrelationWindowSupport.isFinite(mean) ? mean : NaN.NaN;
        }
        Num total = previousCost[sampleCount - 1];
        return CorrelationWindowSupport.isFinite(total) ? total : NaN.NaN;
    }

    /**
     * @return {@code true} when the candidate predecessor strictly improves the
     *         selection: strictly lower cost, or equal cost with a strictly shorter
     *         path
     */
    private static boolean better(Num candidateCost, int candidateLength, Num bestCost, int bestLength) {
        if (bestCost == null) {
            return true;
        }
        int comparison = candidateCost.compareTo(bestCost);
        return comparison < 0 || (comparison == 0 && candidateLength < bestLength);
    }

    /**
     * @return {@code true} when the candidate predecessor strictly improves the
     *         selection under path-length normalization: strictly lower total cost
     *         {@code mean * length}, or equal total cost with a strictly shorter
     *         path
     */
    private static boolean betterNormalized(Num candidateMean, int candidateLength, Num bestMean, int bestLength,
            NumFactory numFactory) {
        if (bestMean == null) {
            return true;
        }
        // Comparing the total costs mean * length as materialized products can
        // overflow even when every local cost is finite; dividing each mean by
        // the other predecessor's length preserves the product ordering for
        // positive lengths without an overflowing multiplication.
        int comparison = candidateMean.dividedBy(numFactory.numOf(bestLength))
                .compareTo(bestMean.dividedBy(numFactory.numOf(candidateLength)));
        return comparison < 0 || (comparison == 0 && candidateLength < bestLength);
    }

    private static int columnMin(int row, WarpingWindow warpingWindow, int sampleCount) {
        if (warpingWindow.unrestricted()) {
            return 0;
        }
        long minimum = Math.max(0L, (long) row - warpingWindow.radius());
        return (int) Math.min(minimum, sampleCount - 1L);
    }

    private static int columnMax(int row, WarpingWindow warpingWindow, int sampleCount) {
        if (warpingWindow.unrestricted()) {
            return sampleCount - 1;
        }
        long maximum = Math.min((long) sampleCount - 1L, (long) row + warpingWindow.radius());
        return (int) Math.max(maximum, 0L);
    }

    private static Num localCost(Num firstValue, Num secondValue, LocalDistance localDistance) {
        Num delta = firstValue.minus(secondValue);
        if (localDistance == LocalDistance.ABSOLUTE) {
            return delta.abs();
        }
        Num squared = delta.multipliedBy(delta);
        // A nonzero delta whose square underflows to zero (raw subnormal
        // deltas under NONE normalization) would otherwise be scored as
        // identical, breaking the zero-means-identical contract. Such a cost
        // is unrepresentable in the requested precision: report it as NaN so
        // the distance stays undefined.
        if (squared.isZero() && (delta.isPositive() || delta.isNegative())) {
            return NaN.NaN;
        }
        return squared;
    }

    /**
     * @return the normalized sequence, the input itself for {@code NONE}, or
     *         {@code null} when z-score normalization is numerically undefined
     *         (non-finite input, mean, or variance)
     */
    private static Num[] normalize(NumFactory numFactory, Num[] values, SequenceNormalization normalization) {
        if (normalization == SequenceNormalization.NONE) {
            return values;
        }
        // Rescale by the largest absolute value before computing the moments:
        // raw squared deviations of a tiny-magnitude varying window (for
        // example around 1e-200) underflow to zero in double precision, which
        // would misclassify it as a constant sequence. Z-scores are scale
        // invariant, so normalizing the rescaled sequence yields the same
        // result while keeping every intermediate in [-1, 1].
        Num scale = numFactory.zero();
        for (Num value : values) {
            if (!CorrelationWindowSupport.isFinite(value)) {
                // Non-finite input makes the mean and variance undefined.
                return null;
            }
            Num magnitude = value.abs();
            if (magnitude.isGreaterThan(scale)) {
                scale = magnitude;
            }
        }
        if (scale.isZero()) {
            // All-zero sequence: constant, so shape distance ignores level.
            Num[] zeros = new Num[values.length];
            java.util.Arrays.fill(zeros, numFactory.zero());
            return zeros;
        }
        Num[] rescaled = new Num[values.length];
        for (int i = 0; i < values.length; i++) {
            rescaled[i] = values[i].dividedBy(scale);
        }
        // Incremental mean keeps the running sum finite even when the rescaled
        // values are near the extremes: plain summation of e.g. ten values of
        // magnitude one could overflow to infinity before the division.
        Num mean = numFactory.zero();
        for (int i = 0; i < values.length; i++) {
            mean = mean.plus(rescaled[i].minus(mean).dividedBy(numFactory.numOf(i + 1)));
        }
        Num sumOfSquares = numFactory.zero();
        for (Num value : rescaled) {
            Num delta = value.minus(mean);
            sumOfSquares = sumOfSquares.plus(delta.multipliedBy(delta));
        }
        Num standardDeviation = sumOfSquares.dividedBy(numFactory.numOf(values.length)).sqrt();
        if (standardDeviation.isZero()) {
            // Constant sequence: map to zeros so shape distance ignores level.
            Num[] zeros = new Num[values.length];
            java.util.Arrays.fill(zeros, numFactory.zero());
            return zeros;
        }
        if (!CorrelationWindowSupport.isFinite(standardDeviation)) {
            // Mean or variance overflow: z-scoring is numerically undefined.
            // Report the distance as NaN instead of silently measuring the raw
            // values, which would misrepresent the requested normalization.
            return null;
        }
        Num[] normalized = new Num[values.length];
        for (int i = 0; i < values.length; i++) {
            normalized[i] = rescaled[i].minus(mean).dividedBy(standardDeviation);
        }
        return normalized;
    }
}
