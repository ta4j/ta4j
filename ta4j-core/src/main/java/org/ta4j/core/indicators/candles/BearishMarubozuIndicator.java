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
package org.ta4j.core.indicators.candles;

import org.ta4j.core.BarSeries;

/**
 * Bearish Marubozu candlestick pattern indicator.
 *
 * <p>
 * A bearish Marubozu is a long-bodied bearish candle where both shadows are
 * very small compared to the body, signalling strong downward momentum.
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/m/marubozo.asp">https://www.investopedia.com/terms/m/marubozo.asp</a>
 * @since 0.19
 */
public class BearishMarubozuIndicator extends AbstractMarubozuIndicator {

    /**
     * Creates a bearish Marubozu indicator with the default thresholds (5-bar body
     * average, body &gt; average, shadows &le; 5% of the body).
     *
     * @param series the bar series
     * @since 0.19
     */
    public BearishMarubozuIndicator(final BarSeries series) {
        super(series);
    }

    /**
     * Creates a bearish Marubozu indicator with custom thresholds.
     *
     * @param series                 the bar series
     * @param bodyAveragePeriod      number of previous bars considered when
     *                               computing the average body height
     * @param bodyToAverageBodyRatio minimum ratio between the current body height
     *                               and the historical average body height
     * @param upperShadowToBodyRatio maximum ratio between the upper shadow height
     *                               and the body height
     * @param lowerShadowToBodyRatio maximum ratio between the lower shadow height
     *                               and the body height
     * @since 0.19
     */
    public BearishMarubozuIndicator(final BarSeries series, final int bodyAveragePeriod,
            final double bodyToAverageBodyRatio, final double upperShadowToBodyRatio,
            final double lowerShadowToBodyRatio) {
        super(series, bodyAveragePeriod, bodyToAverageBodyRatio, upperShadowToBodyRatio, lowerShadowToBodyRatio);
    }

    @Override
    protected boolean isBullish() {
        return false;
    }
}
