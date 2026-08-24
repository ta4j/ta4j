/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
     * <p>
     * The returned history reflects the final reconciled state only. Consumers that
     * must evaluate earlier bars causally use {@link #observeReplay(BarSeries)} so
     * later withdrawals cannot rewrite earlier views.
     *
     * @param series series to observe
     * @return normalized history with confirmation provenance
     */
    PivotHistory observe(final BarSeries series) {
        return observeReplay(series).history();
    }

    /**
     * Observes the full series and additionally records every intermediate
     * confirmed-pivot state so any past bar can be replayed exactly as it was known
     * at that bar.
     *
     * <p>
     * A snapshot is stored whenever the tracked order changes; unchanged bars share
     * the previous version, keeping memory proportional to the number of state
     * changes rather than the number of bars.
     *
     * <p>
     * Known limitation: swing-backed detectors only expose pivots once a swing
     * pair exists, so a history's first pivot is confirmed when the second
     * pivot appears and its confirmation lag is overstated by that pairing
     * delay. A pivot-level detector view would remove the bias but would fork
     * detection from the indicator-backed ground truth the study's emitted
     * views use, so it stays out of scope here.
     *
     * @param series series to observe
     * @return final normalized history plus the per-bar causal replay
     */
    CausalReplay observeReplay(final BarSeries series) {
        Objects.requireNonNull(series, "series");
        final Map<Integer, ConfirmedPivot> known = new HashMap<>();
        // Indices normalized away by snapshot collapse. Cumulative detectors
        // keep reporting them; they must never re-enter the tracked order.
        final Set<Integer> collapsed = new HashSet<>();
        final List<ConfirmedPivot> order = new ArrayList<>();
        final int begin = Math.max(series.getBeginIndex(), 0);
        final int end = series.getEndIndex();
        final List<Integer> versionAsOf = new ArrayList<>();
        final List<List<ConfirmedPivot>> versions = new ArrayList<>();
        for (int asOf = begin; asOf <= end; asOf++) {
            final boolean changed = reconcile(order, known, detector.detectPivots(series, asOf), asOf, collapsed);
            if (changed) {
                versions.add(List.copyOf(PivotHistory.of(order).pivots()));
                versionAsOf.add(asOf);
            }
        }
        return new CausalReplay(PivotHistory.of(order), versions,
                versionAsOf.stream().mapToInt(Integer::intValue).toArray());
    }

    private boolean reconcile(final List<ConfirmedPivot> order, final Map<Integer, ConfirmedPivot> known,
            final List<SwingPivot> reported, final int asOf, final Set<Integer> collapsed) {
        boolean changed = false;
        while (!order.isEmpty() && !containsIndex(reported, order.get(order.size() - 1).pivotIndex())) {
            final ConfirmedPivot removed = order.remove(order.size() - 1);
            known.remove(removed.pivotIndex());
            changed = true;
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
                    changed = true;
                    continue;
                }
                throw new IllegalStateException("detector contradicted frozen pivot history at index " + pivot.index());
            }
            if (collapsed.contains(pivot.index())) {
                continue;
            }
            final ConfirmedPivot confirmed = new ConfirmedPivot(pivot.index(), asOf, pivot.price(), pivot.type());
            known.put(pivot.index(), confirmed);
            order.add(confirmed);
            changed = true;
        }
        if (normalizeOrder(order, known, collapsed)) {
            changed = true;
        }
        for (int i = 0; i < order.size(); i++) {
            if (!containsIndex(reported, order.get(i).pivotIndex())) {
                throw new IllegalStateException("detector withdrew non-trailing pivot " + order.get(i).pivotIndex()
                        + " at bar " + asOf + "; frozen histories must never lose interior pivots");
            }
        }
        return changed;
    }

    /**
     * Collapses consecutive same-type pivots in the tracked order to the more
     * extreme one, mirroring the normalization applied to emitted snapshots.
     * Without this, a detector withdrawing a pivot that is dominated under snapshot
     * normalization would be misread as a frozen-history violation even though no
     * emitted view ever contained it.
     *
     * @param collapsedSink sink recording indices removed by collapse so later
     *                      reports of those dominated pivots are ignored
     * @return true if the order changed through normalization
     */
    private static boolean normalizeOrder(final List<ConfirmedPivot> order, final Map<Integer, ConfirmedPivot> known,
            final Set<Integer> collapsedSink) {
        if (order.size() < 2) {
            return false;
        }
        final List<ConfirmedPivot> collapsed = PivotHistory.of(order).pivots();
        if (collapsed.size() == order.size()) {
            return false;
        }
        final Set<Integer> kept = new HashSet<>();
        for (final ConfirmedPivot pivot : collapsed) {
            kept.add(pivot.pivotIndex());
        }
        for (final Integer index : known.keySet()) {
            if (!kept.contains(index)) {
                collapsedSink.add(index);
            }
        }
        known.keySet().removeIf(index -> !kept.contains(index));
        order.clear();
        order.addAll(collapsed);
        return true;
    }

    private boolean containsIndex(final List<SwingPivot> reported, final int pivotIndex) {
        for (final SwingPivot candidate : reported) {
            if (candidate.index() == pivotIndex) {
                return true;
            }
        }
        return false;
    }

    /**
     * Final frozen history plus the exact per-bar pivot states observed while the
     * series was replayed.
     */
    record CausalReplay(PivotHistory history, List<List<ConfirmedPivot>> versions, int[] versionAsOf) {
        CausalReplay {
            Objects.requireNonNull(history, "history");
            versions = versions == null ? List.of() : List.copyOf(versions);
            versionAsOf = versionAsOf == null ? new int[0] : versionAsOf.clone();
        }

        /**
         * Returns the confirmed pivots exactly as known at {@code asOfIndex}.
         *
         * @param asOfIndex bar index to replay
         * @return immutable pivot list visible at that bar
         */
        List<ConfirmedPivot> at(final int asOfIndex) {
            int low = 0;
            int high = versionAsOf.length - 1;
            int found = -1;
            while (low <= high) {
                final int mid = (low + high) >>> 1;
                if (versionAsOf[mid] <= asOfIndex) {
                    found = mid;
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }
            return found < 0 ? List.of() : versions.get(found);
        }
    }
}
