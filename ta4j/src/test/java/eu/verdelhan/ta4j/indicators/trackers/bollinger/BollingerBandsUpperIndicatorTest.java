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
package eu.verdelhan.ta4j.indicators.trackers.bollinger;

import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.statistics.StandardDeviationIndicator;
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

        assertDecimalEquals(bbuSMA.getK(), 2);

        assertDecimalEquals(bbuSMA.getValue(0), 1);
        assertDecimalEquals(bbuSMA.getValue(1), 2.5);
        assertDecimalEquals(bbuSMA.getValue(2), 3.633);
        assertDecimalEquals(bbuSMA.getValue(3), 4.633);
        assertDecimalEquals(bbuSMA.getValue(4), 4.2761);
        assertDecimalEquals(bbuSMA.getValue(5), 4.6094);
        assertDecimalEquals(bbuSMA.getValue(6), 5.633);
        assertDecimalEquals(bbuSMA.getValue(7), 5.2761);
        assertDecimalEquals(bbuSMA.getValue(8), 5.633);
        assertDecimalEquals(bbuSMA.getValue(9), 4.2761);

        BollingerBandsUpperIndicator bbuSMAwithK = new BollingerBandsUpperIndicator(bbmSMA, standardDeviation, Decimal.valueOf("1.5"));

        assertDecimalEquals(bbuSMAwithK.getK(), 1.5);

        assertDecimalEquals(bbuSMAwithK.getValue(0), 1);
        assertDecimalEquals(bbuSMAwithK.getValue(1), 2.25);
        assertDecimalEquals(bbuSMAwithK.getValue(2), 3.2247);
        assertDecimalEquals(bbuSMAwithK.getValue(3), 4.2247);
        assertDecimalEquals(bbuSMAwithK.getValue(4), 4.0404);
        assertDecimalEquals(bbuSMAwithK.getValue(5), 4.3737);
        assertDecimalEquals(bbuSMAwithK.getValue(6), 5.2247);
        assertDecimalEquals(bbuSMAwithK.getValue(7), 5.0404);
        assertDecimalEquals(bbuSMAwithK.getValue(8), 5.2247);
        assertDecimalEquals(bbuSMAwithK.getValue(9), 4.0404);
    }
}
