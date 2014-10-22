/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan & respective authors
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

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TADecimal;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.helpers.MeanDeviationIndicator;
import eu.verdelhan.ta4j.indicators.simple.TypicalPriceIndicator;
import eu.verdelhan.ta4j.indicators.trackers.SMAIndicator;

/**
 * Commodity Channel Index (CCI) indicator.
 * <p>
 * @see http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:commodity_channel_in
 */
public class CCIIndicator implements Indicator<TADecimal> {

    public static final TADecimal FACTOR = TADecimal.valueOf("0.015");

    private TypicalPriceIndicator typicalPriceInd;

    private SMAIndicator smaInd;

    private MeanDeviationIndicator meanDeviationInd;

    private int timeFrame;

    /**
     * Constructor.
     * @param timeSeries the time series
     * @param timeFrame the time frame
     */
    public CCIIndicator(TimeSeries timeSeries, int timeFrame) {
        typicalPriceInd = new TypicalPriceIndicator(timeSeries);
        smaInd = new SMAIndicator(typicalPriceInd, timeFrame);
        meanDeviationInd = new MeanDeviationIndicator(typicalPriceInd, timeFrame);
        this.timeFrame = timeFrame;
    }

    @Override
    public TADecimal getValue(int index) {
        final TADecimal typicalPrice = typicalPriceInd.getValue(index);
        final TADecimal typicalPriceAvg = smaInd.getValue(index);
        final TADecimal meanDeviation = meanDeviationInd.getValue(index);
        if (meanDeviation.isZero()) {
            return TADecimal.ZERO;
        }
        return (typicalPrice.minus(typicalPriceAvg)).dividedBy(meanDeviation.multipliedBy(FACTOR));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " timeFrame: " + timeFrame;
    }
}
