/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics.event;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Package-private matching and scoring engine behind
 * {@link EventSynchronizationIndicator}; not part of the public API.
 *
 * <p>
 * Given an inclusive {@code [startIndex, endIndex]} evaluation range, predicted
 * and reference events are extracted in one pass and matched one-to-one with
 * the lexicographic objective documented on
 * {@link EventSynchronizationIndicator}. The effective range is the
 * intersection of the requested range with the series' available history and
 * both signals' unstable-bar boundaries; an empty intersection is reported as
 * the canonical empty inclusive range ({@code start == end + 1}) with no events
 * and undefined metrics.
 *
 * <p>
 * The matching cost depends on the number of events, not the number of bars:
 * the baseline matcher runs in {@code O(|P| * |R|)} time and memory for
 * {@code |P|} predicted and {@code |R|} reference events.
 */
final class EventSynchronizationSupport {

    /**
     * Upper bound on the sequence-alignment matrix cells to keep the baseline
     * matcher's memory bounded: 8 million cells at 16 bytes per cell is roughly 128
     * MB of arrays (~2,800 events per stream). The documented
     * {@link IllegalArgumentException} therefore always fires before an allocation
     * that could become an {@link OutOfMemoryError}.
     */
    static final long MAX_MATCHING_CELLS = 8_000_000L;

    /** Sentinel for "no matched pair yet" in worst-offset tracking. */
    private static final int NO_WORST_OFFSET = -1;

    private static final int[] EMPTY_EVENTS = new int[0];

    private EventSynchronizationSupport() {
    }

    /**
     * Synchronizes two event signals over an inclusive bar-index range.
     *
     * @param predicted   the predicted event signal
     * @param reference   the reference event signal
     * @param startIndex  the requested inclusive start index
     * @param endIndex    the requested inclusive end index
     * @param maxLeadBars maximum bars a prediction may lead its reference
     * @param maxLagBars  maximum bars a prediction may lag its reference
     * @return the immutable evaluation result
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if the signals use different series, the
     *                                  requested range is inverted, a tolerance is
     *                                  negative, or the event counts exceed the
     *                                  baseline matcher capacity
     */
    static EventSynchronizationResult synchronize(EventSignal predicted, EventSignal reference, int startIndex,
            int endIndex, int maxLeadBars, int maxLagBars) {
        Objects.requireNonNull(predicted, "predicted");
        Objects.requireNonNull(reference, "reference");
        if (startIndex > endIndex) {
            throw new IllegalArgumentException(
                    "startIndex (" + startIndex + ") must not exceed endIndex (" + endIndex + ")");
        }
        validateTolerances(maxLeadBars, maxLagBars);

        BarSeries series = predicted.getBarSeries();
        BarSeries otherSeries = reference.getBarSeries();
        requireSameSeries(series, otherSeries);

        int availableStart = series.getBeginIndex();
        int availableEnd = series.getEndIndex();
        int effectiveStart = Math.max(startIndex, Math.max(availableStart,
                Math.max(predicted.getCountOfUnstableBars(), reference.getCountOfUnstableBars())));
        int effectiveEnd = Math.min(endIndex, availableEnd);
        if (effectiveStart > effectiveEnd) {
            // Canonical empty inclusive range: start == end + 1.
            effectiveStart = effectiveEnd + 1;
        }

        int[] predictedEvents = extractEvents(predicted, effectiveStart, effectiveEnd);
        int[] referenceEvents = extractEvents(reference, effectiveStart, effectiveEnd);
        return synchronize(predictedEvents, referenceEvents, startIndex, endIndex, effectiveStart, effectiveEnd,
                maxLeadBars, maxLagBars, series.numFactory());
    }

    /**
     * Scores pre-extracted event arrays over an inclusive bar-index range.
     *
     * @param predictedEvents the ascending predicted event indexes
     * @param referenceEvents the ascending reference event indexes
     * @param requestedStart  the requested inclusive start index
     * @param requestedEnd    the requested inclusive end index
     * @param effectiveStart  the resolved inclusive start index actually evaluated
     * @param effectiveEnd    the resolved inclusive end index actually evaluated
     * @param maxLeadBars     maximum bars a prediction may lead its reference
     * @param maxLagBars      maximum bars a prediction may lag its reference
     * @param numFactory      the numeric factory for metric outputs
     * @return the immutable evaluation result
     * @throws NullPointerException     if an argument is null
     * @throws IllegalArgumentException if a tolerance is negative or the event
     *                                  counts exceed the baseline matcher capacity
     */
    static EventSynchronizationResult synchronize(int[] predictedEvents, int[] referenceEvents, int requestedStart,
            int requestedEnd, int effectiveStart, int effectiveEnd, int maxLeadBars, int maxLagBars,
            NumFactory numFactory) {
        Objects.requireNonNull(predictedEvents, "predictedEvents");
        Objects.requireNonNull(referenceEvents, "referenceEvents");
        Objects.requireNonNull(numFactory, "numFactory");
        validateTolerances(maxLeadBars, maxLagBars);

        List<EventSynchronizationIndicator.Result.Match> matches = matchEvents(predictedEvents, referenceEvents,
                maxLeadBars, maxLagBars);

        int predictedCount = predictedEvents.length;
        int referenceCount = referenceEvents.length;
        int matchedCount = matches.size();
        int falsePositives = predictedCount - matchedCount;
        int falseNegatives = referenceCount - matchedCount;

        Num precision;
        Num recall;
        Num f1;
        if (predictedCount == 0 && referenceCount == 0) {
            // Both streams empty: the score is undefined, never a perfect score for
            // an optimizer that configures a signal that never fires.
            precision = NaN.NaN;
            recall = NaN.NaN;
            f1 = NaN.NaN;
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
            EventSynchronizationIndicator.Result.Match match = matches.get(i);
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

        return new EventSynchronizationResult(requestedStart, requestedEnd, effectiveStart, effectiveEnd,
                predictedCount, referenceCount, matchedCount, falsePositives, falseNegatives, precision, recall, f1,
                matches, unmatchedIndexes(predictedEvents, matches, true),
                unmatchedIndexes(referenceEvents, matches, false), exactMatchCount, meanSignedOffset,
                meanAbsoluteOffset, medianSignedOffset, minSignedOffset, maxSignedOffset,
                effectiveStart <= effectiveEnd);
    }

    private static void validateTolerances(int maxLeadBars, int maxLagBars) {
        if (maxLeadBars < 0) {
            throw new IllegalArgumentException("maxLeadBars must be >= 0");
        }
        if (maxLagBars < 0) {
            throw new IllegalArgumentException("maxLagBars must be >= 0");
        }
    }

    /**
     * Verifies that two series are the same underlying bar series.
     *
     * <p>
     * ta4j indicators expose read-only views over the underlying series; the
     * identity check unwraps both sides so equal-but-distinct custom series
     * implementations cannot be mistaken for one series.
     *
     * @param series      the first series
     * @param otherSeries the second series
     * @return {@code series}
     * @throws IllegalArgumentException when the series differ
     */
    static BarSeries requireSameSeries(BarSeries series, BarSeries otherSeries) {
        if (!IndicatorUtils.isSameSeries(series, otherSeries)) {
            throw new IllegalArgumentException("predicted and reference must be defined over the same BarSeries");
        }
        return series;
    }

    /**
     * Extracts the event indexes of a signal inside an inclusive range.
     *
     * @param signal     the event signal
     * @param startIndex the inclusive start index
     * @param endIndex   the inclusive end index
     * @return the ascending event indexes; empty when {@code endIndex < startIndex}
     * @throws IllegalArgumentException when the signal fires more often than the
     *                                  baseline matcher's capacity allows
     */
    static int[] extractEvents(EventSignal signal, int startIndex, int endIndex) {
        if (endIndex < startIndex) {
            return EMPTY_EVENTS;
        }
        // Long arithmetic: a rolling series may legally reach
        // Integer.MAX_VALUE, and the inclusive range length can overflow an int.
        int initialCapacity = (int) Math.min(16, (long) endIndex - startIndex + 1);
        int[] events = new int[initialCapacity];
        int size = 0;
        for (int i = startIndex;; i++) {
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
            if (i == endIndex) {
                break;
            }
        }
        return size == events.length ? events : Arrays.copyOf(events, size);
    }

    private static List<EventSynchronizationIndicator.Result.Match> matchEvents(int[] predicted, int[] reference,
            int maxLeadBars, int maxLagBars) {
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

        List<EventSynchronizationIndicator.Result.Match> matches = new ArrayList<>();
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
                        matches.add(new EventSynchronizationIndicator.Result.Match(predicted[k], reference[l]));
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

    static int lowerBound(int[] values, int from, int to, long target) {
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

    static int upperBound(int[] values, int from, int to, long target) {
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

    private static List<Integer> unmatchedIndexes(int[] events,
            List<EventSynchronizationIndicator.Result.Match> matches, boolean predictedSide) {
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
