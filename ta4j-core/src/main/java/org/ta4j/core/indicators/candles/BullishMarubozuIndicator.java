/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import org.ta4j.core.BarSeries;

/**
 * Bullish Marubozu candlestick pattern indicator.
 *
 * <p>
 * A bullish Marubozu is a long-bodied bullish candle where both shadows are
 * very small compared to the body, signalling strong upward momentum.
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/m/marubozo.asp">https://www.investopedia.com/terms/m/marubozo.asp</a>
 * @since 0.19
 */
public class BullishMarubozuIndicator extends AbstractMarubozuIndicator {

    /**
     * Creates a bullish Marubozu indicator with the default thresholds (5-bar body
     * average, body &gt; average, shadows &le; 5% of the body).
     *
     * @param series the bar series
     * @since 0.19
     */
    public BullishMarubozuIndicator(final BarSeries series) {
        super(series);
    }

    /**
     * Creates a bullish Marubozu indicator with custom thresholds.
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
    public BullishMarubozuIndicator(final BarSeries series, final int bodyAveragePeriod,
            final double bodyToAverageBodyRatio, final double upperShadowToBodyRatio,
            final double lowerShadowToBodyRatio) {
        super(series, bodyAveragePeriod, bodyToAverageBodyRatio, upperShadowToBodyRatio, lowerShadowToBodyRatio);
    }

    @Override
    protected boolean isBullish() {
        return true;
    }
}
