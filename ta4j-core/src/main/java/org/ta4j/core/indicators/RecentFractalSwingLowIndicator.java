/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Recent Fractal Swing Low Indicator.
 * <p>
 * Identifies the price of the most recently confirmed swing low, defined as a
 * bar whose low is lower than its surrounding bars within the configured
 * windows. The behaviour aligns with the commonly used definition of swing lows
 * in technical analysis literature, using a fractal-based window detection
 * approach similar to Bill Williams' Fractal indicator.
 *
 * @see RecentSwingIndicator
 * @see <a href=
 *      "https://www.investopedia.com/terms/s/swinglow.asp">Investopedia: Swing
 *      Low</a>
 * @since 0.20
 */
public class RecentFractalSwingLowIndicator extends AbstractRecentFractalSwingIndicator {

    private final int precedingHigherBars;
    private final int followingHigherBars;
    private final int allowedEqualBars;

    /**
     * Constructs a RecentFractalSwingLowIndicator.
     *
     * @param indicator           the indicator providing the values to inspect for
     *                            swing lows
     * @param precedingHigherBars number of immediately preceding bars that must
     *                            have strictly higher values than the candidate
     *                            swing low
     * @param followingHigherBars number of immediately following bars that must
     *                            have strictly higher values than the candidate
     *                            swing low
     * @param allowedEqualBars    number of bars on each side that are allowed to
     *                            match the candidate value (to support rounded
     *                            bottoms)
     * @throws IllegalArgumentException if {@code precedingHigherBars} is less than
     *                                  {@code 1}
     * @throws IllegalArgumentException if {@code followingHigherBars} is negative
     * @throws IllegalArgumentException if {@code allowedEqualBars} is negative
     */
    public RecentFractalSwingLowIndicator(Indicator<Num> indicator, int precedingHigherBars, int followingHigherBars,
            int allowedEqualBars) {
        super(indicator, validateWindowConfiguration(precedingHigherBars, followingHigherBars, allowedEqualBars,
                "precedingHigherBars", "followingHigherBars"));
        this.precedingHigherBars = precedingHigherBars;
        this.followingHigherBars = followingHigherBars;
        this.allowedEqualBars = allowedEqualBars;
    }

    /**
     * Constructs a RecentFractalSwingLowIndicator that uses the low price of each
     * bar and a symmetric window on both sides of the candidate swing low.
     *
     * @param series                the {@link BarSeries} to analyse
     * @param surroundingHigherBars number of bars on each side that must remain
     *                              above the candidate swing low
     */
    public RecentFractalSwingLowIndicator(BarSeries series, int surroundingHigherBars) {
        this(new LowPriceIndicator(series), surroundingHigherBars, surroundingHigherBars, 0);
    }

    /**
     * Constructs a RecentFractalSwingLowIndicator that uses a 3-bar symmetric
     * window and no tolerance for equal lows.
     *
     * @param series the {@link BarSeries} to analyse
     */
    public RecentFractalSwingLowIndicator(BarSeries series) {
        this(series, 3);
    }

    @Override
    protected int precedingBars() {
        return precedingHigherBars;
    }

    @Override
    protected int followingBars() {
        return followingHigherBars;
    }

    @Override
    protected int allowedEqualBars() {
        return allowedEqualBars;
    }

    @Override
    protected FractalDetectionHelper.Direction direction() {
        return FractalDetectionHelper.Direction.LOW;
    }

    @Override
    protected boolean purgeOnNegativeDetection() {
        return true;
    }
}
