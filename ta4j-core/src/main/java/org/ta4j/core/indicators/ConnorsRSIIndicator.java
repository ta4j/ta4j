/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.PercentRankIndicator;
import org.ta4j.core.indicators.helpers.DifferenceIndicator;
import org.ta4j.core.indicators.helpers.StreakIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Connors RSI indicator.
 *
 * <p>
 * Combines a short-term RSI, RSI of streak length, and the percentile rank of
 * daily price changes.
 *
 * @see <a href=
 *      "https://chartschool.stockcharts.com/table-of-contents/technical-indicators-and-overlays/technical-indicators/connorsrsi">
 *      StockCharts: ConnorsRSI</a>
 * @since 0.20
 */
public class ConnorsRSIIndicator extends CachedIndicator<Num> {

    private final RSIIndicator priceRsi;
    private final RSIIndicator streakRsi;
    private final PercentRankIndicator percentRankIndicator;
    private final int percentRankPeriod;

    /**
     * Constructor using the original Connors RSI defaults ({@code rsiPeriod}=3,
     * {@code streakRsiPeriod}=2, {@code percentRankPeriod}=100).
     *
     * @param indicator the {@link Indicator} providing the price series
     * @since 0.20
     */
    public ConnorsRSIIndicator(Indicator<Num> indicator) {
        this(indicator, 3, 2, 100);
    }

    /**
     * Constructor.
     *
     * @param indicator         the {@link Indicator} providing the price series
     * @param rsiPeriod         the look-back period for the price RSI
     * @param streakRsiPeriod   the look-back period for the streak RSI
     * @param percentRankPeriod the look-back period for the percentile rank of
     *                          price changes
     * @since 0.20
     */
    public ConnorsRSIIndicator(Indicator<Num> indicator, int rsiPeriod, int streakRsiPeriod, int percentRankPeriod) {
        super(indicator);
        if (rsiPeriod < 1 || streakRsiPeriod < 1 || percentRankPeriod < 1) {
            throw new IllegalArgumentException("Connors RSI periods must be positive integers");
        }
        this.percentRankPeriod = percentRankPeriod;

        DifferenceIndicator priceChangeIndicator = new DifferenceIndicator(indicator);
        this.priceRsi = new RSIIndicator(indicator, rsiPeriod);
        this.streakRsi = new RSIIndicator(new StreakIndicator(indicator), streakRsiPeriod);
        this.percentRankIndicator = new PercentRankIndicator(priceChangeIndicator, percentRankPeriod);
    }

    @Override
    protected Num calculate(int index) {
        // Return NaN during unstable period
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }
        Num priceRsiValue = priceRsi.getValue(index);
        Num streakRsiValue = streakRsi.getValue(index);
        Num percentRankValue = percentRankIndicator.getValue(index);
        if (priceRsiValue.isNaN() || streakRsiValue.isNaN() || percentRankValue.isNaN()) {
            return NaN;
        }
        Num total = priceRsiValue.plus(streakRsiValue).plus(percentRankValue);
        return total.dividedBy(getBarSeries().numFactory().numOf(3));
    }

    @Override
    public int getCountOfUnstableBars() {
        // Return the maximum unstable period of all three components
        return Math.max(Math.max(priceRsi.getCountOfUnstableBars(), streakRsi.getCountOfUnstableBars()),
                percentRankIndicator.getCountOfUnstableBars());
    }
}
