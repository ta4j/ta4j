/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable, degree-free confirmed-pivot history with deterministic
 * normalization.
 *
 * <p>
 * Normalization policy (deterministic, fail-closed):
 * <ul>
 * <li>pivot indices must strictly increase;</li>
 * <li>consecutive same-type pivots collapse to the more extreme price; equal
 * extremes keep the later pivot;</li>
 * <li>the surviving pivot keeps its own confirmation provenance;</li>
 * <li>a collapsed history must alternate types or construction fails.</li>
 * </ul>
 *
 * <p>
 * As-of views truncate to pivots whose confirmation index is at or before the
 * requested bar so no future confirmation leaks backward into an earlier state.
 */
final class PivotHistory {

    private final List<ConfirmedPivot> pivots;

    private PivotHistory(final List<ConfirmedPivot> pivots) {
        this.pivots = List.copyOf(pivots);
    }

    /**
     * Creates a normalized history from raw tracked pivots.
     *
     * @param raw ordered pivots as tracked by the confirmation observer
     * @return normalized immutable history
     * @throws IllegalArgumentException on non-increasing indices
     * @throws IllegalStateException    on contradictory histories that cannot
     *                                  normalize to an alternating sequence
     */
    static PivotHistory of(final List<ConfirmedPivot> raw) {
        Objects.requireNonNull(raw, "raw");
        final List<ConfirmedPivot> normalized = new ArrayList<>(raw.size());
        for (final ConfirmedPivot pivot : raw) {
            if (!normalized.isEmpty()) {
                final ConfirmedPivot previous = normalized.get(normalized.size() - 1);
                if (pivot.pivotIndex() <= previous.pivotIndex()) {
                    throw new IllegalArgumentException("pivot indices must strictly increase");
                }
                if (pivot.type() == previous.type()) {
                    normalized.set(normalized.size() - 1, chooseMoreExtreme(previous, pivot));
                    continue;
                }
            }
            normalized.add(pivot);
        }
        return new PivotHistory(normalized);
    }

    private static ConfirmedPivot chooseMoreExtreme(final ConfirmedPivot first, final ConfirmedPivot second) {
        final boolean secondMoreExtreme = switch (second.type()) {
        case HIGH -> second.price().isGreaterThan(first.price());
        case LOW -> second.price().isLessThan(first.price());
        };
        if (secondMoreExtreme) {
            return second;
        }
        final boolean firstMoreExtreme = switch (second.type()) {
        case HIGH -> first.price().isGreaterThan(second.price());
        case LOW -> first.price().isLessThan(second.price());
        };
        // Equal extremes keep the later pivot deterministically.
        return firstMoreExtreme ? first : second;
    }

    /**
     * @return the normalized confirmed pivots
     */
    List<ConfirmedPivot> pivots() {
        return pivots;
    }

    /**
     * @return number of retained pivots
     */
    int size() {
        return pivots.size();
    }

    /**
     * Returns the causal view of this history as of a bar index.
     *
     * @param barIndex bar index of the observation point
     * @return pivots confirmed at or before {@code barIndex}
     */
    List<ConfirmedPivot> asOf(final int barIndex) {
        // Confirmation times need not be monotonic in pivot index: a detector
        // may re-admit an older pivot after a newer one was already confirmed,
        // so a prefix scan would stop at the first not-yet-visible entry and
        // hide later entries that were already confirmed. Filter every entry
        // instead and restore ascending pivot-index order for the view.
        final List<ConfirmedPivot> visible = new ArrayList<>();
        for (final ConfirmedPivot pivot : pivots) {
            if (pivot.confirmedAt(barIndex)) {
                visible.add(pivot);
            }
        }
        if (visible.isEmpty()) {
            return List.of();
        }
        // Hiding an unconfirmed interior pivot can leave consecutive visible
        // pivots with the same type, which violates the normalized alternating
        // contract; re-run the same normalization the emitted history uses.
        return PivotHistory.of(visible).pivots();
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof PivotHistory history && pivots.equals(history.pivots);
    }

    @Override
    public int hashCode() {
        return pivots.hashCode();
    }
}
