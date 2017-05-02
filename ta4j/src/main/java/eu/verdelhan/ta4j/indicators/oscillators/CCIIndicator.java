/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
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
package eu.verdelhan.ta4j.indicators.oscillators;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.CachedIndicator;
import eu.verdelhan.ta4j.indicators.helpers.MeanDeviationIndicator;
import eu.verdelhan.ta4j.indicators.simple.TypicalPriceIndicator;
import eu.verdelhan.ta4j.indicators.trackers.SMAIndicator;

/**
 * Commodity Channel Index (CCI) indicator.
 * <p>
 * @see http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:commodity_channel_in
 */
public class CCIIndicator extends CachedIndicator<Decimal> {

    public static final Decimal FACTOR = Decimal.valueOf("0.015");

    private TypicalPriceIndicator typicalPriceInd;

    private SMAIndicator smaInd;

    private MeanDeviationIndicator meanDeviationInd;

    private int timeFrame;

    /**
     * Constructor.
     * @param series the time series
     * @param timeFrame the time frame
     */
    public CCIIndicator(TimeSeries series, int timeFrame) {
        super(series);
        typicalPriceInd = new TypicalPriceIndicator(series);
        smaInd = new SMAIndicator(typicalPriceInd, timeFrame);
        meanDeviationInd = new MeanDeviationIndicator(typicalPriceInd, timeFrame);
        this.timeFrame = timeFrame;
    }

    @Override
    protected Decimal calculate(int index) {
        final Decimal typicalPrice = typicalPriceInd.getValue(index);
        final Decimal typicalPriceAvg = smaInd.getValue(index);
        final Decimal meanDeviation = meanDeviationInd.getValue(index);
        if (meanDeviation.isZero()) {
            return Decimal.ZERO;
        }
        return (typicalPrice.minus(typicalPriceAvg)).dividedBy(meanDeviation.multipliedBy(FACTOR));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " timeFrame: " + timeFrame;
    }
}
