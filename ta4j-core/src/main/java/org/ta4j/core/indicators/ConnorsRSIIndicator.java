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
