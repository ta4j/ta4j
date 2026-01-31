/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.supertrend;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.indicators.helpers.MedianPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * The upper band of the SuperTrend indicator.
 *
 * <p>
 * The upper band acts as dynamic resistance during a downtrend. When price
 * closes above this band, it signals a potential trend reversal to bullish.
 *
 * <h2>Formula</h2>
 *
 * <pre>
 * Basic Upper Band = (High + Low) / 2 + (Multiplier × ATR)
 *
 * Final Upper Band:
 *   - If Basic Upper Band &lt; Previous Upper Band OR Previous Close &gt; Previous Upper Band:
 *       Upper Band = Basic Upper Band
 *   - Otherwise:
 *       Upper Band = Previous Upper Band (band only moves down, never up)
 * </pre>
 *
 * <p>
 * The "ratcheting" behavior (band only moves down, never up during a downtrend)
 * prevents the resistance level from rising when price pulls back, ensuring the
 * band tightens as the downtrend progresses.
 *
 * <h2>NaN Handling</h2>
 * <ul>
 * <li>During the unstable period (when ATR returns NaN), this indicator returns
 * NaN to signal that the value is not yet reliable.</li>
 * <li>When recovering from NaN, the indicator returns the current basic value
 * to allow graceful recovery.</li>
 * </ul>
 *
 * @see SuperTrendIndicator
 * @see SuperTrendLowerBandIndicator
 * @see ATRIndicator
 */
public class SuperTrendUpperBandIndicator extends RecursiveCachedIndicator<Num> {

    private final ATRIndicator atrIndicator;
    private final Num multiplier;
    private final MedianPriceIndicator medianPriceIndicator;

    /**
     * Constructor with {@code multiplier} = 3.
     *
     * @param barSeries the bar series
     */
    public SuperTrendUpperBandIndicator(final BarSeries barSeries) {
        this(barSeries, new ATRIndicator(barSeries, 10), 3d);
    }

    /**
     * Constructor.
     *
     * @param barSeries    the bar series
     * @param atrIndicator the {@link ATRIndicator} used to measure volatility
     * @param multiplier   the ATR multiplier that determines band width. Higher
     *                     values create wider bands (more conservative), lower
     *                     values create narrower bands (more sensitive).
     */
    public SuperTrendUpperBandIndicator(final BarSeries barSeries, final ATRIndicator atrIndicator, double multiplier) {
        super(barSeries);
        this.atrIndicator = atrIndicator;
        this.multiplier = getBarSeries().numFactory().numOf(multiplier);
        this.medianPriceIndicator = new MedianPriceIndicator(barSeries);
    }

    @Override
    protected Num calculate(int index) {
        Num currentBasic = medianPriceIndicator.getValue(index)
                .plus(multiplier.multipliedBy(atrIndicator.getValue(index)));
        if (Num.isNaNOrNull(currentBasic)) {
            return currentBasic;
        }
        if (index == 0) {
            return currentBasic;
        }

        Bar bar = getBarSeries().getBar(index - 1);
        Num previousValue = this.getValue(index - 1);
        if (Num.isNaNOrNull(previousValue)) {
            return currentBasic;
        }

        return currentBasic.isLessThan(previousValue) || bar.getClosePrice().isGreaterThan(previousValue) ? currentBasic
                : previousValue;
    }

    @Override
    public int getCountOfUnstableBars() {
        return atrIndicator.getCountOfUnstableBars();
    }
}
