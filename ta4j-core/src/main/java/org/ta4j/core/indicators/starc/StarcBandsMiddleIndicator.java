/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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
package org.ta4j.core.indicators.starc;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.TypicalPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * STARC Bands Middle Indicator.
 * <p>
 * The middle STARC band is a Simple Moving Average.
 *
 * @see <a href="https://www.stockmaniacs.net/starc-bands-indicator/">STARC
 *      Bands Indicator</a>
 */
public class StarcBandsMiddleIndicator extends AbstractIndicator<Num> {

    private final SMAIndicator sma;
    private final int barCount;

    /**
     * Constructor.
     *
     * @param series   the bar series containing the close prices that will be used
     *                 for the simple moving average calculation
     * @param barCount the bar count for the simple moving average calculation
     */
    public StarcBandsMiddleIndicator(BarSeries series, int barCount) {
        this(new ClosePriceIndicator(series), barCount);
    }

    /**
     * Constructor.
     *
     * @param indicator a custom signal indicator (instead of the default close
     *                  price) that will be used for the simple moving average
     *                  calculation
     * @param barCount  the bar count for the simple moving average calculation
     */
    public StarcBandsMiddleIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator.getBarSeries());
        sma = new SMAIndicator(indicator, barCount);
        this.barCount = barCount;
    }

    @Override
    public Num getValue(int index) {
        return sma.getValue(index);
    }

    @Override
    public int getUnstableBars() {
        return this.barCount;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + this.barCount;
    }
}
