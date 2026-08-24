/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.analysis.elliott.swing.SwingDetector;
import org.ta4j.core.analysis.elliott.swing.SwingPivot;

/**
 * Causal observer that adapts any degree-free {@link SwingDetector} into a
 * {@link PivotHistory} with explicit confirmation provenance.
 *
 * <p>
 * The tracker replays the detector over ascending as-of indices and records for
 * every pivot the first index at which the detector reported it. Detector
 * revisions are handled deterministically:
 * <ul>
 * <li>a trailing pivot that disappears is dropped while no successor has been
 * confirmed;</li>
 * <li>a trailing pivot whose price or type is revised is replaced and its
 * confirmation index moves to the revision bar;</li>
 * <li>any contradiction of a frozen non-trailing pivot fails closed.</li>
 * </ul>
 *
 * <p>
 * The Elliott layer never changes detector sensitivity or rescans bars to
 * rescue a count.
 */
final class ConfirmationTracker {

    private final SwingDetector detector;

    ConfirmationTracker(final SwingDetector detector) {
        this.detector = Objects.requireNonNull(detector, "detector");
    }

    /**
     * Observes the full series and produces the frozen confirmed-pivot history.
     *
     * @param series series to observe
     * @return normalized history with confirmation provenance
     */
    PivotHistory observe(final BarSeries series) {
        Objects.requireNonNull(series, "series");
        final Map<Integer, ConfirmedPivot> known = new HashMap<>();
        final List<ConfirmedPivot> order = new ArrayList<>();
        final int begin = Math.max(series.getBeginIndex(), 0);
        final int end = series.getEndIndex();
        for (int asOf = begin; asOf <= end; asOf++) {
            reconcile(order, known, detector.detectPivots(series, asOf), asOf);
        }
        return PivotHistory.of(order);
    }

    private void reconcile(final List<ConfirmedPivot> order, final Map<Integer, ConfirmedPivot> known,
            final List<SwingPivot> reported, final int asOf) {
        while (!order.isEmpty() && !containsIndex(reported, order.get(order.size() - 1).pivotIndex())) {
            final ConfirmedPivot removed = order.remove(order.size() - 1);
            known.remove(removed.pivotIndex());
        }
        for (final SwingPivot pivot : reported) {
            final ConfirmedPivot existing = known.get(pivot.index());
            if (existing != null) {
                if (existing.type() == pivot.type() && existing.price().compareTo(pivot.price()) == 0) {
                    continue;
                }
                if (!order.isEmpty() && order.get(order.size() - 1).pivotIndex() == existing.pivotIndex()) {
                    known.put(pivot.index(), new ConfirmedPivot(pivot.index(), asOf, pivot.price(), pivot.type()));
                    order.set(order.size() - 1, known.get(pivot.index()));
                    continue;
                }
                throw new IllegalStateException("detector contradicted frozen pivot history at index " + pivot.index());
            }
            final ConfirmedPivot confirmed = new ConfirmedPivot(pivot.index(), asOf, pivot.price(), pivot.type());
            known.put(pivot.index(), confirmed);
            order.add(confirmed);
        }
    }

    private boolean containsIndex(final List<SwingPivot> reported, final int pivotIndex) {
        for (final SwingPivot candidate : reported) {
            if (candidate.index() == pivotIndex) {
                return true;
            }
        }
        return false;
    }
}
