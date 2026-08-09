/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.event;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.analysis.event.EventSynchronizationConfig.EmptyEventPolicy;
import org.ta4j.core.analysis.event.EventSynchronizationConfig.HistoryPolicy;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Deterministic one-to-one evaluator for two sparse event streams over the same
 * {@link BarSeries}.
 *
 * <p>
 * Given an inclusive {@code [startIndex, endIndex]} evaluation range, predicted
 * and reference events are extracted in one pass and matched one-to-one with
 * the following lexicographic objective:
 * </p>
 * <ol>
 * <li>maximize the number of matched pairs</li>
 * <li>among maximum-cardinality assignments, minimize the total absolute
 * offset</li>
 * <li>then minimize the worst absolute offset</li>
 * <li>then prefer the lexicographically earliest sequence of
 * {@code (predictedIndex, referenceIndex)} pairs</li>
 * </ol>
 *
 * <p>
 * Events outside the effective evaluation range can never satisfy an in-range
 * event, so training and validation windows cannot match across their boundary.
 * The effective start is the maximum of the requested start, the series begin
 * index, and both signals' unstable-bar boundaries. Under
 * {@link HistoryPolicy#STRICT} a request that includes unavailable history
 * fails fast; under {@link HistoryPolicy#CLAMP} the range is intersected with
 * the available history.
 *
 * <p>
 * The matching cost depends on the number of events, not the number of bars:
 * the baseline matcher runs in {@code O(|P| * |R|)} time and memory for
 * {@code |P|} predicted and {@code |R|} reference events.
 *
 * @since 0.24.1
 */
public final class EventSynchronizationEvaluator {

    /**
     * Upper bound on the sequence-alignment matrix cells to keep the baseline
     * matcher's memory bounded: 8 million cells at 16 bytes per cell is roughly 128
     * MB of arrays (~2,800 events per stream). The documented
     * {@link IllegalArgumentException} therefore always fires before an allocation
     * that could become an {@link OutOfMemoryError}.
     */
    private static final long MAX_MATCHING_CELLS = 8_000_000L;

    /** Sentinel for "no matched pair yet" in worst-offset tracking. */
    private static final int NO_WORST_OFFSET = -1;

    private static final int[] EMPTY_EVENTS = new int[0];

    /**
     * Evaluates two event streams over an inclusive bar-index range.
     *
     * @param predicted  the predicted event stream
     * @param reference  the reference event stream
     * @param startIndex the requested inclusive start index
     * @param endIndex   the requested inclusive end index
     * @param config     the matching and policy configuration
     * @return the immutable evaluation result
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if the signals use different series, the
     *                                  requested range is inverted, unavailable
     *                                  history is requested under
     *                                  {@link HistoryPolicy#STRICT}, or the event
     *                                  counts exceed the baseline matcher capacity
     */
    public EventSynchronizationResult evaluate(EventSignal predicted, EventSignal reference, int startIndex,
            int endIndex, EventSynchronizationConfig config) {
        Objects.requireNonNull(predicted, "predicted");
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(config, "config");
        if (startIndex > endIndex) {
            throw new IllegalArgumentException(
                    "startIndex (" + startIndex + ") must not exceed endIndex (" + endIndex + ")");
        }

        BarSeries series = predicted.getBarSeries();
        BarSeries otherSeries = reference.getBarSeries();
        // ta4j indicators expose read-only views over the underlying series;
        // the view's equals() unwraps both sides, so this check must be
        // symmetric to accept an indicator adapter paired with a rule or
        // predicate adapter over the same series.
        if (!series.equals(otherSeries) && !otherSeries.equals(series)) {
            throw new IllegalArgumentException("predicted and reference must be defined over the same BarSeries");
        }

        int availableStart = series.getBeginIndex();
        int availableEnd = series.getEndIndex();
        int effectiveStart;
        int effectiveEnd;
        if (config.historyPolicy() == HistoryPolicy.STRICT) {
            // Bars below the signals' unstable boundaries are unavailable history
            // too: fail fast whenever the requested range includes them.
            int unavailableStart = Math.max(availableStart,
                    Math.max(predicted.getCountOfUnstableBars(), reference.getCountOfUnstableBars()));
            if (startIndex < unavailableStart || endIndex > availableEnd) {
                throw new IllegalArgumentException("requested range [" + startIndex + ", " + endIndex
                        + "] includes unavailable history [" + unavailableStart + ", " + availableEnd + "]");
            }
            effectiveStart = Math.max(startIndex, unavailableStart);
            effectiveEnd = endIndex;
        } else {
            effectiveStart = Math.max(startIndex, Math.max(availableStart,
                    Math.max(predicted.getCountOfUnstableBars(), reference.getCountOfUnstableBars())));
            effectiveEnd = Math.min(endIndex, availableEnd);
            if (effectiveStart > effectiveEnd) {
                // Canonical empty inclusive range: start == end + 1.
                effectiveStart = effectiveEnd + 1;
            }
        }

        int[] predictedEvents = extractEvents(predicted, effectiveStart, effectiveEnd);
        int[] referenceEvents = extractEvents(reference, effectiveStart, effectiveEnd);
        List<EventMatch> matches = matchEvents(predictedEvents, referenceEvents, config);

        NumFactory numFactory = series.numFactory();
        int predictedCount = predictedEvents.length;
        int referenceCount = referenceEvents.length;
        int matchedCount = matches.size();
        int falsePositives = predictedCount - matchedCount;
        int falseNegatives = referenceCount - matchedCount;

        Num precision;
        Num recall;
        Num f1;
        if (predictedCount == 0 && referenceCount == 0) {
            switch (config.emptyEventPolicy()) {
            case UNDEFINED_WHEN_BOTH_EMPTY -> {
                precision = NaN.NaN;
                recall = NaN.NaN;
                f1 = NaN.NaN;
            }
            case ZERO_WHEN_BOTH_EMPTY -> {
                precision = numFactory.zero();
                recall = numFactory.zero();
                f1 = numFactory.zero();
            }
            case ONE_WHEN_BOTH_EMPTY -> {
                precision = numFactory.one();
                recall = numFactory.one();
                f1 = numFactory.one();
            }
            default -> throw new AssertionError("unknown EmptyEventPolicy: " + config.emptyEventPolicy());
            }
        } else if (predictedCount == 0) {
            precision = NaN.NaN;
            recall = numFactory.zero();
            f1 = numFactory.zero();
        } else if (referenceCount == 0) {
            precision = numFactory.zero();
            recall = NaN.NaN;
            f1 = numFactory.zero();
        } else {
            precision = numFactory.numOf(matchedCount).dividedBy(numFactory.numOf(predictedCount));
            recall = numFactory.numOf(matchedCount).dividedBy(numFactory.numOf(referenceCount));
            if (precision.isZero() && recall.isZero()) {
                f1 = numFactory.zero();
            } else {
                f1 = numFactory.two().multipliedBy(precision).multipliedBy(recall).dividedBy(precision.plus(recall));
            }
        }

        int exactMatchCount = 0;
        long offsetSum = 0L;
        long absoluteOffsetSum = 0L;
        int minOffset = Integer.MAX_VALUE;
        int maxOffset = Integer.MIN_VALUE;
        long[] offsets = new long[matchedCount];
        for (int i = 0; i < matchedCount; i++) {
            EventMatch match = matches.get(i);
            int offset = match.offsetBars();
            offsets[i] = offset;
            if (offset == 0) {
                exactMatchCount++;
            }
            offsetSum += offset;
            absoluteOffsetSum += offset < 0 ? -(long) offset : offset;
            if (offset < minOffset) {
                minOffset = offset;
            }
            if (offset > maxOffset) {
                maxOffset = offset;
            }
        }

        Num meanSignedOffset;
        Num meanAbsoluteOffset;
        Num medianSignedOffset;
        Num minSignedOffset;
        Num maxSignedOffset;
        if (matchedCount == 0) {
            meanSignedOffset = NaN.NaN;
            meanAbsoluteOffset = NaN.NaN;
            medianSignedOffset = NaN.NaN;
            minSignedOffset = NaN.NaN;
            maxSignedOffset = NaN.NaN;
        } else {
            Arrays.sort(offsets);
            meanSignedOffset = numFactory.numOf(offsetSum).dividedBy(numFactory.numOf(matchedCount));
            meanAbsoluteOffset = numFactory.numOf(absoluteOffsetSum).dividedBy(numFactory.numOf(matchedCount));
            int mid = matchedCount / 2;
            if ((matchedCount & 1) == 1) {
                medianSignedOffset = numFactory.numOf(offsets[mid]);
            } else {
                medianSignedOffset = numFactory.numOf(offsets[mid - 1])
                        .plus(numFactory.numOf(offsets[mid]))
                        .dividedBy(numFactory.two());
            }
            minSignedOffset = numFactory.numOf(minOffset);
            maxSignedOffset = numFactory.numOf(maxOffset);
        }

        return new EventSynchronizationResult(startIndex, endIndex, effectiveStart, effectiveEnd, predictedCount,
                referenceCount, matchedCount, falsePositives, falseNegatives, precision, recall, f1, matches,
                unmatchedIndexes(predictedEvents, matches, true), unmatchedIndexes(referenceEvents, matches, false),
                exactMatchCount, meanSignedOffset, meanAbsoluteOffset, medianSignedOffset, minSignedOffset,
                maxSignedOffset, config.emptyEventPolicy());
    }

    private static int[] extractEvents(EventSignal signal, int startIndex, int endIndex) {
        if (endIndex < startIndex) {
            return EMPTY_EVENTS;
        }
        int[] events = new int[Math.min(16, endIndex - startIndex + 1)];
        int size = 0;
        for (int i = startIndex; i <= endIndex; i++) {
            if (signal.isEvent(i)) {
                if (size == events.length) {
                    if ((long) events.length * 2 > MAX_MATCHING_CELLS) {
                        // A single signal with more events than the baseline
                        // matcher's cell cap can never participate in an
                        // evaluation below the cap, so fail with the documented
                        // exception before allocating further.
                        throw new IllegalArgumentException("event count exceeds the baseline matcher capacity of "
                                + (MAX_MATCHING_CELLS / 1_000_000L) + " million cells (~128 MB of alignment arrays)");
                    }
                    events = Arrays.copyOf(events, events.length * 2);
                }
                events[size++] = i;
            }
        }
        return size == events.length ? events : Arrays.copyOf(events, size);
    }

    private static List<EventMatch> matchEvents(int[] predicted, int[] reference, EventSynchronizationConfig config) {
        int n = predicted.length;
        int m = reference.length;
        if (n == 0 || m == 0) {
            return List.of();
        }
        long cells = (long) (n + 1) * (m + 1);
        if (cells > MAX_MATCHING_CELLS) {
            throw new IllegalArgumentException(
                    "event counts " + n + " x " + m + " exceed the baseline matcher " + "capacity of "
                            + (MAX_MATCHING_CELLS / 1_000_000L) + " million cells (~128 MB of alignment " + "arrays)");
        }
        int stride = m + 1;
        int[] bestPairs = new int[(int) cells];
        long[] bestTotalAbs = new long[(int) cells];
        int[] bestWorstAbs = new int[(int) cells];
        for (int i = n; i >= 0; i--) {
            bestWorstAbs[i * stride + m] = NO_WORST_OFFSET;
        }
        for (int j = m; j >= 0; j--) {
            bestWorstAbs[n * stride + j] = NO_WORST_OFFSET;
        }

        int maxLeadBars = config.maxLeadBars();
        int maxLagBars = config.maxLagBars();
        for (int i = n - 1; i >= 0; i--) {
            long predictedIndex = predicted[i];
            int row = i * stride;
            for (int j = m - 1; j >= 0; j--) {
                int cell = row + j;
                int bestPairsHere = bestPairs[cell + stride];
                long bestTotalHere = bestTotalAbs[cell + stride];
                int bestWorstHere = bestWorstAbs[cell + stride];
                if (isBetter(bestPairs[cell + 1], bestTotalAbs[cell + 1], bestWorstAbs[cell + 1], bestPairsHere,
                        bestTotalHere, bestWorstHere)) {
                    bestPairsHere = bestPairs[cell + 1];
                    bestTotalHere = bestTotalAbs[cell + 1];
                    bestWorstHere = bestWorstAbs[cell + 1];
                }
                long offset = (long) reference[j] - predictedIndex;
                if (offset >= -maxLagBars && offset <= maxLeadBars) {
                    int matchedCell = cell + stride + 1;
                    long absoluteOffset = offset < 0 ? -offset : offset;
                    int candidatePairs = bestPairs[matchedCell] + 1;
                    long candidateTotal = bestTotalAbs[matchedCell] + absoluteOffset;
                    int candidateWorst = Math.max(bestWorstAbs[matchedCell], (int) absoluteOffset);
                    if (isBetter(candidatePairs, candidateTotal, candidateWorst, bestPairsHere, bestTotalHere,
                            bestWorstHere)) {
                        bestPairsHere = candidatePairs;
                        bestTotalHere = candidateTotal;
                        bestWorstHere = candidateWorst;
                    }
                }
                bestPairs[cell] = bestPairsHere;
                bestTotalAbs[cell] = bestTotalHere;
                bestWorstAbs[cell] = bestWorstHere;
            }
        }

        List<EventMatch> matches = new ArrayList<>();
        int i = 0;
        int j = 0;
        // The lexicographic tie-break compares full sequences, so a continuation
        // only needs to keep the *global* worst offset optimal: a candidate pair
        // is admissible when its child can still complete the global optimum, not
        // merely when it reproduces the target subproblem's minimal worst. An
        // earlier chosen pair can already reach the global worst, in which case a
        // lexicographically earlier continuation whose own suffix worst is not
        // suffix-minimal is still the canonical choice.
        int worstBudget = bestWorstAbs[0];
        while (i < n && j < m) {
            int targetCell = i * stride + j;
            int targetPairs = bestPairs[targetCell];
            if (targetPairs == 0) {
                break;
            }
            boolean found = false;
            for (int k = i; k < n && !found; k++) {
                long predictedIndex = predicted[k];
                int firstEligible = lowerBound(reference, j, m, predictedIndex - maxLagBars);
                int firstIneligible = upperBound(reference, j, m, predictedIndex + maxLeadBars);
                for (int l = firstEligible; l < firstIneligible; l++) {
                    int childCell = (k + 1) * stride + (l + 1);
                    long offset = (long) reference[l] - predictedIndex;
                    long absoluteOffset = offset < 0 ? -offset : offset;
                    if (bestPairs[childCell] + 1 == targetPairs
                            && bestTotalAbs[childCell] + absoluteOffset == bestTotalAbs[targetCell]
                            && Math.max(bestWorstAbs[childCell], (int) absoluteOffset) <= worstBudget) {
                        matches.add(new EventMatch(predicted[k], reference[l], (int) offset));
                        i = k + 1;
                        j = l + 1;
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                // The DP invariant guarantees an optimal-feasible first pair
                // whenever the target has at least one pair; a violation would
                // silently understate the match counts, so fail loudly.
                throw new IllegalStateException("matcher invariant violated: no optimal-feasible pair at (" + i + ", "
                        + j + ") with " + targetPairs + " pairs remaining");
            }
        }
        return matches;
    }

    /**
     * Lexicographic objective comparison on {@code (pairs, -totalAbs, -worstAbs)}:
     * {@code true} when the candidate is strictly better than the incumbent.
     */
    private static boolean isBetter(int candidatePairs, long candidateTotal, int candidateWorst, int incumbentPairs,
            long incumbentTotal, int incumbentWorst) {
        return candidatePairs > incumbentPairs || (candidatePairs == incumbentPairs && (candidateTotal < incumbentTotal
                || (candidateTotal == incumbentTotal && candidateWorst < incumbentWorst)));
    }

    private static int lowerBound(int[] values, int from, int to, long target) {
        int low = from;
        int high = to;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (values[mid] < target) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    private static int upperBound(int[] values, int from, int to, long target) {
        int low = from;
        int high = to;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (values[mid] <= target) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    private static List<Integer> unmatchedIndexes(int[] events, List<EventMatch> matches, boolean predictedSide) {
        List<Integer> unmatched = new ArrayList<>();
        int matchIndex = 0;
        for (int event : events) {
            while (matchIndex < matches.size() && (predictedSide ? matches.get(matchIndex).predictedIndex()
                    : matches.get(matchIndex).referenceIndex()) < event) {
                matchIndex++;
            }
            boolean matched = matchIndex < matches.size() && (predictedSide ? matches.get(matchIndex).predictedIndex()
                    : matches.get(matchIndex).referenceIndex()) == event;
            if (!matched) {
                unmatched.add(event);
            }
        }
        return unmatched;
    }
}
