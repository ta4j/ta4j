/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.event;

import org.ta4j.core.BarSeries;

/**
 * Internal read-only view of a sparse Boolean event stream over one
 * {@link BarSeries}; not part of the public API.
 *
 * <p>
 * The public evaluator inputs are {@link org.ta4j.core.Indicator} instances and
 * an explicit predicate overload; this interface only normalizes those two
 * shapes for the shared extraction pass.
 *
 * <p>
 * An event occurs at bar index {@code i} when {@link #isEvent(int)} returns
 * {@code true}. Implementations must be deterministic: repeated calls with the
 * same index must return the same value, and results must not depend on
 * iteration order or parallel scheduling.
 *
 * <p>
 * {@link #getCountOfUnstableBars()} follows the ta4j indicator convention: it
 * is the first index at which events are trustworthy. Evaluators never read
 * events below that index (the event at {@code getCountOfUnstableBars()} is the
 * first one evaluated and must be valid), so implementations may return
 * arbitrary values for indexes strictly below it.
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
