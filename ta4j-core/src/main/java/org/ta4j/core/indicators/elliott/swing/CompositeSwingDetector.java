/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.swing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.num.Num;

/**
 * Combines multiple swing detectors with AND/OR pivot agreement.
 *
 * @since 0.22.2
 */
public final class CompositeSwingDetector implements SwingDetector {

    /**
     * Composite policies for pivot agreement.
     *
     * @since 0.22.2
     */
    public enum Policy {
        AND, OR
    }

    private final List<SwingDetector> detectors;
    private final Policy policy;

    /**
     * Creates a composite detector.
     *
     * @param policy    agreement policy
     * @param detectors detectors to combine
     * @since 0.22.2
     */
    public CompositeSwingDetector(final Policy policy, final List<SwingDetector> detectors) {
        this.policy = Objects.requireNonNull(policy, "policy");
        if (detectors == null || detectors.isEmpty()) {
            throw new IllegalArgumentException("detectors cannot be null or empty");
        }
        this.detectors = List.copyOf(detectors);
    }

    @Override
    public SwingDetectorResult detect(final BarSeries series, final int index, final ElliottDegree degree) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(degree, "degree");
        if (series.isEmpty()) {
            return new SwingDetectorResult(List.of(), List.of());
        }
        final List<List<SwingPivot>> pivotSets = new ArrayList<>(detectors.size());
        for (final SwingDetector detector : detectors) {
            final SwingDetectorResult result = detector.detect(series, index, degree);
            pivotSets.add(result.pivots());
        }
        final List<SwingPivot> merged = mergePivots(pivotSets, policy);
        return SwingDetectorResult.fromPivots(merged, degree);
    }

    private List<SwingPivot> mergePivots(final List<List<SwingPivot>> pivotSets, final Policy policy) {
        final Map<PivotKey, PivotAccumulator> accumulatorMap = new HashMap<>();
        final int requiredCount = policy == Policy.AND ? pivotSets.size() : 1;

        for (final List<SwingPivot> pivots : pivotSets) {
            if (pivots == null || pivots.isEmpty()) {
                continue;
            }
            for (final SwingPivot pivot : pivots) {
                if (pivot == null) {
                    continue;
                }
                final PivotKey key = new PivotKey(pivot.index(), pivot.type());
                final PivotAccumulator accumulator = accumulatorMap.computeIfAbsent(key,
                        unused -> new PivotAccumulator(pivot.type()));
                accumulator.count++;
                accumulator.update(pivot.price());
            }
        }

        if (accumulatorMap.isEmpty()) {
            return List.of();
        }

        final List<SwingPivot> merged = new ArrayList<>();
        for (final Map.Entry<PivotKey, PivotAccumulator> entry : accumulatorMap.entrySet()) {
            final PivotAccumulator accumulator = entry.getValue();
            if (accumulator.count < requiredCount) {
                continue;
            }
            merged.add(new SwingPivot(entry.getKey().index(), accumulator.price, accumulator.type));
        }

        return SwingDetectorSupport.normalizePivots(merged);
    }

    private record PivotKey(int index, SwingPivotType type) {
    }

    private static final class PivotAccumulator {
        private final SwingPivotType type;
        private Num price;
        private int count;

        private PivotAccumulator(final SwingPivotType type) {
            this.type = type;
        }

        private void update(final Num candidate) {
            if (candidate == null || Num.isNaNOrNull(candidate)) {
                return;
            }
            if (price == null) {
                price = candidate;
                return;
            }
            if (type == SwingPivotType.HIGH) {
                if (candidate.isGreaterThan(price)) {
                    price = candidate;
                }
            } else {
                if (candidate.isLessThan(price)) {
                    price = candidate;
                }
            }
        }
    }
}
