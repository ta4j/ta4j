/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan, Ta4j Organization & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.bollinger;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.mocks.MockTimeSeries;

import static org.ta4j.core.TATestsUtils.assertNumEquals;

public class BollingerBandWidthIndicatorTest {

    private ClosePriceIndicator closePrice;

    @Before
    public void setUp() {
        TimeSeries data = new MockTimeSeries(
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
        
        assertNumEquals(bandwidth.getValue(0), 0.0);
        assertNumEquals(bandwidth.getValue(1), 36.3636);
        assertNumEquals(bandwidth.getValue(2), 66.6423);
        assertNumEquals(bandwidth.getValue(3), 60.2443);
        assertNumEquals(bandwidth.getValue(4), 71.0767);
        assertNumEquals(bandwidth.getValue(5), 69.9394);
        assertNumEquals(bandwidth.getValue(6), 62.7043);
        assertNumEquals(bandwidth.getValue(7), 56.0178);
        assertNumEquals(bandwidth.getValue(8), 27.683);
        assertNumEquals(bandwidth.getValue(9), 12.6491);
        assertNumEquals(bandwidth.getValue(10), 12.6491);
        assertNumEquals(bandwidth.getValue(11), 24.2956);
        assertNumEquals(bandwidth.getValue(12), 68.3332);
        assertNumEquals(bandwidth.getValue(13), 85.1469);
        assertNumEquals(bandwidth.getValue(14), 112.8481);
        assertNumEquals(bandwidth.getValue(15), 108.1682);
        assertNumEquals(bandwidth.getValue(16), 66.9328);
        assertNumEquals(bandwidth.getValue(17), 56.5194);
        assertNumEquals(bandwidth.getValue(18), 28.1091);
        assertNumEquals(bandwidth.getValue(19), 32.5362);
    }
}
