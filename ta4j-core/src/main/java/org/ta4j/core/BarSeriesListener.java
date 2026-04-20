/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

/**
 * Listener for {@link BarSeries} change events. Enables push-based
 * (producer/subscriber) updates so indicators can pre-compute values
 * incrementally when new bars arrive.
 *
 * @since 0.22.7
 * @see BarSeries#addListener(BarSeriesListener)
 */
public interface BarSeriesListener {

    /**
     * Called after a new bar has been appended to the series.
     *
     * @param index the index of the newly added bar
     * @param bar   the bar that was added
     * @since 0.22.7
     */
    void onBarAdded(int index, Bar bar);

    /**
     * Called after the last bar has been replaced (e.g. live tick update).
     *
     * @param index the index of the replaced bar
     * @param bar   the replacement bar
     * @since 0.22.7
     */
    default void onBarReplaced(int index, Bar bar) {
        onBarAdded(index, bar);
    }
}
