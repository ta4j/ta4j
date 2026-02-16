/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.swing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.num.Num;

/**
 * Package-private helpers for normalizing swing pivots and sequences.
 *
 * <p>
 * This utility centralizes pivot normalization so detectors produce consistent
 * swing lists regardless of input order or duplicate pivots.
 */
final class SwingDetectorSupport {

    private SwingDetectorSupport() {
    }

    static List<SwingPivot> pivotsFromSwings(final List<ElliottSwing> swings) {
        if (swings == null || swings.isEmpty()) {
            return List.of();
        }
        final List<SwingPivot> pivots = new ArrayList<>(swings.size() + 1);
        final ElliottSwing first = swings.get(0);
        final SwingPivotType firstType = first.isRising() ? SwingPivotType.LOW : SwingPivotType.HIGH;
        pivots.add(new SwingPivot(first.fromIndex(), first.fromPrice(), firstType));
        for (final ElliottSwing swing : swings) {
            if (swing == null) {
                continue;
            }
            final SwingPivotType type = swing.isRising() ? SwingPivotType.HIGH : SwingPivotType.LOW;
            pivots.add(new SwingPivot(swing.toIndex(), swing.toPrice(), type));
        }
        return normalizePivots(pivots);
    }

    static List<ElliottSwing> swingsFromPivots(final List<SwingPivot> pivots, final ElliottDegree degree) {
        if (pivots == null || pivots.size() < 2) {
            return List.of();
        }
        final List<ElliottSwing> swings = new ArrayList<>(pivots.size() - 1);
        for (int i = 1; i < pivots.size(); i++) {
            final SwingPivot previous = pivots.get(i - 1);
            final SwingPivot current = pivots.get(i);
            swings.add(new ElliottSwing(previous.index(), current.index(), previous.price(), current.price(), degree));
        }
        return List.copyOf(swings);
    }

    static List<SwingPivot> normalizePivots(final List<SwingPivot> pivots) {
        if (pivots == null || pivots.isEmpty()) {
            return List.of();
        }
        final List<SwingPivot> sorted = new ArrayList<>(pivots.stream().filter(pivot -> pivot != null).toList());
        sorted.sort(Comparator.comparingInt(SwingPivot::index));
        if (sorted.isEmpty()) {
            return List.of();
        }

        final List<SwingPivot> normalized = new ArrayList<>(sorted.size());
        for (final SwingPivot pivot : sorted) {
            if (normalized.isEmpty()) {
                normalized.add(pivot);
                continue;
            }
            final SwingPivot last = normalized.get(normalized.size() - 1);
            if (last.type() == pivot.type()) {
                normalized.set(normalized.size() - 1, chooseMoreExtreme(last, pivot));
            } else {
                normalized.add(pivot);
            }
        }
        return List.copyOf(normalized);
    }

    private static SwingPivot chooseMoreExtreme(final SwingPivot first, final SwingPivot second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        final Num firstPrice = first.price();
        final Num secondPrice = second.price();
        if (Num.isNaNOrNull(firstPrice)) {
            return second;
        }
        if (Num.isNaNOrNull(secondPrice)) {
            return first;
        }
        if (first.type() == SwingPivotType.HIGH) {
            if (secondPrice.isGreaterThan(firstPrice)) {
                return second;
            }
        } else {
            if (secondPrice.isLessThan(firstPrice)) {
                return second;
            }
        }
        return first;
    }
}
