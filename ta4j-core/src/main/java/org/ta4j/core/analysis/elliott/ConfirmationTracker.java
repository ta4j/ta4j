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
     * Known limitation: swing-backed detectors only expose pivots once a swing pair
     * exists, so a history's first pivot is confirmed when the second pivot appears
     * and its confirmation lag is overstated by that pairing delay. A pivot-level
     * detector view would remove the bias but would fork detection from the
     * indicator-backed ground truth the study's emitted views use, so it stays out
     * of scope here.
     *
     * @param series series to observe
     * @return final normalized history plus the per-bar causal replay
     */
    CausalReplay observeReplay(final BarSeries series) {
        return observeReplay(series, series.getEndIndex());
    }

    /**
     * As {@link #observeReplay(BarSeries)}, but stops observing at the requested
     * bar so a contradiction the detector only produces after {@code endIndex}
     * cannot abort an evaluation of an earlier interval.
     *
     * @param series   series to observe
     * @param endIndex last bar index to observe, inclusive
     * @return final normalized history plus the per-bar causal replay
     */
    CausalReplay observeReplay(final BarSeries series, final int endIndex) {
        Objects.requireNonNull(series, "series");
        final Map<Integer, ConfirmedPivot> known = new HashMap<>();
        // Indices normalized away by snapshot collapse mapped to the pivot
        // that dominates them. Cumulative detectors keep reporting dominated
        // pivots; they stay suppressed only while their dominator is still
        // tracked, and are reconsidered once it is withdrawn.
        final Map<Integer, Integer> collapsed = new HashMap<>();
        final List<ConfirmedPivot> order = new ArrayList<>();
        final int begin = Math.max(series.getBeginIndex(), 0);
        final int end = Math.min(series.getEndIndex(), endIndex);
        final List<Integer> versionAsOf = new ArrayList<>();
        final List<List<ConfirmedPivot>> versions = new ArrayList<>();
        List<SwingPivot> previousReported = null;
        if (begin <= end) {
            for (int asOf = begin;; asOf++) {
                final List<SwingPivot> reported = detector.detectPivots(series, asOf);
                if (reported != previousReported) {
                    final boolean changed = reconcile(order, known, reported, asOf, collapsed);
                    if (changed) {
                        versions.add(List.copyOf(PivotHistory.of(order).pivots()));
                        versionAsOf.add(asOf);
                    }
                    previousReported = reported;
                }
                if (asOf == end) {
                    break;
                }
            }
        }
        return new CausalReplay(PivotHistory.of(order), versions,
                versionAsOf.stream().mapToInt(Integer::intValue).toArray());
    }

    private boolean reconcile(final List<ConfirmedPivot> order, final Map<Integer, ConfirmedPivot> known,
            final List<SwingPivot> reported, final int asOf, final Map<Integer, Integer> collapsed) {
        boolean changed = false;
        // Membership lookups against the freshly reported list happen once per
        // tracked pivot per replay bar; scanning the list linearly for each of
        // them made unchanged reconciliation quadratic in the pivot history.
        // One update must also be internally consistent: two entries sharing an
        // index with differing type or price are contradictory, and admitting
        // the first while silently skipping the second would record partial
        // history. Identical restatements of the same pivot stay tolerated.
        final Map<Integer, SwingPivot> reportedByIndex = new HashMap<>(reported.size() * 2);
        for (final SwingPivot pivot : reported) {
            final SwingPivot previous = reportedByIndex.put(pivot.index(), pivot);
            if (previous != null
                    && (previous.type() != pivot.type() || previous.price().compareTo(pivot.price()) != 0)) {
                throw new IllegalStateException(
                        "detector reported contradictory pivots at index " + pivot.index() + " at bar " + asOf);
            }
        }
        final Set<Integer> reportedIndices = reportedByIndex.keySet();
        // Only the newest tracked pivot is retractable: everything before it
        // was already frozen by that pivot's confirmation, so a detector
        // withdrawing several reported pivots in one update rewrites frozen
        // history and must fail loud instead of being popped successively.
        final int retractableIndex = order.isEmpty() ? -1 : order.get(order.size() - 1).pivotIndex();
        // Capture frozen pivots before any trailing withdrawal. If the
        // retractable tail is withdrawn, its predecessor remains frozen by
        // that tail's earlier confirmation and must not be normalized away
        // by admission of a replacement report.
        final Set<Integer> frozenBeforeAdmission = new HashSet<>();
        for (int index = 0; index + 1 < order.size(); index++) {
            frozenBeforeAdmission.add(order.get(index).pivotIndex());
        }
        while (!order.isEmpty() && !reportedIndices.contains(order.get(order.size() - 1).pivotIndex())) {
            final ConfirmedPivot removed = order.remove(order.size() - 1);
            if (retractableIndex != -1 && removed.pivotIndex() != retractableIndex) {
                throw new IllegalStateException("detector withdrew frozen pivot " + removed.pivotIndex() + " at bar "
                        + asOf + "; only the newest tracked pivot may be withdrawn");
            }
            known.remove(removed.pivotIndex());
            // A withdrawn dominator no longer suppresses the pivots it
            // dominated: if the detector still reports them, they must be
            // reconsidered instead of staying invisible forever.
            collapsed.values().removeIf(dominator -> dominator.equals(removed.pivotIndex()));
            changed = true;
        }
        // First settle revisions of already-tracked pivots so dominance is
        // judged against today's prices; then admit suppressed or fresh
        // reports against those settled prices.
        for (final SwingPivot pivot : reported) {
            final ConfirmedPivot existing = known.get(pivot.index());
            if (existing == null) {
                continue;
            }
            if (existing.type() == pivot.type() && existing.price().compareTo(pivot.price()) == 0) {
                continue;
            }
            if (retractableIndex != -1 && retractableIndex == existing.pivotIndex()) {
                final ConfirmedPivot revised = new ConfirmedPivot(pivot.index(), asOf, pivot.price(), pivot.type());
                // A retyped trailing pivot can normalize away its same-type
                // frozen predecessor; that predecessor was already frozen by
                // this pivot's original confirmation, so the revision would
                // silently rewrite confirmed history. Project the change first
                // and fail closed when anything other than the revised pivot
                // disappears.
                final List<ConfirmedPivot> projected = new ArrayList<>(order);
                projected.set(projected.size() - 1, revised);
                final Set<Integer> keptBefore = new HashSet<>();
                for (final ConfirmedPivot tracked : order) {
                    keptBefore.add(tracked.pivotIndex());
                }
                final Set<Integer> keptAfter = new HashSet<>();
                for (final ConfirmedPivot normalizedPivot : PivotHistory.of(projected).pivots()) {
                    keptAfter.add(normalizedPivot.pivotIndex());
                }
                for (final Integer index : keptBefore) {
                    if (index.intValue() != revised.pivotIndex() && !keptAfter.contains(index)) {
                        throw new IllegalStateException("detector revision at bar " + asOf
                                + " would normalize away frozen pivot " + index + "; rejecting retyped trailing pivot");
                    }
                }
                known.put(pivot.index(), revised);
                order.set(order.size() - 1, revised);
                changed = true;
                continue;
            }
            throw new IllegalStateException("detector contradicted frozen pivot history at index " + pivot.index());
        }
        for (final SwingPivot pivot : reported) {
            if (known.containsKey(pivot.index())) {
                continue;
            }
            final Integer dominatorIndex = collapsed.get(pivot.index());
            if (dominatorIndex != null) {
                final ConfirmedPivot dominator = known.get(dominatorIndex);
                // Suppression survives only while the dominator still
                // dominates today: a revision can hand dominance back, and a
                // retyped dominator never dominated this type at all.
                if (dominator != null && dominates(dominator, pivot)) {
                    continue;
                }
            }
            final ConfirmedPivot confirmed = new ConfirmedPivot(pivot.index(), asOf, pivot.price(), pivot.type());
            known.put(pivot.index(), confirmed);
            // A reconsidered dominated pivot can be older than pivots already
            // tracked; keep the order index-ascending so snapshot collapse
            // sees the same chronological shape the detector reports.
            int at = order.size();
            while (at > 0 && order.get(at - 1).pivotIndex() > pivot.index()) {
                at--;
            }
            order.add(at, confirmed);
            changed = true;
        }
        // A reconsidered collapsed pivot can out-dominate the pivot that used
        // to suppress it; unconditional snapshot collapse would then drop that
        // former dominator even though a later confirmation already froze it.
        // Project the normalization first and fail closed when anything
        // tracked before this update would disappear -- only pivots admitted
        // at this bar may collapse away.
        final List<ConfirmedPivot> projectedOrder = new ArrayList<>(order);
        final Map<Integer, ConfirmedPivot> projectedKnown = new HashMap<>(known);
        final Map<Integer, Integer> projectedCollapsed = new HashMap<>();
        normalizeOrder(projectedOrder, projectedKnown, projectedCollapsed);
        final Set<Integer> keptAfterNormalization = new HashSet<>();
        for (final ConfirmedPivot kept : projectedOrder) {
            keptAfterNormalization.add(kept.pivotIndex());
        }
        for (final Integer index : frozenBeforeAdmission) {
            if (!keptAfterNormalization.contains(index)) {
                throw new IllegalStateException("detector admission at bar " + asOf
                        + " would normalize away frozen pivot " + index + "; rejecting collapsed-pivot readmission");
            }
        }
        if (normalizeOrder(order, known, collapsed)) {
            changed = true;
        }
        for (int i = 0; i < order.size(); i++) {
            if (!reportedIndices.contains(order.get(i).pivotIndex())) {
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
     * @param collapsedSink map recording each index removed by collapse and the
     *                      pivot that dominates it, so later reports of those
     *                      dominated pivots are ignored while their dominator stays
     *                      tracked
     * @return true if the order changed through normalization
     */
    private static boolean normalizeOrder(final List<ConfirmedPivot> order, final Map<Integer, ConfirmedPivot> known,
            final Map<Integer, Integer> collapsedSink) {
        if (order.size() < 2) {
            return false;
        }
        final List<ConfirmedPivot> normalized = PivotHistory.of(order).pivots();
        if (normalized.size() == order.size()) {
            return false;
        }
        final Set<Integer> kept = new HashSet<>();
        for (final ConfirmedPivot pivot : normalized) {
            kept.add(pivot.pivotIndex());
        }
        // Derive explicit dominance by walking the original order against the
        // normalized snapshot: every dropped pivot belongs to a same-type run
        // whose representative is the nearest kept neighbor in that run.
        int keptCursor = 0;
        for (final ConfirmedPivot pivot : order) {
            if (keptCursor < normalized.size() && normalized.get(keptCursor).pivotIndex() == pivot.pivotIndex()) {
                keptCursor++;
            } else {
                final ConfirmedPivot dominator = keptCursor < normalized.size()
                        && normalized.get(keptCursor).type() == pivot.type() ? normalized.get(keptCursor)
                                : normalized.get(Math.max(0, keptCursor - 1));
                collapsedSink.put(pivot.pivotIndex(), dominator.pivotIndex());
            }
        }
        collapsedSink.values().removeIf(dominator -> !kept.contains(dominator));
        known.keySet().removeIf(index -> !kept.contains(index));
        order.clear();
        order.addAll(normalized);
        return true;
    }

    private static boolean dominates(final ConfirmedPivot dominator, final SwingPivot dominated) {
        if (dominator.type() != dominated.type()) {
            return false;
        }
        // Normalization keeps the LATER pivot of an equal-price same-type run,
        // so equality counts as dominance: otherwise cumulative detectors
        // re-admitting the retained equal pivot would collapse it again on
        // every replay bar, defeating change-proportional snapshotting.
        return switch (dominator.type()) {
        case HIGH -> dominated.price().compareTo(dominator.price()) <= 0;
        case LOW -> dominated.price().compareTo(dominator.price()) >= 0;
        };
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
         * Returns a defensive copy so callers cannot corrupt the ordering that
         * {@link #at(int)} binary search relies on.
         */
        @Override
        public int[] versionAsOf() {
            return versionAsOf.clone();
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
