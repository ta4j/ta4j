/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.ta4j.core.BarSeries;
import org.ta4j.core.analysis.elliott.swing.SwingDetector;

/**
 * Memoizes causal detector replays of one real series so every consumer of the
 * same detector configuration shares a single replay.
 *
 * <p>
 * A replay is keyed by the identity of its detector supplier and is only shared
 * for the series and end index the cache is bound to; any other series (for
 * example a null-ensemble member) or end index is replayed fresh and never
 * stored. The scale-relation study draws its per-scale tapes from the same
 * cache the study runner used for its modes, so a hierarchy never re-detects a
 * scale the study already observed.
 * </p>
 */
final class DetectorReplays {

    private static final DetectorReplays UNCACHED = new DetectorReplays(null, -1);

    private final BarSeries series;
    private final int end;
    private final Map<Supplier<SwingDetector>, ConfirmationTracker.CausalReplay> replays = new IdentityHashMap<>();
    private int computed;

    private DetectorReplays(final BarSeries series, final int end) {
        this.series = series;
        this.end = end;
    }

    /** @return an instance that never shares replays */
    static DetectorReplays uncached() {
        return UNCACHED;
    }

    /**
     * Binds a cache to one series and causal end index.
     *
     * @param series series every shared replay observes
     * @param end    last observed bar of every shared replay
     * @return the cache
     */
    static DetectorReplays forSeries(final BarSeries series, final int end) {
        return new DetectorReplays(Objects.requireNonNull(series, "series"), end);
    }

    /**
     * Returns the causal replay of {@code factory}'s detector over {@code series}
     * up to {@code endIndex}, reusing a prior identical replay when the cache is
     * bound to that series and end.
     *
     * @param target   series to observe
     * @param factory  detector supplier
     * @param endIndex last observed bar, inclusive
     * @return causal replay
     */
    ConfirmationTracker.CausalReplay replay(final BarSeries target, final Supplier<SwingDetector> factory,
            final int endIndex) {
        Objects.requireNonNull(factory, "detectorFactory");
        if (series == null || target != series || endIndex != end) {
            return compute(target, factory, endIndex);
        }
        final ConfirmationTracker.CausalReplay shared = replays.get(factory);
        if (shared != null) {
            return shared;
        }
        final ConfirmationTracker.CausalReplay fresh = compute(target, factory, endIndex);
        replays.put(factory, fresh);
        return fresh;
    }

    /** @return number of replays actually computed by this instance */
    int computedCount() {
        return computed;
    }

    private ConfirmationTracker.CausalReplay compute(final BarSeries target, final Supplier<SwingDetector> factory,
            final int endIndex) {
        final SwingDetector detector = Objects.requireNonNull(factory.get(), "detectorFactory returned null");
        if (this != UNCACHED) {
            computed++;
        }
        // Causally truncate: a detector contradiction on a bar beyond the
        // requested range must not abort a report about an earlier interval.
        return new ConfirmationTracker(detector).observeReplay(target, endIndex);
    }
}
