/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.numeric.BinaryOperationIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.num.Num;

/**
 * The Detrended Price Oscillator (DPO) indicator.
 *
 * <p>
 * The Detrended Price Oscillator (DPO) is an indicator designed to remove trend
 * from price and make it easier to identify cycles. DPO does not extend to the
 * last date because it is based on a displaced moving average. However,
 * alignment with the most recent is not an issue because DPO is not a momentum
 * oscillator. Instead, DPO is used to identify cycles highs/lows and estimate
 * cycle length.
 *
 * <p>
 * In short, DPO(20) equals price 11 days ago less the 20-day SMA.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:detrended_price_osci">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:detrended_price_osci</a>
 */
public class DPOIndicator extends CachedIndicator<Num> {

    private final BinaryOperationIndicator indicatorMinusPreviousSMAIndicator;
    private final String name;

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the time frame
     */
    public DPOIndicator(BarSeries series, int barCount) {
        this(new ClosePriceIndicator(series), barCount);
    }

    /**
     * Constructor.
     *
     * @param price    the price
     * @param barCount the time frame
     */
    public DPOIndicator(Indicator<Num> price, int barCount) {
        super(price);
        final int timeFrame = barCount / 2 + 1;
        final var simpleMovingAverage = new SMAIndicator(price, barCount);
        final var previousSimpleMovingAverage = new PreviousValueIndicator(simpleMovingAverage, timeFrame);
        this.indicatorMinusPreviousSMAIndicator = BinaryOperationIndicator.difference(price,
                previousSimpleMovingAverage);
        this.name = String.format("%s barCount: %s", getClass().getSimpleName(), barCount);
    }

    @Override
    protected Num calculate(int index) {
        return indicatorMinusPreviousSMAIndicator.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return indicatorMinusPreviousSMAIndicator.getCountOfUnstableBars();
    }

    @Override
    public String toString() {
        return name;
    }
}
