/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.zigzag;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * ZigZag State Indicator.
 * <p>
 * The ZigZag indicator filters out price movements below a specified threshold
 * to identify significant price reversals and swing points. This indicator
 * tracks the state of the ZigZag pattern at each bar, including confirmed swing
 * highs and lows, the current trend direction, and the current extreme point
 * being tracked.
 * <p>
 * The algorithm works by:
 * <ul>
 * <li>Tracking the current trend direction (up or down)</li>
 * <li>Maintaining the current extreme point (highest in up-trend, lowest in
 * down-trend)</li>
 * <li>Confirming a swing point when price reverses by at least the reversal
 * threshold</li>
 * <li>Starting a new trend leg from the reversal point</li>
 * </ul>
 * <p>
 * Unlike window-based swing indicators that require a fixed number of
 * surrounding bars, the ZigZag uses a percentage or absolute price threshold to
 * determine significant reversals. This makes it adaptive to different market
 * conditions and timeframes.
 * <p>
 * The reversal threshold can be provided as a fixed value or as a dynamic
 * indicator (e.g., based on ATR for adaptive thresholds).
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/z/zigzagindicator.asp">Investopedia:
 *      ZigZag Indicator</a>
 * @see ZigZagPivotHighIndicator
 * @see ZigZagPivotLowIndicator
 * @see RecentZigZagSwingHighIndicator
 * @see RecentZigZagSwingLowIndicator
 * @since 0.20
 */
public class ZigZagStateIndicator extends CachedIndicator<ZigZagState> {

    private final Indicator<Num> highPrice;
    private final Indicator<Num> lowPrice;
    private final Indicator<Num> confirmationPrice;
    private final Indicator<Num> reversalAmount; // threshold in price units

    /**
     * Constructs a ZigZagStateIndicator.
     *
     * @param price          the price indicator to analyze (typically
     *                       {@code HighPriceIndicator}, {@code LowPriceIndicator},
     *                       or {@code ClosePriceIndicator})
     * @param reversalAmount the reversal threshold indicator. A swing point is
     *                       confirmed when price reverses by at least this amount.
     *                       Can be a fixed value (e.g., using
     *                       {@code ConstantIndicator}) or dynamic (e.g., based on
     *                       ATR). The threshold is in the same units as the price
     *                       indicator.
     */
    public ZigZagStateIndicator(Indicator<Num> price, Indicator<Num> reversalAmount) {
        this(price, price, price, reversalAmount);
    }

    /**
     * Constructs an OHLC-aware ZigZag state indicator.
     *
     * @param highPrice      source used to extend upward legs and confirm upward
     *                       reversals
     * @param lowPrice       source used to extend downward legs and confirm
     *                       downward reversals
     * @param reversalAmount positive reversal threshold in price units
     * @since 0.22.9
     */
    public ZigZagStateIndicator(Indicator<Num> highPrice, Indicator<Num> lowPrice, Indicator<Num> reversalAmount) {
        this(highPrice, lowPrice, new ClosePriceIndicator(highPrice.getBarSeries()), reversalAmount);
    }

    /**
     * Constructs a ZigZag with dedicated extreme and confirmation sources.
     *
     * @param highPrice         source used to locate swing highs
     * @param lowPrice          source used to locate swing lows
     * @param confirmationPrice source used to confirm movement away from an
     *                          extreme, commonly the close
     * @param reversalAmount    positive reversal threshold in price units
     * @since 0.22.9
     */
    public ZigZagStateIndicator(Indicator<Num> highPrice, Indicator<Num> lowPrice, Indicator<Num> confirmationPrice,
            Indicator<Num> reversalAmount) {
        super(highPrice);
        this.highPrice = IndicatorUtils.requireIndicator(highPrice, "highPrice");
        this.lowPrice = IndicatorUtils.requireIndicator(lowPrice, "lowPrice");
        IndicatorUtils.requireSameSeries(this.highPrice, this.lowPrice);
        this.confirmationPrice = IndicatorUtils.requireIndicator(confirmationPrice, "confirmationPrice");
        IndicatorUtils.requireSameSeries(this.highPrice, this.confirmationPrice);
        this.reversalAmount = IndicatorUtils.requireIndicator(reversalAmount, "reversalAmount");
        IndicatorUtils.requireSameSeries(this.highPrice, this.reversalAmount);
    }

    /**
     * Constructs an OHLC-aware ZigZag with a constant reversal threshold.
     *
     * @param highPrice      source used to locate swing highs
     * @param lowPrice       source used to locate swing lows
     * @param reversalAmount positive reversal threshold in price units
     * @since 0.22.9
     */
    public ZigZagStateIndicator(Indicator<Num> highPrice, Indicator<Num> lowPrice, Number reversalAmount) {
        this(highPrice, lowPrice, new ConstantIndicator<Num>(highPrice.getBarSeries(),
                highPrice.getBarSeries().numFactory().numOf(reversalAmount)));
    }

    public ZigZagStateIndicator(Indicator<Num> price, Number reversalAmount) {
        this(price, new ConstantIndicator<Num>(price.getBarSeries(),
                price.getBarSeries().numFactory().numOf(reversalAmount)));
    }

    public ZigZagStateIndicator(BarSeries series) {
        this(new HighPriceIndicator(series), new LowPriceIndicator(series), new ClosePriceIndicator(series),
                new ATRIndicator(series, 14));
    }

    ZigZagStateIndicator copy() {
        return new ZigZagStateIndicator(highPrice, lowPrice, confirmationPrice, reversalAmount);
    }

    @Override
    protected ZigZagState calculate(int index) {
        final BarSeries series = getBarSeries();
        final int beginIndex = series.getBeginIndex();
        final Num high = highPrice.getValue(index);
        final Num low = lowPrice.getValue(index);
        final Num confirmation = confirmationPrice.getValue(index);

        if (index == beginIndex) {
            final Num initialPrice = Num.isFinite(high) ? high : low;
            return new ZigZagState(-1, null, // lastHighIndex, lastHighPrice
                    -1, null, // lastLowIndex, lastLowPrice
                    ZigZagTrend.UNDEFINED, index, initialPrice, index, high, index, low);
        }

        final ZigZagState prev = getValue(index - 1);

        int lastHighIndex = prev.getLastHighIndex();
        Num lastHighPrice = prev.getLastHighPrice();

        int lastLowIndex = prev.getLastLowIndex();
        Num lastLowPrice = prev.getLastLowPrice();

        ZigZagTrend trend = prev.getTrend();
        int extremeIndex = prev.getLastExtremeIndex();
        Num extremePrice = prev.getLastExtremePrice();

        if (!Num.isFinite(high) || !Num.isFinite(low) || !Num.isFinite(confirmation)) {
            return prev;
        }

        switch (trend) {
        case UNDEFINED:
            int initialHighIndex = prev.getInitialHighIndex();
            Num initialHighPrice = prev.getInitialHighPrice();
            int initialLowIndex = prev.getInitialLowIndex();
            Num initialLowPrice = prev.getInitialLowPrice();
            if (!Num.isFinite(initialHighPrice) || !Num.isFinite(initialLowPrice)) {
                initialHighIndex = index;
                initialHighPrice = high;
                initialLowIndex = index;
                initialLowPrice = low;
            }
            final boolean extendsHigh = high.isGreaterThan(initialHighPrice);
            final boolean extendsLow = low.isLessThan(initialLowPrice);
            if (extendsHigh) {
                initialHighIndex = index;
                initialHighPrice = high;
            }
            if (extendsLow) {
                initialLowIndex = index;
                initialLowPrice = low;
            }
            final Num upThreshold = reversalAmount.getValue(initialLowIndex);
            final Num downThreshold = reversalAmount.getValue(initialHighIndex);
            final boolean confirmsUp = !extendsLow && Num.isFinite(upThreshold) && upThreshold.isPositive()
                    && confirmation.minus(initialLowPrice).isGreaterThanOrEqual(upThreshold);
            final boolean confirmsDown = !extendsHigh && Num.isFinite(downThreshold) && downThreshold.isPositive()
                    && initialHighPrice.minus(confirmation).isGreaterThanOrEqual(downThreshold);
            if (confirmsUp && !confirmsDown) {
                lastLowIndex = initialLowIndex;
                lastLowPrice = initialLowPrice;
                trend = ZigZagTrend.UP;
                extremeIndex = initialHighIndex;
                extremePrice = initialHighPrice;
            } else if (confirmsDown && !confirmsUp) {
                lastHighIndex = initialHighIndex;
                lastHighPrice = initialHighPrice;
                trend = ZigZagTrend.DOWN;
                extremeIndex = initialLowIndex;
                extremePrice = initialLowPrice;
            }
            return new ZigZagState(lastHighIndex, lastHighPrice, lastLowIndex, lastLowPrice, trend, extremeIndex,
                    extremePrice, initialHighIndex, initialHighPrice, initialLowIndex, initialLowPrice);

        case UP:
            if (high.isGreaterThan(extremePrice)) {
                extremeIndex = index;
                extremePrice = high;
            } else {
                final Num anchoredThreshold = reversalAmount.getValue(extremeIndex);
                if (Num.isFinite(anchoredThreshold) && anchoredThreshold.isPositive()
                        && extremePrice.minus(confirmation).isGreaterThanOrEqual(anchoredThreshold)) {
                    lastHighIndex = extremeIndex;
                    lastHighPrice = extremePrice;
                    trend = ZigZagTrend.DOWN;
                    extremeIndex = index;
                    extremePrice = low;
                }
            }
            break;

        case DOWN:
            if (low.isLessThan(extremePrice)) {
                extremeIndex = index;
                extremePrice = low;
            } else {
                final Num anchoredThreshold = reversalAmount.getValue(extremeIndex);
                if (Num.isFinite(anchoredThreshold) && anchoredThreshold.isPositive()
                        && confirmation.minus(extremePrice).isGreaterThanOrEqual(anchoredThreshold)) {
                    lastLowIndex = extremeIndex;
                    lastLowPrice = extremePrice;
                    trend = ZigZagTrend.UP;
                    extremeIndex = index;
                    extremePrice = high;
                }
            }
            break;
        }

        return new ZigZagState(lastHighIndex, lastHighPrice, lastLowIndex, lastLowPrice, trend, extremeIndex,
                extremePrice);
    }

    /**
     * Returns the number of unstable bars.
     * <p>
     * The ZigZag indicator has no fixed lookahead window since reversals can take
     * arbitrarily long to confirm. Each value only uses data up to the current
     * index, so this method returns 0.
     *
     * @return maximum warm-up required by the high, low, and reversal sources
     */
    @Override
    public int getCountOfUnstableBars() {
        return Math.max(Math.max(reversalAmount.getCountOfUnstableBars(), confirmationPrice.getCountOfUnstableBars()),
                Math.max(highPrice.getCountOfUnstableBars(), lowPrice.getCountOfUnstableBars()));
    }
}
