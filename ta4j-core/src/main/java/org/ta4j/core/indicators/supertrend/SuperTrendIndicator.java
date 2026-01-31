/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.supertrend;

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;

/**
 * The SuperTrend indicator.
 *
 * <p>
 * SuperTrend is a trend-following indicator that uses Average True Range (ATR)
 * to calculate dynamic support and resistance levels. It is widely used to
 * identify the current trend direction and potential trend reversal points.
 *
 * <h2>Formula</h2> The indicator calculates two bands:
 *
 * <pre>
 * Upper Band = (High + Low) / 2 + (Multiplier × ATR)
 * Lower Band = (High + Low) / 2 - (Multiplier × ATR)
 * </pre>
 *
 * The SuperTrend value alternates between these bands based on trend direction:
 * <ul>
 * <li><b>Uptrend</b>: SuperTrend = Lower Band (acts as dynamic support)</li>
 * <li><b>Downtrend</b>: SuperTrend = Upper Band (acts as dynamic
 * resistance)</li>
 * </ul>
 *
 * <h2>Trading Signals</h2>
 * <ul>
 * <li><b>Buy Signal</b>: When price closes above the SuperTrend line (trend
 * changes from down to up)</li>
 * <li><b>Sell Signal</b>: When price closes below the SuperTrend line (trend
 * changes from up to down)</li>
 * </ul>
 *
 * <h2>Default Parameters</h2>
 * <ul>
 * <li>ATR Period: 10</li>
 * <li>Multiplier: 3.0</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>
 * SuperTrendIndicator superTrend = new SuperTrendIndicator(series, 10, 3.0);
 *
 * // Check trend direction
 * if (superTrend.isUpTrend(index)) {
 *     // Price is above SuperTrend - bullish
 * }
 *
 * // Detect trend changes for entry signals
 * if (superTrend.trendChanged(index)) {
 *     if (superTrend.isUpTrend(index)) {
 *         // Buy signal - trend just turned bullish
 *     } else {
 *         // Sell signal - trend just turned bearish
 *     }
 * }
 * </pre>
 *
 * <h2>NaN Handling</h2>
 * <ul>
 * <li>During the unstable period (when band indicators return NaN), this
 * indicator returns NaN to signal that the value is not yet reliable.</li>
 * <li>After the unstable period, the indicator recovers gracefully and produces
 * valid values.</li>
 * <li>The unstable period equals the ATR bar count, ensuring the ATR has
 * stabilized before producing reliable values.</li>
 * </ul>
 *
 * @see SuperTrendUpperBandIndicator
 * @see SuperTrendLowerBandIndicator
 * @see ATRIndicator
 * @see <a href="https://www.investopedia.com/supertrend-indicator-7976167">
 *      Investopedia: SuperTrend Indicator</a>
 * @see <a href=
 *      "https://www.tradingview.com/support/solutions/43000634738-supertrend/">
 *      TradingView: SuperTrend</a>
 */
public class SuperTrendIndicator extends RecursiveCachedIndicator<Num> {

    private final SuperTrendUpperBandIndicator superTrendUpperBandIndicator;
    private final SuperTrendLowerBandIndicator superTrendLowerBandIndicator;

    /**
     * Constructor with {@code barCount} = 10 and {@code multiplier} = 3.
     *
     * @param series the bar series
     */
    public SuperTrendIndicator(final BarSeries series) {
        this(series, 10, 3d);
    }

    /**
     * Constructor.
     *
     * @param series     the bar series
     * @param barCount   the time frame (period) for the {@code ATRIndicator}.
     *                   Common values are 7, 10, or 14.
     * @param multiplier the ATR multiplier for calculating the band width. Common
     *                   values range from 1.0 to 4.0, with 3.0 being the most
     *                   widely used default. Higher values create wider bands
     *                   (fewer signals, more reliable), lower values create
     *                   narrower bands (more signals, more noise).
     */
    public SuperTrendIndicator(final BarSeries series, int barCount, double multiplier) {
        super(series);
        ATRIndicator atrIndicator = new ATRIndicator(series, barCount);
        this.superTrendUpperBandIndicator = new SuperTrendUpperBandIndicator(series, atrIndicator, multiplier);
        this.superTrendLowerBandIndicator = new SuperTrendLowerBandIndicator(series, atrIndicator, multiplier);
    }

    @Override
    protected Num calculate(int i) {
        Num lowerBand = superTrendLowerBandIndicator.getValue(i);
        Num upperBand = superTrendUpperBandIndicator.getValue(i);

        // During unstable period when bands are NaN, return NaN
        if (Num.isNaNOrNull(lowerBand) || Num.isNaNOrNull(upperBand)) {
            return NaN;
        }

        if (i == 0) {
            // At index 0, start with lower band (assume uptrend)
            return lowerBand;
        }

        Bar bar = getBarSeries().getBar(i);
        Num closePrice = bar.getClosePrice();
        Num previousValue = this.getValue(i - 1);

        // If previous value is NaN (recovering from unstable period), start fresh with
        // lower band
        if (Num.isNaNOrNull(previousValue)) {
            return lowerBand;
        }

        Num previousUpperBand = superTrendUpperBandIndicator.getValue(i - 1);
        Num previousLowerBand = superTrendLowerBandIndicator.getValue(i - 1);

        if (!Num.isNaNOrNull(previousUpperBand) && previousValue.isEqual(previousUpperBand)) {
            if (closePrice.isLessThanOrEqual(upperBand)) {
                return upperBand;
            } else {
                return lowerBand;
            }
        }

        if (!Num.isNaNOrNull(previousLowerBand) && previousValue.isEqual(previousLowerBand)) {
            if (closePrice.isGreaterThanOrEqual(lowerBand)) {
                return lowerBand;
            } else {
                return upperBand;
            }
        }

        // Fallback: use lower band
        return lowerBand;
    }

    @Override
    public int getCountOfUnstableBars() {
        return Math.max(superTrendUpperBandIndicator.getCountOfUnstableBars(),
                superTrendLowerBandIndicator.getCountOfUnstableBars());
    }

    /**
     * Determines if the market is in an uptrend at the specified index.
     *
     * <p>
     * An uptrend is identified when the SuperTrend value equals the lower band,
     * indicating the lower band is acting as dynamic support. In an uptrend, price
     * is trading above the SuperTrend line.
     *
     * @param index the bar index
     * @return {@code true} if in an uptrend (bullish), {@code false} if in a
     *         downtrend (bearish) or during the unstable period
     */
    public boolean isUpTrend(int index) {
        Num superTrendValue = getValue(index);
        Num lowerBandValue = superTrendLowerBandIndicator.getValue(index);
        // During unstable period, superTrendValue is NaN
        if (Num.isNaNOrNull(superTrendValue) || Num.isNaNOrNull(lowerBandValue)) {
            return false;
        }
        return superTrendValue.isEqual(lowerBandValue);
    }

    /**
     * Determines if the market is in a downtrend at the specified index.
     *
     * <p>
     * A downtrend is identified when the SuperTrend value equals the upper band,
     * indicating the upper band is acting as dynamic resistance. In a downtrend,
     * price is trading below the SuperTrend line.
     *
     * @param index the bar index
     * @return {@code true} if in a downtrend (bearish), {@code false} if in an
     *         uptrend (bullish) or during the unstable period
     */
    public boolean isDownTrend(int index) {
        Num superTrendValue = getValue(index);
        Num upperBandValue = superTrendUpperBandIndicator.getValue(index);
        // During unstable period, superTrendValue is NaN
        if (Num.isNaNOrNull(superTrendValue) || Num.isNaNOrNull(upperBandValue)) {
            return false;
        }
        return superTrendValue.isEqual(upperBandValue);
    }

    /**
     * Detects if a trend change (reversal) occurred at the specified index.
     *
     * <p>
     * A trend change occurs when the SuperTrend flips from the upper band to the
     * lower band (bearish to bullish) or vice versa (bullish to bearish). This is
     * typically used to generate buy/sell signals:
     * <ul>
     * <li>If {@code trendChanged(i)} is {@code true} and {@code isUpTrend(i)} is
     * {@code true}: <b>Buy signal</b></li>
     * <li>If {@code trendChanged(i)} is {@code true} and {@code isDownTrend(i)} is
     * {@code true}: <b>Sell signal</b></li>
     * </ul>
     *
     * @param index the bar index
     * @return {@code true} if the trend direction changed at this index,
     *         {@code false} otherwise or during the unstable period
     */
    public boolean trendChanged(int index) {
        if (index <= 0) {
            return false;
        }
        boolean currentUpTrend = isUpTrend(index);
        boolean previousUpTrend = isUpTrend(index - 1);
        boolean currentDownTrend = isDownTrend(index);
        boolean previousDownTrend = isDownTrend(index - 1);

        // A trend change occurs when we flip from one trend to another
        // We also check that both current and previous are in a defined trend
        // to avoid false signals during unstable period transitions
        return (currentUpTrend && previousDownTrend) || (currentDownTrend && previousUpTrend);
    }

    /** @return the {@link #superTrendLowerBandIndicator} */
    public SuperTrendLowerBandIndicator getSuperTrendLowerBandIndicator() {
        return superTrendLowerBandIndicator;
    }

    /** @return the {@link #superTrendUpperBandIndicator} */
    public SuperTrendUpperBandIndicator getSuperTrendUpperBandIndicator() {
        return superTrendUpperBandIndicator;
    }
}
