/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import org.ta4j.core.BarSeries;

/**
 * Bullish Marubozu candlestick pattern indicator.
 *
 * <p>
 * A candle at index {@code i} is a bullish Marubozu when its body is strictly
 * greater than 1.0 times the average body of the {@code averagePeriod} candles
 * preceding it, its upper and lower shadows are each at most 0.1 times the
 * average high-low range of the same preceding window (inclusive), and the
 * candle is bullish (close &gt; open):
 *
 * <pre>
 * body_i &gt; 1.0 * average(body[i-averagePeriod] ... body[i-1])
 * upperShadow_i &lt;= 0.1 * average(range[i-averagePeriod] ... range[i-1])
 * lowerShadow_i &lt;= 0.1 * average(range[i-averagePeriod] ... range[i-1])
 * close_i &gt; open_i
 * </pre>
 *
 * This indicator evaluates only candle geometry; it does not evaluate trend or
 * direction context. A bullish Marubozu is traditionally interpreted as a sign
 * of strong upward momentum, but whether it predicts continuation depends on
 * the context the caller composes around it.
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/m/marubozo.asp">https://www.investopedia.com/terms/m/marubozo.asp</a>
 * @since 0.19
 */
public class BullishMarubozuIndicator extends AbstractMarubozuIndicator {

    /**
     * Creates a bullish Marubozu indicator with the default thresholds: a 5-candle
     * body and range window, a body strictly greater than the prior average body,
     * and shadows at most 10% of the prior average range.
     *
     * @param series the bar series
     * @since 0.19
     */
    public BullishMarubozuIndicator(final BarSeries series) {
        super(series);
    }

    /**
     * Creates a bullish Marubozu indicator with a custom average period.
     *
     * @param series        the bar series
     * @param averagePeriod the number of preceding candles averaged into the body
     *                      and range baselines
     * @since 0.24.2
     */
    public BullishMarubozuIndicator(final BarSeries series, final int averagePeriod) {
        super(series, averagePeriod);
    }

    /**
     * Creates a bullish Marubozu indicator with custom body and body-relative
     * shadow thresholds. This compatibility overload requires the body to strictly
     * exceed {@code bodyToAverageBodyRatio} times the prior average body and each
     * shadow to be at most its respective shadow-to-body ratio times the current
     * body.
     *
     * @param series                 the bar series
     * @param averagePeriod          the number of preceding candles averaged into
     *                               the body baseline
     * @param bodyToAverageBodyRatio the strictly exceeded body-to-average-body
     *                               ratio; must be finite and positive
     * @param upperShadowToBodyRatio the inclusive upper-shadow-to-body ratio; must
     *                               be finite and non-negative
     * @param lowerShadowToBodyRatio the inclusive lower-shadow-to-body ratio; must
     *                               be finite and non-negative
     * @throws IllegalArgumentException if the average period or a threshold is
     *                                  outside its valid range
     * @since 0.19
     */
    public BullishMarubozuIndicator(final BarSeries series, final int averagePeriod,
            final double bodyToAverageBodyRatio, final double upperShadowToBodyRatio,
            final double lowerShadowToBodyRatio) {
        super(series, averagePeriod, bodyToAverageBodyRatio, upperShadowToBodyRatio, lowerShadowToBodyRatio);
    }

    @Override
    protected boolean isBullish() {
        return true;
    }
}
