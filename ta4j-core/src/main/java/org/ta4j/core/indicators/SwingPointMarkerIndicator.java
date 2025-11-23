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

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Indicator that returns price values only at swing point indexes, and NaN
 * elsewhere. This allows swing points to be displayed as markers on charts.
 * <p>
 * The indicator identifies swing points by iterating through the bar series and
 * collecting all confirmed swing point indexes from the provided swing
 * indicator. At each bar index, it returns the price value if that index is a
 * swing point, otherwise it returns NaN.
 * <p>
 * This is useful for visualization purposes where you want to highlight swing
 * points on a chart without drawing connecting lines between them.
 *
 * @see RecentSwingIndicator
 * @see <a href=
 *      "https://www.investopedia.com/terms/s/swinghigh.asp">Investopedia: Swing
 *      High</a>
 * @see <a href=
 *      "https://www.investopedia.com/terms/s/swinglow.asp">Investopedia: Swing
 *      Low</a>
 * @since 0.20
 */
public class SwingPointMarkerIndicator extends CachedIndicator<Num> {

    private final Set<Integer> swingPointIndexes;
    private final Indicator<Num> priceIndicator;
    private final int unstableBars;

    /**
     * Constructs a SwingPointMarkerIndicator from a swing indicator.
     * <p>
     * The indicator will collect all swing point indexes by evaluating the swing
     * indicator across the entire series, then return price values only at those
     * indexes. The price indicator is automatically derived from the swing
     * indicator to ensure consistency.
     *
     * @param series         the bar series (must match the series used by the swing
     *                       indicator)
     * @param swingIndicator the swing indicator to use for identifying swing points
     *                       (can be a swing high or swing low indicator)
     * @throws IllegalArgumentException if the series does not match the swing
     *                                  indicator's series
     * @since 0.20
     */
    public SwingPointMarkerIndicator(BarSeries series, RecentSwingIndicator swingIndicator) {
        super(swingIndicator.getPriceIndicator());
        Objects.requireNonNull(series, "Series cannot be null");
        Objects.requireNonNull(swingIndicator, "Swing indicator cannot be null");

        Indicator<Num> priceIndicator = swingIndicator.getPriceIndicator();
        Objects.requireNonNull(priceIndicator, "Swing indicator must provide a price indicator");

        BarSeries swingSeries = swingIndicator.getBarSeries();
        if (swingSeries == null || !swingSeries.equals(series)) {
            throw new IllegalArgumentException("The swing indicator's series must match the provided series");
        }

        this.priceIndicator = priceIndicator;
        this.unstableBars = swingIndicator.getCountOfUnstableBars();
        this.swingPointIndexes = new HashSet<>(swingIndicator.getSwingPointIndexes());
    }

    @Override
    protected Num calculate(int index) {
        if (swingPointIndexes.contains(index)) {
            return priceIndicator.getValue(index);
        }
        return NaN;
    }

    @Override
    public int getCountOfUnstableBars() {
        return unstableBars;
    }

    /**
     * Returns the set of swing point indexes identified by this indicator.
     *
     * @return an unmodifiable set containing the swing point indexes
     * @since 0.20
     */
    public Set<Integer> getSwingPointIndexes() {
        return Set.copyOf(swingPointIndexes);
    }
}
