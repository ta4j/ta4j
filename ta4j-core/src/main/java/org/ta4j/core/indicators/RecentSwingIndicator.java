/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import java.util.List;

/**
 * Interface for indicators that identify the most recently confirmed swing
 * point (either a swing high or swing low).
 * <p>
 * A swing point is a price point that is higher or lower than the surrounding
 * price points. Different implementations may use different algorithms to
 * identify swing points (e.g., fractal-based window detection, ZigZag pattern
 * detection, bounded prominence, slope changes, or detector consensus).
 * <p>
 * Implementations should clearly indicate whether they detect swing highs or
 * swing lows through their class names (e.g.,
 * {@code RecentFractalSwingHighIndicator},
 * {@code RecentFractalSwingLowIndicator}). Use {@link RecentSwingIndicators}
 * for paired factories and the canonical general-purpose default.
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/s/swinghigh.asp">Investopedia: Swing
 *      High</a>
 * @see <a href=
 *      "https://www.investopedia.com/terms/s/swinglow.asp">Investopedia: Swing
 *      Low</a>
 * @see RecentSwingIndicators
 * @since 0.20
 */
public interface RecentSwingIndicator extends Indicator<Num> {

    /**
     * Returns the index of the most recent confirmed swing point that can be
     * evaluated with the data available up to {@code index}.
     *
     * @param index the current evaluation index
     * @return the index of the most recent swing point or {@code -1} if none can be
     *         confirmed yet
     */
    int getLatestSwingIndex(int index);

    /**
     * Returns the index at which the latest swing point became confirmed.
     *
     * <p>
     * The confirmation index can be later than the swing point itself for methods
     * that require bars to the right of a candidate. Implementations that cannot
     * expose confirmation timing return {@code -1}.
     *
     * @param index the current evaluation index
     * @return confirmation index for the latest available swing, or {@code -1}
     * @since 0.23.1
     */
    default int getLatestSwingConfirmationIndex(int index) {
        return -1;
    }

    /**
     * Returns the confirmed swing point indexes discoverable with the data
     * available up to {@code index}. Only swing points at or before the given index
     * are included in the returned list.
     *
     * @param index the maximum index to evaluate (inclusive)
     * @return immutable list of swing point indexes in chronological order, all of
     *         which are less than or equal to {@code index}
     * @since 0.20
     */
    List<Integer> getSwingPointIndexesUpTo(int index);

    /**
     * Returns all confirmed swing point indexes discoverable with the current bar
     * series data.
     *
     * @return immutable list of swing point indexes in chronological order
     * @since 0.20
     */
    default List<Integer> getSwingPointIndexes() {
        if (getBarSeries() == null) {
            return List.of();
        }
        return getSwingPointIndexesUpTo(getBarSeries().getEndIndex());
    }

    /**
     * Returns the underlying price indicator used by this swing indicator to
     * retrieve price values at swing point indices.
     *
     * @return the price indicator that supplies the values at swing point indices
     */
    Indicator<Num> getPriceIndicator();
}
