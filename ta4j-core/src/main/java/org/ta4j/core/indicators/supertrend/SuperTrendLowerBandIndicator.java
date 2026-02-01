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
 * The lower band of the SuperTrend indicator.
 *
 * <p>
 * The lower band acts as dynamic support during an uptrend. When price closes
 * below this band, it signals a potential trend reversal to bearish.
 *
 * <h2>Formula</h2>
 *
 * <pre>
 * Basic Lower Band = (High + Low) / 2 - (Multiplier × ATR)
 *
 * Final Lower Band:
 *   - If Basic Lower Band &gt; Previous Lower Band OR Previous Close &lt; Previous Lower Band:
 *       Lower Band = Basic Lower Band
 *   - Otherwise:
 *       Lower Band = Previous Lower Band (band only moves up, never down)
 * </pre>
 *
 * <p>
 * The "ratcheting" behavior (band only moves up, never down during an uptrend)
 * prevents the support level from falling when price pulls back, ensuring the
 * band tightens as the uptrend progresses.
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
 * @see SuperTrendUpperBandIndicator
 * @see ATRIndicator
 */
public class SuperTrendLowerBandIndicator extends RecursiveCachedIndicator<Num> {

    private final ATRIndicator atrIndicator;
    private final Num multiplier;
    private final MedianPriceIndicator medianPriceIndicator;

    /**
     * Constructor with {@code multiplier} = 3.
     *
     * @param barSeries the bar series
     */
    public SuperTrendLowerBandIndicator(final BarSeries barSeries) {
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
    public SuperTrendLowerBandIndicator(final BarSeries barSeries, final ATRIndicator atrIndicator, double multiplier) {
        super(barSeries);
        this.atrIndicator = atrIndicator;
        this.multiplier = getBarSeries().numFactory().numOf(multiplier);
        this.medianPriceIndicator = new MedianPriceIndicator(barSeries);
    }

    @Override
    protected Num calculate(int index) {
        Num currentBasic = medianPriceIndicator.getValue(index)
                .minus(multiplier.multipliedBy(atrIndicator.getValue(index)));
        // If currentBasic is NaN (during unstable period), return NaN
        if (Num.isNaNOrNull(currentBasic)) {
            return currentBasic;
        }
        if (index == 0) {
            return currentBasic;
        }

        Bar bar = getBarSeries().getBar(index - 1);
        Num previousValue = this.getValue(index - 1);
        // If previousValue is NaN, recover by returning currentBasic
        if (Num.isNaNOrNull(previousValue)) {
            return currentBasic;
        }

        return currentBasic.isGreaterThan(previousValue) || bar.getClosePrice().isLessThan(previousValue) ? currentBasic
                : previousValue;
    }

    @Override
    public int getCountOfUnstableBars() {
        return atrIndicator.getCountOfUnstableBars();
    }
}
