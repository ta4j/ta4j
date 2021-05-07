/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.CombineIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.num.Num;

/**
 * The Detrended Price Oscillator (DPO) indicator.
 *
 * The Detrended Price Oscillator (DPO) is an indicator designed to remove trend
 * from price and make it easier to identify cycles. DPO does not extend to the
 * last date because it is based on a displaced moving average. However,
 * alignment with the most recent is not an issue because DPO is not a momentum
 * oscillator. Instead, DPO is used to identify cycles highs/lows and estimate
 * cycle length.
 *
 * In short, DPO(20) equals price 11 days ago less the 20-day SMA.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:detrended_price_osci">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:detrended_price_osci</a>
 */
public class DPOIndicator extends CachedIndicator<Num> {

    private final CombineIndicator indicatorMinusPreviousSMAIndicator;
    private final String name;

    /**
     * Constructor.
     *
     * @param series   the series
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
        int timeFrame = barCount / 2 + 1;
        final SMAIndicator simpleMovingAverage = new SMAIndicator(price, barCount);
        final PreviousValueIndicator previousSimpleMovingAverage = new PreviousValueIndicator(simpleMovingAverage,
                timeFrame);

        this.indicatorMinusPreviousSMAIndicator = CombineIndicator.minus(price, previousSimpleMovingAverage);
        this.name = String.format("%s barCount: %s", getClass().getSimpleName(), barCount);
    }

    @Override
    protected Num calculate(int index) {
        return indicatorMinusPreviousSMAIndicator.getValue(index);
    }

    @Override
    public String toString() {
        return name;
    }
}
