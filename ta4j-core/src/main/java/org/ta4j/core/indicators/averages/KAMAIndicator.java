/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;

/**
 * The Kaufman's Adaptive Moving Average (KAMA) Indicator.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:kaufman_s_adaptive_moving_average">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:kaufman_s_adaptive_moving_average</a>
 */
public class KAMAIndicator extends RecursiveCachedIndicator<Num> {

    private final Indicator<Num> price;
    private final int barCountEffectiveRatio;
    private final Num fastest;
    private final Num slowest;

    /**
     * Constructor.
     *
     * @param price                  the price
     * @param barCountEffectiveRatio the time frame of the effective ratio (usually
     *                               10)
     * @param barCountFast           the time frame fast (usually 2)
     * @param barCountSlow           the time frame slow (usually 30)
     */
    public KAMAIndicator(Indicator<Num> price, int barCountEffectiveRatio, int barCountFast, int barCountSlow) {
        super(price);
        this.price = price;
        this.barCountEffectiveRatio = barCountEffectiveRatio;
        final var numFactory = getBarSeries().numFactory();
        final var two = numFactory.two();
        this.fastest = two.dividedBy(numFactory.numOf(barCountFast + 1));
        this.slowest = two.dividedBy(numFactory.numOf(barCountSlow + 1));
    }

    /**
     * Constructor with:
     *
     * <ul>
     * <li>{@code barCountEffectiveRatio} = 10
     * <li>{@code barCountFast} = 2
     * <li>{@code barCountSlow} = 30
     * </ul>
     *
     * @param price the priceindicator
     */
    public KAMAIndicator(Indicator<Num> price) {
        this(price, 10, 2, 30);
    }

    @Override
    protected Num calculate(int index) {
        Num currentPrice = price.getValue(index);
        if (index < barCountEffectiveRatio) {
            return currentPrice;
        }
        /*
         * Efficiency Ratio (ER) ER = Change/Volatility Change = ABS(Close - Close (10
         * periods ago)) Volatility = Sum10(ABS(Close - Prior Close)) Volatility is the
         * sum of the absolute value of the last ten price changes (Close - Prior
         * Close).
         */
        int startChangeIndex = Math.max(0, index - barCountEffectiveRatio);
        Num change = currentPrice.minus(price.getValue(startChangeIndex)).abs();
        Num volatility = getBarSeries().numFactory().zero();
        for (int i = startChangeIndex; i < index; i++) {
            volatility = volatility.plus(price.getValue(i + 1).minus(price.getValue(i)).abs());
        }
        Num er = change.dividedBy(volatility);
        /*
         * Smoothing Constant (SC) SC = [ER x (fastest SC - slowest SC) + slowest SC]2
         * SC = [ER x (2/(2+1) - 2/(30+1)) + 2/(30+1)]2
         */
        Num sc = er.multipliedBy(fastest.minus(slowest)).plus(slowest).pow(2);
        /*
         * KAMA Current KAMA = Prior KAMA + SC x (Price - Prior KAMA)
         */
        Num priorKAMA = getValue(index - 1);
        return priorKAMA.plus(sc.multipliedBy(currentPrice.minus(priorKAMA)));
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

}
