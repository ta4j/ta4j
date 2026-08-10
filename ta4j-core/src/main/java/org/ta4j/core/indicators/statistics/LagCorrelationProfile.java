/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

import org.ta4j.core.num.Num;

/**
 * Immutable result of one {@link LeadLagCorrelationAnalyzer#analyze} call: the
 * full correlation profile over the requested lag range plus the deterministic
 * best-lag selection.
 *
 * <p>
 * {@link #points()} always contains one entry per lag in ascending lag order,
 * including undefined lags. {@link #bestLags()} contains every lag whose
 * correlation ties for the best score under the selection policy, in ascending
 * order; it is empty when no lag is defined. {@link #selectedLag()} is one
 * deterministic pick from {@link #bestLags()}: the smallest absolute lag, then
 * the smallest signed lag, so a symmetric {@code [-k, k]} tie resolves to
 * {@code -k}. The selected correlation keeps its original sign even when the
 * absolute-correlation policy did the selection.
 * </p>
 *
 * @param endIndex            the evaluation index the profile is anchored at
 * @param barCount            the aligned window length shared by all lags
 * @param minimumLag          inclusive lower lag bound of the scan
 * @param maximumLag          inclusive upper lag bound of the scan
 * @param selectionPolicy     the policy used to pick {@link #bestLags()}
 * @param points              one point per lag, ascending, undefined lags
 *                            retained
 * @param bestLags            all lags tying for the best score, ascending;
 *                            empty when no lag is defined
 * @param selectedLag         deterministic pick from {@code bestLags}; empty
 *                            when no lag is defined
 * @param selectedCorrelation the signed correlation at {@code selectedLag}, or
 *                            {@code NaN} when no lag is defined
 * @since 0.24.1
 */
public record LagCorrelationProfile(int endIndex, int barCount, int minimumLag, int maximumLag,
        LagSelectionPolicy selectionPolicy, List<LagCorrelationPoint> points, List<Integer> bestLags,
        OptionalInt selectedLag, Num selectedCorrelation) {

    /**
     * Validates the profile and makes the lists unmodifiable.
     *
     * @throws NullPointerException     if {@code selectionPolicy}, {@code points},
     *                                  {@code bestLags}, {@code selectedLag}, or
     *                                  {@code selectedCorrelation} is null
     * @throws IllegalArgumentException if {@code barCount < 2},
     *                                  {@code minimumLag > maximumLag},
     *                                  {@code bestLags} is not ascending, or the
     *                                  selected lag/correlation combination is
     *                                  inconsistent
     */
    public LagCorrelationProfile {
        if (barCount < 2) {
            throw new IllegalArgumentException("barCount must be >= 2");
        }
        if (minimumLag > maximumLag) {
            throw new IllegalArgumentException("minimumLag must be <= maximumLag");
        }
        Objects.requireNonNull(selectionPolicy, "selectionPolicy");
        points = List.copyOf(points);
        bestLags = List.copyOf(bestLags);
        Objects.requireNonNull(selectedLag, "selectedLag");
        Objects.requireNonNull(selectedCorrelation, "selectedCorrelation");
        long expectedPointCount = (long) maximumLag - minimumLag + 1L;
        if (points.size() != expectedPointCount) {
            throw new IllegalArgumentException("points must contain one point per requested lag");
        }
        for (int index = 0; index < points.size(); index++) {
            long expectedLag = (long) minimumLag + index;
            if (points.get(index).lag() != expectedLag) {
                throw new IllegalArgumentException("points must be in ascending requested-lag order");
            }
        }
        Integer previousLag = null;
        for (Integer lag : bestLags) {
            if (previousLag != null && lag <= previousLag) {
                throw new IllegalArgumentException("bestLags must be strictly ascending");
            }
            previousLag = lag;
        }
        boolean selectedDefined = selectedLag.isPresent();
        boolean correlationDefined = !selectedCorrelation.isNaN();
        if (selectedDefined != correlationDefined) {
            throw new IllegalArgumentException(
                    "selectedLag and selectedCorrelation must be defined together or both undefined");
        }
        // Derive the expected selection from the points themselves so a caller
        // cannot supply a self-consistent but wrong best-lag list or tie-break.
        Num bestScore = null;
        for (LagCorrelationPoint point : points) {
            if (!point.isDefined()) {
                continue;
            }
            Num score = selectionScore(point.correlation(), selectionPolicy);
            if (bestScore == null || score.compareTo(bestScore) > 0) {
                bestScore = score;
            }
        }
        List<Integer> expectedBestLags = new ArrayList<>();
        if (bestScore != null) {
            for (LagCorrelationPoint point : points) {
                if (!point.isDefined()) {
                    continue;
                }
                if (selectionScore(point.correlation(), selectionPolicy).compareTo(bestScore) == 0) {
                    expectedBestLags.add(point.lag());
                }
            }
        }
        if (!bestLags.equals(expectedBestLags)) {
            throw new IllegalArgumentException("bestLags must contain exactly the maximal-scoring lags, ascending");
        }
        OptionalInt expectedSelectedLag = OptionalInt.empty();
        if (!expectedBestLags.isEmpty()) {
            int selected = expectedBestLags.get(0);
            for (int i = 1; i < expectedBestLags.size(); i++) {
                int candidate = expectedBestLags.get(i);
                // Long arithmetic keeps the tie-break overflow-safe at the
                // extremes of the int range.
                long candidateDistance = Math.abs((long) candidate);
                long selectedDistance = Math.abs((long) selected);
                if (candidateDistance < selectedDistance
                        || (candidateDistance == selectedDistance && candidate < selected)) {
                    selected = candidate;
                }
            }
            expectedSelectedLag = OptionalInt.of(selected);
        }
        if (!selectedLag.equals(expectedSelectedLag)) {
            throw new IllegalArgumentException("selectedLag must be the deterministic pick from bestLags");
        }
        if (selectedDefined) {
            Map<Integer, LagCorrelationPoint> pointsByLag = new HashMap<>(points.size());
            for (LagCorrelationPoint point : points) {
                pointsByLag.put(point.lag(), point);
            }
            LagCorrelationPoint selectedPoint = pointsByLag.get(selectedLag.getAsInt());
            if (selectedPoint == null || selectedPoint.correlation().compareTo(selectedCorrelation) != 0) {
                throw new IllegalArgumentException(
                        "selectedCorrelation must equal the correlation of the selected point");
            }
        }
    }

    private static Num selectionScore(Num correlation, LagSelectionPolicy selectionPolicy) {
        return selectionPolicy == LagSelectionPolicy.MAXIMUM_CORRELATION ? correlation : correlation.abs();
    }
}
