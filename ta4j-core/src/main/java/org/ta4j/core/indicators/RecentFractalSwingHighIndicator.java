/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Recent Fractal Swing High Indicator.
 * <p>
 * Identifies the price of the most recently confirmed swing high, i.e., a bar
 * whose high is greater than the surrounding bars as defined by the supplied
 * look-back and look-forward windows. The definition mirrors the common
 * description of a swing high in technical analysis literature, using a
 * fractal-based window detection approach similar to Bill Williams' Fractal
 * indicator.
 *
 * @see RecentSwingIndicator
 * @see <a href=
 *      "https://www.investopedia.com/terms/s/swinghigh.asp">Investopedia: Swing
 *      High</a>
 * @since 0.20
 */
public class RecentFractalSwingHighIndicator extends AbstractRecentFractalSwingIndicator {

    private final int precedingLowerBars;
    private final int followingLowerBars;
    private final int allowedEqualBars;

    /**
     * Constructs a RecentFractalSwingHighIndicator.
     *
     * @param indicator          the indicator providing the values to inspect for
     *                           swing highs
     * @param precedingLowerBars number of immediately preceding bars that must have
     *                           strictly lower values than the candidate swing high
     * @param followingLowerBars number of immediately following bars that must have
     *                           strictly lower values than the candidate swing high
     * @param allowedEqualBars   number of bars on each side that are allowed to
     *                           match the candidate value (to support flat tops)
     * @throws IllegalArgumentException if {@code precedingLowerBars} is less than
     *                                  {@code 1}
     * @throws IllegalArgumentException if {@code followingLowerBars} is negative
     * @throws IllegalArgumentException if {@code allowedEqualBars} is negative
     */
    public RecentFractalSwingHighIndicator(Indicator<Num> indicator, int precedingLowerBars, int followingLowerBars,
            int allowedEqualBars) {
        super(indicator, validateWindowConfiguration(precedingLowerBars, followingLowerBars, allowedEqualBars,
                "precedingLowerBars", "followingLowerBars"));
        this.precedingLowerBars = precedingLowerBars;
        this.followingLowerBars = followingLowerBars;
        this.allowedEqualBars = allowedEqualBars;
    }

    /**
     * Constructs a RecentFractalSwingHighIndicator that uses the high price of each
     * bar and a symmetric window on both sides of the candidate swing high.
     *
     * @param series               the {@link BarSeries} to analyse
     * @param surroundingLowerBars number of bars on each side that must remain
     *                             below the candidate swing high
     */
    public RecentFractalSwingHighIndicator(BarSeries series, int surroundingLowerBars) {
        this(new HighPriceIndicator(series), surroundingLowerBars, surroundingLowerBars, 0);
    }

    /**
     * Constructs a RecentFractalSwingHighIndicator that uses a 3-bar symmetric
     * window and no tolerance for equal highs.
     *
     * @param series the {@link BarSeries} to analyse
     */
    public RecentFractalSwingHighIndicator(BarSeries series) {
        this(series, 3);
    }

    @Override
    protected int precedingBars() {
        return precedingLowerBars;
    }

    @Override
    protected int followingBars() {
        return followingLowerBars;
    }

    @Override
    protected int allowedEqualBars() {
        return allowedEqualBars;
    }

    @Override
    protected FractalDetectionHelper.Direction direction() {
        return FractalDetectionHelper.Direction.HIGH;
    }

    @Override
    protected boolean purgeOnNegativeDetection() {
        return true;
    }
}
