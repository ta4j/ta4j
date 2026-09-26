/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics.event;

import org.ta4j.core.BarSeries;

/**
 * Internal read-only view of a sparse Boolean event stream over one
 * {@link BarSeries}; not part of the public API.
 *
 * <p>
 * The public API is {@link EventSynchronizationIndicator}, which takes two
 * {@code Indicator<Boolean>} instances; this interface normalizes those
 * indicators (and the internal predicate shape used by tests and benchmarks)
 * for the shared extraction pass.
 *
 * <p>
 * An event occurs at bar index {@code i} when {@link #isEvent(int)} returns
 * {@code true}. Implementations must be deterministic: repeated calls with the
 * same index must return the same value, and results must not depend on
 * iteration order or parallel scheduling.
 *
 * <p>
 * {@link #getCountOfUnstableBars()} follows the ta4j indicator convention: it
 * is the number of leading bars, counted from the series' retained head, whose
 * event values are not trustworthy. Evaluators never read events below
 * {@code getBarSeries().getBeginIndex() + getCountOfUnstableBars()}, so
 * implementations may return arbitrary values for indexes strictly below that
 * boundary.
 *
 * @see EventSignals
 */
interface EventSignal {

    /**
     * @return the bar series this signal is defined over
     */
    BarSeries getBarSeries();

    /**
     * Returns the number of leading bars whose event values are not trustworthy.
     *
     * <p>
     * The effective evaluation range always starts at or after this boundary,
     * mirroring the unstable-bar contract of ta4j indicators.
     *
     * @return the count of unstable bars, always {@code >= 0}
     */
    int getCountOfUnstableBars();

    /**
     * @param index the bar index
     * @return {@code true} if an event occurs at {@code index}
     */
    boolean isEvent(int index);
}
