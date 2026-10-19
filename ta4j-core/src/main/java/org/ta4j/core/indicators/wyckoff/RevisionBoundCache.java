/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.wyckoff;

import java.util.Map;
import java.util.Objects;

import org.ta4j.core.BarSeries;

/**
 * Binds index-keyed indicator caches to the {@link BarSeries} revision journal.
 *
 * <p>
 * Indicator values cached by absolute bar index become stale after any series
 * mutation that alters or shifts bars (historical replacement, removal, or
 * rolling-window expiry). On each {@link #reconcile(Map[])} call the current
 * change snapshot is compared against the last observed one; stale entries are
 * evicted from the supplied caches so that callers recompute them on demand.
 *
 * @since 0.24.2
 */
final class RevisionBoundCache {

    private final BarSeries series;
    private BarSeries.BarSeriesChangeSnapshot observed;

    /**
     * Creates a reconciler for the provided series.
     *
     * @param series underlying bar series, must not be null
     */
    RevisionBoundCache(BarSeries series) {
        this.series = Objects.requireNonNull(series, "series");
    }

    /**
     * Evicts stale entries from the provided caches when the series revision,
     * retained window, or capacity changed since the last observation.
     *
     * @param caches index-keyed caches to reconcile
     * @return {@code true} when at least one entry was evicted
     */
    @SafeVarargs
    final synchronized boolean reconcile(Map<Integer, ?>... caches) {
        final BarSeries.BarSeriesChangeSnapshot snapshot = series
                .getBarSeriesChangeSnapshot(observed == null ? -1L : observed.revision());
        if (observed != null && sameSeriesState(snapshot, observed)) {
            return false;
        }
        boolean evicted = false;
        if (observed != null && snapshot.removedThroughIndex() != observed.removedThroughIndex()) {
            // Removed bars shift the meaning of every surviving index, so all
            // index-keyed entries are stale.
            for (Map<Integer, ?> cache : caches) {
                evicted |= !cache.isEmpty();
                cache.clear();
            }
        } else {
            final int invalidateFrom = snapshot.earliestChangedIndex();
            if (invalidateFrom >= 0) {
                for (Map<Integer, ?> cache : caches) {
                    evicted |= cache.keySet().removeIf(key -> key >= invalidateFrom);
                }
            }
        }
        observed = snapshot;
        return evicted;
    }

    private static boolean sameSeriesState(BarSeries.BarSeriesChangeSnapshot left,
            BarSeries.BarSeriesChangeSnapshot right) {
        return left.revision() == right.revision() && left.removedThroughIndex() == right.removedThroughIndex()
                && left.maximumBarCount() == right.maximumBarCount() && left.endIndex() == right.endIndex();
    }
}
