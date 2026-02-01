/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.zigzag;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
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

    private final Indicator<Num> price; // typically High, Low, or Close
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
        super(price);
        this.price = price;
        this.reversalAmount = reversalAmount;
    }

    public ZigZagStateIndicator(Indicator<Num> price, Number reversalAmount) {
        this(price, new ConstantIndicator<Num>(price.getBarSeries(),
                price.getBarSeries().numFactory().numOf(reversalAmount)));
    }

    public ZigZagStateIndicator(BarSeries series) {
        this(new ClosePriceIndicator(series), new ATRIndicator(series, 14));
    }

    @Override
    protected ZigZagState calculate(int index) {
        final BarSeries series = getBarSeries();
        final int beginIndex = series.getBeginIndex();
        final Num p = price.getValue(index);

        if (index == beginIndex) {
            // Initial state: no pivots, trend undefined, extreme is this bar
            return new ZigZagState(-1, null, // lastHighIndex, lastHighPrice
                    -1, null, // lastLowIndex, lastLowPrice
                    ZigZagTrend.UNDEFINED, index, p // lastExtremeIndex, lastExtremePrice
            );
        }

        final ZigZagState prev = getValue(index - 1);

        int lastHighIndex = prev.getLastHighIndex();
        Num lastHighPrice = prev.getLastHighPrice();

        int lastLowIndex = prev.getLastLowIndex();
        Num lastLowPrice = prev.getLastLowPrice();

        ZigZagTrend trend = prev.getTrend();
        int extremeIndex = prev.getLastExtremeIndex();
        Num extremePrice = prev.getLastExtremePrice();

        final Num threshold = reversalAmount.getValue(index);

        switch (trend) {
        case UNDEFINED:
            if (p.isGreaterThan(extremePrice)) {
                trend = ZigZagTrend.UP;
                extremeIndex = index;
                extremePrice = p;
            } else if (p.isLessThan(extremePrice)) {
                trend = ZigZagTrend.DOWN;
                extremeIndex = index;
                extremePrice = p;
            }
            break;

        case UP:
            if (p.isGreaterThan(extremePrice)) {
                // Extend up‑leg
                extremeIndex = index;
                extremePrice = p;
            } else {
                // Potential reversal down
                Num moveDown = extremePrice.minus(p);
                if (moveDown.isGreaterThanOrEqual(threshold)) {
                    // Confirm swing high at extremeIndex
                    lastHighIndex = extremeIndex;
                    lastHighPrice = extremePrice;

                    // Start new down‑leg from here
                    trend = ZigZagTrend.DOWN;
                    extremeIndex = index;
                    extremePrice = p;
                }
            }
            break;

        case DOWN:
            if (p.isLessThan(extremePrice)) {
                // Extend down‑leg
                extremeIndex = index;
                extremePrice = p;
            } else {
                // Potential reversal up
                Num moveUp = p.minus(extremePrice);
                if (moveUp.isGreaterThanOrEqual(threshold)) {
                    // Confirm swing low at extremeIndex
                    lastLowIndex = extremeIndex;
                    lastLowPrice = extremePrice;

                    // Start new up‑leg from here
                    trend = ZigZagTrend.UP;
                    extremeIndex = index;
                    extremePrice = p;
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
     * @return 0, indicating no lookahead is required
     */
    @Override
    public int getCountOfUnstableBars() {
        // There is no fixed lookahead window; reversals can take arbitrarily long.
        // Returning 0 is reasonable: each value only uses data up to `index`.
        return 0;
    }
}
