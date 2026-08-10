/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

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
 * Equal-cost predecessors are resolved deterministically in the order diagonal,
 * vertical, horizontal, and the path length is tracked alongside the cost so
 * path-length normalization is exact.
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
     *         non-finite values or the result is not finite
     */
    static Num distance(NumFactory numFactory, Num[] first, Num[] second, DynamicTimeWarpingConfig config) {
        int sampleCount = first.length;
        Num[] firstSequence = normalize(numFactory, first, config.normalization());
        Num[] secondSequence = normalize(numFactory, second, config.normalization());

        Num[] previousCost = new Num[sampleCount];
        int[] previousLength = new int[sampleCount];
        Num[] currentCost = new Num[sampleCount];
        int[] currentLength = new int[sampleCount];

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
            }
            for (int j = columnMin; j <= columnMax; j++) {
                Num localCost = localCost(firstSequence[i], secondSequence[j], config.localDistance());

                // Predecessor tie-break order: diagonal, vertical, horizontal.
                Num bestCost = null;
                int bestLength = 0;
                if (i > 0 && j > 0) {
                    bestCost = previousCost[j - 1];
                    bestLength = previousLength[j - 1];
                }
                Num vertical = previousCost[j];
                if (vertical != null && (bestCost == null || vertical.compareTo(bestCost) < 0)) {
                    bestCost = vertical;
                    bestLength = previousLength[j];
                }
                if (j > 0) {
                    Num horizontal = currentCost[j - 1];
                    if (horizontal != null && (bestCost == null || horizontal.compareTo(bestCost) < 0)) {
                        bestCost = horizontal;
                        bestLength = currentLength[j - 1];
                    }
                }
                if (bestCost == null) {
                    // Start cell: no predecessor.
                    bestCost = numFactory.zero();
                    bestLength = 0;
                }
                currentCost[j] = localCost.plus(bestCost);
                currentLength[j] = bestLength + 1;
            }

            Num[] swapCost = previousCost;
            previousCost = currentCost;
            currentCost = swapCost;
            int[] swapLength = previousLength;
            previousLength = currentLength;
            currentLength = swapLength;
        }

        Num total = previousCost[sampleCount - 1];
        if (config.pathCostNormalization() == PathCostNormalization.BY_PATH_LENGTH) {
            total = total.dividedBy(numFactory.numOf(previousLength[sampleCount - 1]));
        }
        return CorrelationWindowSupport.isFinite(total) ? total : NaN.NaN;
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
        return localDistance == LocalDistance.ABSOLUTE ? delta.abs() : delta.multipliedBy(delta);
    }

    private static Num[] normalize(NumFactory numFactory, Num[] values, SequenceNormalization normalization) {
        if (normalization == SequenceNormalization.NONE) {
            return values;
        }
        Num mean = average(numFactory, values);
        Num sumOfSquares = numFactory.zero();
        for (Num value : values) {
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
            // Non-finite input: propagate it instead of masking with zeros.
            return values;
        }
        Num[] normalized = new Num[values.length];
        for (int i = 0; i < values.length; i++) {
            normalized[i] = values[i].minus(mean).dividedBy(standardDeviation);
        }
        return normalized;
    }

    private static Num average(NumFactory numFactory, Num[] values) {
        Num sum = numFactory.zero();
        for (Num value : values) {
            sum = sum.plus(value);
        }
        return sum.dividedBy(numFactory.numOf(values.length));
    }
}
