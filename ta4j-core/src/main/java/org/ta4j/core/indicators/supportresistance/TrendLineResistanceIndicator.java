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
import org.ta4j.core.indicators.RecentSwingHighIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Projects a resistance trend line by connecting the two most recent confirmed
 * swing highs.
 * <p>
 * The indicator applies the conventional resistance-trend-line definition used
 * in discretionary technical analysis where successive swing highs establish
 * the line.
 *
 * @see <a href="https://www.investopedia.com/trading/trendlines/">Investopedia:
 *      Trendlines</a>
 * @see <a href=
 *      "https://www.investopedia.com/terms/r/resistance.asp">Investopedia:
 *      Resistance</a>
 * @since 0.19
 */
public class TrendLineResistanceIndicator extends AbstractTrendLineIndicator {

    private final RecentSwingHighIndicator swingHighIndicator;

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
     * @since 0.19
     */
    public TrendLineResistanceIndicator(Indicator<Num> indicator, int precedingLowerBars, int followingLowerBars,
            int allowedEqualBars) {
        super(indicator, precedingLowerBars + followingLowerBars);
        this.swingHighIndicator = new RecentSwingHighIndicator(indicator, precedingLowerBars, followingLowerBars,
                allowedEqualBars);
    }

    /**
     * Builds a resistance trend line by analysing the high price of each bar using
     * a symmetric look-back and look-forward window.
     *
     * @param series               the series to analyse
     * @param surroundingLowerBars number of bars on each side that must be strictly
     *                             lower than the swing high
     * @since 0.19
     */
    public TrendLineResistanceIndicator(BarSeries series, int surroundingLowerBars) {
        this(new HighPriceIndicator(series), surroundingLowerBars, surroundingLowerBars, 0);
    }

    /**
     * Builds a resistance trend line that uses a default three-bar symmetric
     * window.
     *
     * @param series the series to analyse
     * @since 0.19
     */
    public TrendLineResistanceIndicator(BarSeries series) {
        this(series, 3);
    }

    @Override
    protected int getLatestPivotIndex(int index) {
        return swingHighIndicator.getLatestSwingIndex(index);
    }
}
