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

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.statistics.StandardDeviationIndicator;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.indicators.trackers.SMAIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import org.junit.Before;
import org.junit.Test;

public class BollingerBandWidthIndicatorTest {

    private TimeSeries data;

    private ClosePriceIndicator closePrice;

    @Before
    public void setUp() {
        data = new MockTimeSeries(
                10, 12, 15, 14, 17,
                20, 21, 20, 20, 19,
                20, 17, 12, 12, 9,
                8, 9, 10, 9, 10
        );
        closePrice = new ClosePriceIndicator(data);
    }

    @Test
    public void bollingerBandWidthUsingSMAAndStandardDeviation() {

        SMAIndicator sma = new SMAIndicator(closePrice, 5);
        StandardDeviationIndicator standardDeviation = new StandardDeviationIndicator(closePrice, 5);
        
        BollingerBandsMiddleIndicator bbmSMA = new BollingerBandsMiddleIndicator(sma);
        BollingerBandsUpperIndicator bbuSMA = new BollingerBandsUpperIndicator(bbmSMA, standardDeviation);
        BollingerBandsLowerIndicator bblSMA = new BollingerBandsLowerIndicator(bbmSMA, standardDeviation);

        BollingerBandWidthIndicator bandwidth = new BollingerBandWidthIndicator(bbuSMA, bbmSMA, bblSMA);
        
        assertDecimalEquals(bandwidth.getValue(0), 0.0);
        assertDecimalEquals(bandwidth.getValue(1), 36.3636);
        assertDecimalEquals(bandwidth.getValue(2), 66.6423);
        assertDecimalEquals(bandwidth.getValue(3), 60.2443);
        assertDecimalEquals(bandwidth.getValue(4), 71.0767);
        assertDecimalEquals(bandwidth.getValue(5), 69.9394);
        assertDecimalEquals(bandwidth.getValue(6), 62.7043);
        assertDecimalEquals(bandwidth.getValue(7), 56.0178);
        assertDecimalEquals(bandwidth.getValue(8), 27.683);
        assertDecimalEquals(bandwidth.getValue(9), 12.6491);
        assertDecimalEquals(bandwidth.getValue(10), 12.6491);
        assertDecimalEquals(bandwidth.getValue(11), 24.2956);
        assertDecimalEquals(bandwidth.getValue(12), 68.3332);
        assertDecimalEquals(bandwidth.getValue(13), 85.1469);
        assertDecimalEquals(bandwidth.getValue(14), 112.8481);
        assertDecimalEquals(bandwidth.getValue(15), 108.1682);
        assertDecimalEquals(bandwidth.getValue(16), 66.9328);
        assertDecimalEquals(bandwidth.getValue(17), 56.5194);
        assertDecimalEquals(bandwidth.getValue(18), 28.1091);
        assertDecimalEquals(bandwidth.getValue(19), 32.5362);
    }
}
