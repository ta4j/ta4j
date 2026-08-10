/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

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
        Integer previousLag = null;
        for (Integer lag : bestLags) {
            if (previousLag != null && lag < previousLag) {
                throw new IllegalArgumentException("bestLags must be ascending");
            }
            previousLag = lag;
        }
        boolean selectedDefined = selectedLag.isPresent();
        boolean correlationDefined = !selectedCorrelation.isNaN();
        if (selectedDefined != correlationDefined) {
            throw new IllegalArgumentException(
                    "selectedLag and selectedCorrelation must be defined together or both undefined");
        }
        if (selectedDefined && !bestLags.contains(selectedLag.getAsInt())) {
            throw new IllegalArgumentException("selectedLag must be one of bestLags");
        }
        if (!bestLags.isEmpty() || selectedDefined) {
            // One lookup pass keeps validation linear in the profile size; a
            // tie-heavy profile can carry up to MAX_PROFILE_LAGS entries.
            Map<Integer, LagCorrelationPoint> pointsByLag = new HashMap<>(points.size());
            for (LagCorrelationPoint point : points) {
                pointsByLag.put(point.lag(), point);
            }
            for (Integer lag : bestLags) {
                LagCorrelationPoint point = pointsByLag.get(lag);
                if (point == null || !point.isDefined()) {
                    throw new IllegalArgumentException("every best lag must map to a defined point");
                }
            }
            if (selectedDefined) {
                LagCorrelationPoint selectedPoint = pointsByLag.get(selectedLag.getAsInt());
                if (selectedPoint == null || selectedPoint.correlation().compareTo(selectedCorrelation) != 0) {
                    throw new IllegalArgumentException(
                            "selectedCorrelation must equal the correlation of the selected point");
                }
            }
        }
    }
}
