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
package org.ta4j.core.indicators.supportresistance;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.RecentSwingIndicator;
import org.ta4j.core.indicators.RecentFractalSwingHighIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Projects a resistance trend line that spans a configurable look-back window
 * of swing highs.
 * <p>
 * The indicator selects the straight line that touches the greatest number of
 * confirmed swing highs within the window. When multiple candidates touch the
 * same number of swing points, the line that sits above the current high price
 * is preferred, followed by the line that spans the widest distance between its
 * anchor swing highs.
 *
 * @see <a href="https://www.investopedia.com/trading/trendlines/">Investopedia:
 *      Trendlines</a>
 * @see <a href=
 *      "https://www.investopedia.com/terms/r/resistance.asp">Investopedia:
 *      Resistance</a>
 * @since 0.20
 */
public class TrendLineResistanceIndicator extends AbstractTrendLineIndicator {

    /**
     * Builds a resistance trend line from an arbitrary indicator that provides the
     * values inspected by the underlying swing-high detector.
     *
     * @param indicator          the indicator supplying the candidate swing-high
     *                           values
     * @param precedingLowerBars number of immediately preceding bars that must be
     *                           strictly lower than a swing high
     * @param followingLowerBars number of immediately following bars that must be
     *                           strictly lower than a swing high
     * @param allowedEqualBars   number of bars on each side that may equal the
     *                           swing-high value
     * @param barCount           number of bars to look back when selecting swing
     *                           points for the trend line
     * @since 0.20
     */
    public TrendLineResistanceIndicator(Indicator<Num> indicator, int precedingLowerBars, int followingLowerBars,
            int allowedEqualBars, int barCount) {
        this(new RecentFractalSwingHighIndicator(indicator, precedingLowerBars, followingLowerBars, allowedEqualBars),
                precedingLowerBars, followingLowerBars, barCount);
    }

    /**
     * Builds a resistance trend line from an arbitrary indicator that provides the
     * values inspected by the underlying swing-high detector.
     *
     * @param indicator          the indicator supplying the candidate swing-high
     *                           values
     * @param precedingLowerBars number of immediately preceding bars that must be
     *                           strictly lower than a swing high
     * @param followingLowerBars number of immediately following bars that must be
     *                           strictly lower than a swing high
     * @param allowedEqualBars   number of bars on each side that may equal the
     *                           swing-high value
     * @since 0.20
     */
    public TrendLineResistanceIndicator(Indicator<Num> indicator, int precedingLowerBars, int followingLowerBars,
            int allowedEqualBars) {
        this(indicator, precedingLowerBars, followingLowerBars, allowedEqualBars, Integer.MAX_VALUE);
    }

    /**
     * Builds a resistance trend line from a swing-high indicator implementation.
     *
     * @param recentSwingHighIndicator the swing-high indicator to use
     * @param precedingLowerBars       number of immediately preceding bars that
     *                                 must be strictly lower than a swing high
     * @param followingLowerBars       number of immediately following bars that
     *                                 must be strictly lower than a swing high
     * @param barCount                 number of bars to look back when selecting
     *                                 swing points for the trend line
     * @since 0.20
     */
    public TrendLineResistanceIndicator(RecentSwingIndicator recentSwingHighIndicator, int precedingLowerBars,
            int followingLowerBars, int barCount) {
        super(recentSwingHighIndicator, barCount, precedingLowerBars + followingLowerBars);
    }

    /**
     * Builds a resistance trend line from a swing-high indicator implementation.
     *
     * @param recentSwingHighIndicator the swing-high indicator to use
     * @param precedingLowerBars       number of immediately preceding bars that
     *                                 must be strictly lower than a swing high
     * @param followingLowerBars       number of immediately following bars that
     *                                 must be strictly lower than a swing high
     * @since 0.20
     */
    public TrendLineResistanceIndicator(RecentSwingIndicator recentSwingHighIndicator, int precedingLowerBars,
            int followingLowerBars) {
        this(recentSwingHighIndicator, precedingLowerBars, followingLowerBars, Integer.MAX_VALUE);
    }

    /**
     * Builds a resistance trend line by analysing the high price of each bar using
     * a symmetric look-back and look-forward window.
     *
     * @param series               the series to analyse
     * @param surroundingLowerBars number of bars on each side that must be strictly
     *                             lower than the swing high
     * @param barCount             number of bars to look back when selecting swing
     *                             points for the trend line
     * @since 0.20
     */
    public TrendLineResistanceIndicator(BarSeries series, int surroundingLowerBars, int barCount) {
        this(new HighPriceIndicator(series), surroundingLowerBars, surroundingLowerBars, 0, barCount);
    }

    /**
     * Builds a resistance trend line by analysing the high price of each bar using
     * a symmetric look-back and look-forward window.
     *
     * @param series               the series to analyse
     * @param surroundingLowerBars number of bars on each side that must be strictly
     *                             lower than the swing high
     * @since 0.20
     */
    public TrendLineResistanceIndicator(BarSeries series, int surroundingLowerBars) {
        this(series, surroundingLowerBars, Integer.MAX_VALUE);
    }

    /**
     * Builds a resistance trend line that uses a default three-bar symmetric
     * window.
     *
     * @param series the series to analyse
     * @since 0.20
     */
    public TrendLineResistanceIndicator(BarSeries series) {
        this(series, 3);
    }

    @Override
    protected boolean isSupportLine() {
        return false;
    }
}
