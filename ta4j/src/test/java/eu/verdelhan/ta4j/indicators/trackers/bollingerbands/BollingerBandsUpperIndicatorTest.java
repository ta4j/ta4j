/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2015 Marc de Verdelhan & respective authors
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
package eu.verdelhan.ta4j.indicators.trackers.bollingerbands;

import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.helpers.StandardDeviationIndicator;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.indicators.trackers.SMAIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import org.junit.Before;
import org.junit.Test;

public class BollingerBandsUpperIndicatorTest {

    private TimeSeries data;

    private int timeFrame;

    private ClosePriceIndicator closePrice;

    private SMAIndicator sma;

    @Before
    public void setUp() {
        data = new MockTimeSeries(1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2);
        timeFrame = 3;
        closePrice = new ClosePriceIndicator(data);
        sma = new SMAIndicator(closePrice, timeFrame);
    }

    @Test
    public void bollingerBandsUpperUsingSMAAndStandardDeviation() {

        BollingerBandsMiddleIndicator bbmSMA = new BollingerBandsMiddleIndicator(sma);
        StandardDeviationIndicator standardDeviation = new StandardDeviationIndicator(closePrice, timeFrame);
        BollingerBandsUpperIndicator bbuSMA = new BollingerBandsUpperIndicator(bbmSMA, standardDeviation);

        assertDecimalEquals(bbuSMA.getValue(0), 1);
        assertDecimalEquals(bbuSMA.getValue(1), 2.9142);
        assertDecimalEquals(bbuSMA.getValue(2), 4.8284);
        assertDecimalEquals(bbuSMA.getValue(3), 5.8284);
        assertDecimalEquals(bbuSMA.getValue(4), 4.9663);
        assertDecimalEquals(bbuSMA.getValue(5), 5.2997);
        assertDecimalEquals(bbuSMA.getValue(6), 6.8284);
        assertDecimalEquals(bbuSMA.getValue(7), 5.9663);
        assertDecimalEquals(bbuSMA.getValue(8), 6.8284);
        assertDecimalEquals(bbuSMA.getValue(9), 4.9663);
    }

    @Test
    public void bollingerBandsUpperShouldWorkJumpingIndexes() {

        BollingerBandsMiddleIndicator bbmSMA = new BollingerBandsMiddleIndicator(sma);
        StandardDeviationIndicator standardDeviation = new StandardDeviationIndicator(closePrice, timeFrame);
        BollingerBandsUpperIndicator bbuSMA = new BollingerBandsUpperIndicator(bbmSMA, standardDeviation);

        assertDecimalEquals(bbuSMA.getValue(4), 4.9663);
        assertDecimalEquals(bbuSMA.getValue(9), 4.9663);
    }
}
