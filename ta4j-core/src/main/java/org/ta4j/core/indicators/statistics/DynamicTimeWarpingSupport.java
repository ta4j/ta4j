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
 * so path-length normalization is exact. Under path-length normalization the
 * best-path total is carried in a scaled form ({@code value * 2^scale}) so
 * totals that overflow the representable range (for example a single absolute
 * difference of {@code 2e308}, or two squared costs of {@code 1e308}) still
 * yield their exact mean, while predecessor selection ranks the same running
 * means as a direct accumulation ({@code m + (c - m) / (l + 1)}) so the chosen
 * paths never diverge from the reference form.
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
        // Path-length-normalized distances carry each cell's best path as a
        // scaled total (value * 2^scale): a local cost or sum can exceed the
        // representable range (for example |1e308 - (-1e308)|, or two squared
        // costs of 1e308) while the path's mean stays finite, and scaling by
        // powers of two is exact, so the derived mean differs from a direct
        // accumulation only by final rounding. The same scaled total also
        // drives predecessor selection: comparing totals directly (instead of
        // rounded running means) honors the documented tie-break exactly and
        // never lets subnormal rounding reorder equal totals.
        boolean byPathLength = config.pathCostNormalization() == PathCostNormalization.BY_PATH_LENGTH;
        Num[] previousMeanValue = byPathLength ? new Num[sampleCount] : null;
        int[] previousMeanScale = byPathLength ? new int[sampleCount] : null;
        Num[] currentMeanValue = byPathLength ? new Num[sampleCount] : null;
        int[] currentMeanScale = byPathLength ? new int[sampleCount] : null;
        Num maxValue = byPathLength ? numFactory.numOf(Double.MAX_VALUE) : null;

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
                    currentMeanValue[k] = null;
                    currentMeanScale[k] = 0;
                }
            }
            for (int j = columnMin; j <= columnMax; j++) {
                Num localCost;
                ScaledCost pathCost;
                if (byPathLength) {
                    // The scaled cost is the single source of truth for
                    // selection and result; the raw view saturates at
                    // infinity (overflowing absolute difference or squared
                    // cost) and only mirrors the scaled total so the
                    // non-normalized accumulator stays coherent.
                    pathCost = scaledLocalCost(firstSequence[i], secondSequence[j], config.localDistance(), numFactory);
                    localCost = pathCost.scale() == 0 ? pathCost.value()
                            : pathCost.value().multipliedBy(pow2(pathCost.scale(), numFactory));
                } else {
                    localCost = localCost(firstSequence[i], secondSequence[j], config.localDistance());
                    pathCost = null;
                }

                // Predecessor tie-break order: shorter path length first, then
                // diagonal, vertical, horizontal.
                Num bestCost = null;
                Num bestMeanValue = null;
                int bestMeanScale = 0;
                int bestLength = 0;
                if (i > 0 && j > 0) {
                    bestCost = previousCost[j - 1];
                    bestMeanValue = byPathLength ? previousMeanValue[j - 1] : null;
                    bestMeanScale = byPathLength ? previousMeanScale[j - 1] : 0;
                    bestLength = previousLength[j - 1];
                }
                if (byPathLength) {
                    // Order predecessors by their exact scaled totals
                    // value * 2^scale: comparing the totals directly honors
                    // the documented tie-break (equal total cost with a
                    // strictly shorter path) instead of letting running-mean
                    // rounding reorder equal totals, which subnormal path
                    // costs in particular turn into a wrong selection. The
                    // totals are carried in scaled form, so the comparison
                    // never materializes an overflowing product.
                    Num vertical = previousMeanValue[j];
                    if (vertical != null && betterNormalized(vertical, previousMeanScale[j], previousLength[j],
                            bestMeanValue, bestMeanScale, bestLength, numFactory)) {
                        bestCost = previousCost[j];
                        bestMeanValue = vertical;
                        bestMeanScale = previousMeanScale[j];
                        bestLength = previousLength[j];
                    }
                    if (j > 0) {
                        Num horizontal = currentMeanValue[j - 1];
                        if (horizontal != null && betterNormalized(horizontal, currentMeanScale[j - 1],
                                currentLength[j - 1], bestMeanValue, bestMeanScale, bestLength, numFactory)) {
                            bestCost = currentCost[j - 1];
                            bestMeanValue = horizontal;
                            bestMeanScale = currentMeanScale[j - 1];
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
                        // Start cell: the path total is the local cost itself.
                        currentMeanValue[j] = pathCost.value();
                        currentMeanScale[j] = pathCost.scale();
                    } else if (bestMeanValue == null) {
                        // A nonzero path must carry a total. Preserve an
                        // undefined result rather than dereferencing an absent one.
                        currentMeanValue[j] = NaN.NaN;
                        currentMeanScale[j] = 0;
                    } else {
                        // total = bestTotal + cost, carried as value * 2^scale
                        // without materializing an overflowing sum: align both
                        // operands to the larger scale, and halve both with a
                        // compensating exponent whenever the sum would exceed
                        // the representable range.
                        int scale = Math.max(bestMeanScale, pathCost.scale());
                        Num bestTotal = bestMeanScale == scale ? bestMeanValue
                                : bestMeanValue.dividedBy(pow2(scale - bestMeanScale, numFactory));
                        Num costTotal = pathCost.scale() == scale ? pathCost.value()
                                : pathCost.value().dividedBy(pow2(scale - pathCost.scale(), numFactory));
                        if (bestTotal.isGreaterThan(maxValue.minus(costTotal))) {
                            bestTotal = bestTotal.dividedBy(numFactory.two());
                            costTotal = costTotal.dividedBy(numFactory.two());
                            scale++;
                        }
                        currentMeanValue[j] = bestTotal.plus(costTotal);
                        currentMeanScale[j] = scale;
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
                Num[] swapMeanValue = previousMeanValue;
                previousMeanValue = currentMeanValue;
                currentMeanValue = swapMeanValue;
                int[] swapMeanScale = previousMeanScale;
                previousMeanScale = currentMeanScale;
                currentMeanScale = swapMeanScale;
            }
        }

        if (byPathLength) {
            // Derive the mean from the scaled total: value * 2^scale / length.
            // A positive total whose mean underflows to zero would violate the
            // zero-means-identical contract, so it is undefined.
            Num totalValue = previousMeanValue[sampleCount - 1];
            if (totalValue == null) {
                return NaN.NaN;
            }
            Num mean = totalValue.dividedBy(numFactory.numOf(previousLength[sampleCount - 1]));
            int meanScale = previousMeanScale[sampleCount - 1];
            if (meanScale > 0) {
                // 2^scale overflows for scale >= 1024 even when the scaled
                // total's true value stays representable (for example a
                // squared local cost carried as m^2 * 2^1024 with a two-cell
                // mean of 9.8e307), so never materialize the full power:
                // value * 2^1023 is finite whenever value * 2^scale is, and
                // the remaining 2^(scale - 1023) restores the true value.
                mean = mean.multipliedBy(pow2(Math.min(meanScale, 1023), numFactory));
                if (meanScale > 1023) {
                    mean = mean.multipliedBy(pow2(meanScale - 1023, numFactory));
                }
            }
            if (mean.isZero() && totalValue.isPositive()) {
                return NaN.NaN;
            }
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
        // An undefined cost marks an unreachable cell: NaN candidates never
        // win, and NaN never blocks a finite best so far (compareTo ties on
        // NaN in primitive-backed Num implementations).
        if (candidateCost == null || candidateCost.isNaN()) {
            return false;
        }
        if (bestCost == null || bestCost.isNaN()) {
            return true;
        }
        int comparison = candidateCost.compareTo(bestCost);
        return comparison < 0 || (comparison == 0 && candidateLength < bestLength);
    }

    /**
     * @return {@code true} when the candidate predecessor strictly improves the
     *         selection under path-length normalization: strictly lower total cost
     *         {@code value * 2^scale}, or equal total cost with a strictly shorter
     *         path
     */
    private static boolean betterNormalized(Num candidateValue, int candidateScale, int candidateLength, Num bestValue,
            int bestScale, int bestLength, NumFactory numFactory) {
        // An undefined value marks an unreachable cell: NaN candidates never
        // win, and NaN never blocks a finite best so far (compareTo ties on
        // NaN in primitive-backed Num implementations).
        if (candidateValue == null || candidateValue.isNaN()) {
            return false;
        }
        if (bestValue == null || bestValue.isNaN()) {
            return true;
        }
        // Compare the exact scaled totals instead of dividing rounded running
        // means: a rounded mean can reorder equal totals (subnormal path
        // costs in particular round their intermediate means so aggressively
        // that the longer path can look cheaper), while the scaled totals
        // carry every accumulated cost exactly and reach the documented
        // shorter-path tie-break whenever the totals really match.
        int comparison = compareScaledTotals(candidateValue, candidateScale, bestValue, bestScale, numFactory);
        return comparison < 0 || (comparison == 0 && candidateLength < bestLength);
    }

    /**
     * Compares two scaled totals {@code value * 2^scale} without materializing the
     * product. Equal scales compare the values directly; otherwise the
     * smaller-scale side is divided by the power of two, which keeps the alignment
     * factor finite (the multiplication direction can overflow) and preserves the
     * ordering even when the division underflows to zero.
     */
    private static int compareScaledTotals(Num valueA, int scaleA, Num valueB, int scaleB, NumFactory numFactory) {
        boolean zeroA = valueA.isZero();
        boolean zeroB = valueB.isZero();
        if (zeroA || zeroB) {
            // A zero total is strictly smaller than any positive total.
            return zeroA && zeroB ? 0 : zeroA ? -1 : 1;
        }
        if (scaleA == scaleB) {
            return valueA.compareTo(valueB);
        }
        if (scaleA > scaleB) {
            return valueA.compareTo(valueB.dividedBy(pow2(scaleA - scaleB, numFactory)));
        }
        return valueA.dividedBy(pow2(scaleB - scaleA, numFactory)).compareTo(valueB);
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
     * A non-negative cost carried as {@code value * 2^scale} without materializing
     * an overflowing product; a NaN value marks an undefined cost, and a scale of
     * zero is the plain value.
     */
    private record ScaledCost(Num value, int scale) {
        static ScaledCost undefined() {
            return new ScaledCost(NaN.NaN, 0);
        }
    }

    /**
     * Computes the local cost in scaled form, safe against overflow of the raw
     * difference (for example {@code |-1e308 - 1e308|}) or of its square.
     *
     * <p>
     * An absolute difference that overflows is carried exactly as
     * {@code (|first| / 2 + |second| / 2) * 2} because both halves fit the
     * representable range. A squared difference is carried as
     * {@code (m * 2^e)^2 = m^2 * 2^(2e)} from the double decomposition of the
     * magnitude. A non-finite operand, an overflowing raw delta, or a nonzero delta
     * whose square underflows to zero stays undefined.
     * </p>
     */
    private static ScaledCost scaledLocalCost(Num firstValue, Num secondValue, LocalDistance localDistance,
            NumFactory numFactory) {
        if (!CorrelationWindowSupport.isFinite(firstValue) || !CorrelationWindowSupport.isFinite(secondValue)) {
            return ScaledCost.undefined();
        }
        Num delta = firstValue.minus(secondValue);
        if (localDistance == LocalDistance.ABSOLUTE) {
            Num magnitude = delta.abs();
            if (CorrelationWindowSupport.isFinite(magnitude)) {
                return new ScaledCost(magnitude, 0);
            }
            // |first - second| = |first| + |second| overflows, but the halved
            // sum is exact and preserves the true value in scaled form.
            return new ScaledCost(
                    firstValue.abs().dividedBy(numFactory.two()).plus(secondValue.abs().dividedBy(numFactory.two())),
                    1);
        }
        if (delta.isZero()) {
            return new ScaledCost(numFactory.zero(), 0);
        }
        if (!CorrelationWindowSupport.isFinite(delta)) {
            // An overflowing raw delta squares beyond any realizable path
            // mean: the cost is unrepresentable.
            return ScaledCost.undefined();
        }
        Num squared = delta.multipliedBy(delta);
        if (CorrelationWindowSupport.isFinite(squared)) {
            // A nonzero delta whose square underflows to zero (raw subnormal
            // deltas under NONE normalization) would otherwise be scored as
            // identical, breaking the zero-means-identical contract. Such a
            // cost is unrepresentable in the requested precision: keep it
            // undefined so the distance stays undefined.
            if (squared.isZero()) {
                return ScaledCost.undefined();
            }
            return new ScaledCost(squared, 0);
        }
        // delta^2 overflows: carry m^2 * 2^(2e) where delta = m * 2^e with
        // m in [1, 2). The decomposition is exact, so the scaled value
        // reproduces the double rounding of delta^2.
        double magnitude = delta.abs().doubleValue();
        int exponent = Math.getExponent(magnitude);
        double mantissa = Math.scalb(magnitude, -exponent);
        return new ScaledCost(numFactory.numOf(mantissa * mantissa), 2 * exponent);
    }

    private static Num pow2(int exponent, NumFactory numFactory) {
        return numFactory.numOf(2).pow(exponent);
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
        // Anchor-shifted moments: values are centered relative to the first
        // rescaled value instead of the mean. The mean of near-endpoint values
        // (for example 1 and Math.nextDown(1)) is not exactly representable,
        // and rounding it to an endpoint would distort the shape: z-scoring
        // [Double.MAX_VALUE, Math.nextDown(Double.MAX_VALUE)] would yield
        // [0, -sqrt(2)] instead of [1, -1], breaking scale/level invariance.
        // The deltas relative to the anchor are exact, and subtracting their
        // exact mean delta recovers the centered values without materializing
        // the mean.
        Num anchor = rescaled[0];
        Num deltaSum = numFactory.zero();
        for (Num value : rescaled) {
            deltaSum = deltaSum.plus(value.minus(anchor));
        }
        Num meanDelta = deltaSum.dividedBy(numFactory.numOf(rescaled.length));
        Num sumOfSquares = numFactory.zero();
        for (Num value : rescaled) {
            Num centered = value.minus(anchor).minus(meanDelta);
            sumOfSquares = sumOfSquares.plus(centered.multipliedBy(centered));
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
            normalized[i] = rescaled[i].minus(anchor).minus(meanDelta).dividedBy(standardDeviation);
        }
        return normalized;
    }
}
