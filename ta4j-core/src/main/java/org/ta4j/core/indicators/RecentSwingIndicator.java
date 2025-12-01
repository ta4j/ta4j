/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
 * detection).
 * <p>
 * Implementations should clearly indicate whether they detect swing highs or
 * swing lows through their class names (e.g.,
 * {@code RecentFractalSwingHighIndicator},
 * {@code RecentFractalSwingLowIndicator}).
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/s/swinghigh.asp">Investopedia: Swing
 *      High</a>
 * @see <a href=
 *      "https://www.investopedia.com/terms/s/swinglow.asp">Investopedia: Swing
 *      Low</a>
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
