/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

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

    private final RecentSwingIndicator swingIndicator;
    private final Indicator<Num> priceIndicator;
    private final int unstableBars;

    /**
     * Constructs a SwingPointMarkerIndicator from a swing indicator.
     * <p>
     * The indicator dynamically queries the swing indicator to determine if each
     * index is a swing point, returning price values only at those indexes. The
     * price indicator is automatically derived from the swing indicator to ensure
     * consistency.
     * <p>
     * This indicator will automatically detect new swing points as they are
     * confirmed when new bars are added to the series.
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
        super(Objects.requireNonNull(swingIndicator.getPriceIndicator(),
                "Swing indicator must provide a price indicator"));
        Objects.requireNonNull(series, "Series cannot be null");
        Objects.requireNonNull(swingIndicator, "Swing indicator cannot be null");

        BarSeries swingSeries = swingIndicator.getBarSeries();
        if (swingSeries == null || !swingSeries.equals(series)) {
            throw new IllegalArgumentException("The swing indicator's series must match the provided series");
        }

        this.swingIndicator = swingIndicator;
        this.priceIndicator = swingIndicator.getPriceIndicator();
        this.unstableBars = swingIndicator.getCountOfUnstableBars();
    }

    @Override
    protected Num calculate(int index) {
        // Check if the current index is a swing point by checking if it's in the list
        // of all swing point indexes. We need to check the full list because
        // getLatestSwingIndex(index) returns the most recent swing point at or before
        // the index, not whether the index itself is a swing point.
        final var swingPointIndexes = swingIndicator.getSwingPointIndexesUpTo(index);
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
     * <p>
     * This method dynamically queries the swing indicator to get all currently
     * confirmed swing points up to the end of the series.
     *
     * @return an unmodifiable set containing the swing point indexes
     * @since 0.20
     */
    public Set<Integer> getSwingPointIndexes() {
        return Set.copyOf(swingIndicator.getSwingPointIndexes());
    }

    /**
     * Returns the swing indicator that supplies the swing point indexes.
     *
     * @return the underlying swing indicator
     * @since 0.20
     */
    public RecentSwingIndicator getSwingIndicator() {
        return swingIndicator;
    }

    /**
     * Returns the price indicator used to fetch values at swing point indexes.
     *
     * @return the price indicator associated with the swing indicator
     * @since 0.20
     */
    public Indicator<Num> getPriceIndicator() {
        return priceIndicator;
    }
}
