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

public class BollingerBandsLowerIndicatorTest {

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
    public void bollingerBandsLowerUsingSMAAndStandardDeviation() {

        BollingerBandsMiddleIndicator bbmSMA = new BollingerBandsMiddleIndicator(sma);
        StandardDeviationIndicator standardDeviation = new StandardDeviationIndicator(closePrice, timeFrame);
        BollingerBandsLowerIndicator bblSMA = new BollingerBandsLowerIndicator(bbmSMA, standardDeviation);

        assertDecimalEquals(bblSMA.getK(), 2);

        assertDecimalEquals(bblSMA.getValue(0), 1);
        assertDecimalEquals(bblSMA.getValue(1), 0.5);
        assertDecimalEquals(bblSMA.getValue(2), 0.367);
        assertDecimalEquals(bblSMA.getValue(3), 1.367);
        assertDecimalEquals(bblSMA.getValue(4), 2.3905);
        assertDecimalEquals(bblSMA.getValue(5), 2.7239);
        assertDecimalEquals(bblSMA.getValue(6), 2.367);

        BollingerBandsLowerIndicator bblSMAwithK = new BollingerBandsLowerIndicator(bbmSMA, standardDeviation, Decimal.valueOf("1.5"));

        assertDecimalEquals(bblSMAwithK.getK(), 1.5);

        assertDecimalEquals(bblSMAwithK.getValue(0), 1);
        assertDecimalEquals(bblSMAwithK.getValue(1), 0.75);
        assertDecimalEquals(bblSMAwithK.getValue(2), 0.7752);
        assertDecimalEquals(bblSMAwithK.getValue(3), 1.7752);
        assertDecimalEquals(bblSMAwithK.getValue(4), 2.6262);
        assertDecimalEquals(bblSMAwithK.getValue(5), 2.9595);
        assertDecimalEquals(bblSMAwithK.getValue(6), 2.7752);
    }
}
