/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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
package org.ta4j.core.indicators.candles;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.trend.UpTrendIndicator;

/**
 * Hanging man candle indicator.
 *
 * @see <a href="https://www.investopedia.com/terms/h/hangingman.asp">
 *      https://www.investopedia.com/terms/h/hangingman.asp</a>
 */
public class HangingManIndicator extends CachedIndicator<Boolean> {

    private static final double DEFAULT_BODY_LENGTH_TO_BOTTOM_WICK_COEFFICIENT = 2d;
    private static final double DEFAULT_BODY_LENGTH_TO_UPPER_WICK_COEFFICIENT = 1d;

    private final RealBodyIndicator realBodyIndicator;
    private final UpTrendIndicator trendIndicator;
    private final double bodyToBottomWickRatio;
    private final double bodyToUpperWickRatio;

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public HangingManIndicator(final BarSeries series) {
        super(series);
        this.realBodyIndicator = new RealBodyIndicator(series);
        this.trendIndicator = new UpTrendIndicator(series);
        this.bodyToBottomWickRatio = DEFAULT_BODY_LENGTH_TO_BOTTOM_WICK_COEFFICIENT;
        this.bodyToUpperWickRatio = DEFAULT_BODY_LENGTH_TO_UPPER_WICK_COEFFICIENT;
    }

    /**
     * Constructor.
     *
     * @param series                the bar series
     * @param bodyToBottomWickRatio the body to bottom wick ratio
     * @param bodyToUpperWickRatio  the body to upper wick ratio
     */
    public HangingManIndicator(final BarSeries series, double bodyToBottomWickRatio, double bodyToUpperWickRatio) {
        super(series);
        this.realBodyIndicator = new RealBodyIndicator(series);
        this.trendIndicator = new UpTrendIndicator(series);
        this.bodyToBottomWickRatio = bodyToBottomWickRatio;
        this.bodyToUpperWickRatio = bodyToUpperWickRatio;
    }

    @Override
    protected Boolean calculate(final int index) {
        final var bar = getBarSeries().getBar(index);
        final var openPrice = bar.getOpenPrice();
        final var closePrice = bar.getClosePrice();
        final var lowPrice = bar.getLowPrice();
        final var highPrice = bar.getHighPrice();

        final var bodyHeight = this.realBodyIndicator.getValue(index).abs();

        final var upperBodyBoundary = openPrice.max(closePrice);
        final var bottomBodyBoundary = openPrice.min(closePrice);
        final var bottomWickHeight = bottomBodyBoundary.minus(lowPrice);
        final var upperWickHeight = highPrice.minus(upperBodyBoundary);

        return bottomWickHeight.dividedBy(bodyHeight)
                .isGreaterThan(getBarSeries().numFactory().numOf(this.bodyToBottomWickRatio))
                && upperWickHeight.dividedBy(bodyHeight)
                        .isLessThanOrEqual(getBarSeries().numFactory().numOf(this.bodyToUpperWickRatio))
                && this.trendIndicator.getValue(index);
    }

    @Override
    public int getUnstableBars() {
        return 0;
    }
}
