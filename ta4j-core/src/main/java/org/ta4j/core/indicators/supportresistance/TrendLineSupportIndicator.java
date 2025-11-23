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
import org.ta4j.core.indicators.RecentFractalSwingLowIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Projects a rising or falling support trend line by connecting the two most
 * recent confirmed swing lows.
 * <p>
 * The implementation follows the classical definition of support trend lines
 * where consecutive swing lows define the slope of the line.
 *
 * @see <a href="https://www.investopedia.com/trading/trendlines/">Investopedia:
 *      Trendlines</a>
 * @see <a href="https://www.investopedia.com/terms/s/support.asp">Investopedia:
 *      Support</a>
 * @since 0.20
 */
public class TrendLineSupportIndicator extends AbstractTrendLineIndicator {

    /**
     * Builds a support trend line from an arbitrary indicator that provides the
     * values inspected by the underlying swing-low detector.
     *
     * @param priceIndicator      the indicator supplying the candidate swing-low
     *                            values
     * @param precedingHigherBars number of immediately preceding bars that must be
     *                            strictly higher than a swing low
     * @param followingHigherBars number of immediately following bars that must be
     *                            strictly higher than a swing low
     * @param allowedEqualBars    number of bars on each side that may equal the
     *                            swing-low value
     * @since 0.20
     */
    public TrendLineSupportIndicator(Indicator<Num> priceIndicator, int precedingHigherBars, int followingHigherBars,
            int allowedEqualBars) {
        this(new RecentFractalSwingLowIndicator(priceIndicator, precedingHigherBars, followingHigherBars,
                allowedEqualBars), precedingHigherBars, followingHigherBars);
    }

    /**
     * Builds a support trend line from a swing-low indicator implementation.
     *
     * @param recentSwingLowIndicator the swing-low indicator to use
     * @param precedingHigherBars     number of immediately preceding bars that must
     *                                be strictly higher than a swing low
     * @param followingHigherBars     number of immediately following bars that must
     *                                be strictly higher than a swing low
     * @since 0.20
     */
    public TrendLineSupportIndicator(RecentSwingIndicator recentSwingLowIndicator, int precedingHigherBars,
            int followingHigherBars) {
        super(recentSwingLowIndicator, precedingHigherBars + followingHigherBars);
    }

    /**
     * Builds a support trend line by analysing the low price of each bar using a
     * symmetric look-back and look-forward window.
     *
     * @param series                the series to analyse
     * @param surroundingHigherBars number of bars on each side that must be
     *                              strictly higher than the swing low
     * @since 0.20
     */
    public TrendLineSupportIndicator(BarSeries series, int surroundingHigherBars) {
        this(new LowPriceIndicator(series), surroundingHigherBars, surroundingHigherBars, 0);
    }

    /**
     * Builds a support trend line that uses a default three-bar symmetric window.
     *
     * @param series the series to analyse
     * @since 0.20
     */
    public TrendLineSupportIndicator(BarSeries series) {
        this(series, 3);
    }
}
