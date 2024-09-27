/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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
package org.ta4j.core.indicators.bollinger;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class BollingerBandWidthIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private ClosePriceIndicator closePrice;

    public BollingerBandWidthIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        var data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 12, 15, 14, 17, 20, 21, 20, 20, 19, 20, 17, 12, 12, 9, 8, 9, 10, 9, 10)
                .build();
        closePrice = new ClosePriceIndicator(data);
    }

    @Test
    public void bollingerBandWidthUsingSMAAndStandardDeviation() {

        var sma = new SMAIndicator(closePrice, 5);
        var standardDeviation = new StandardDeviationIndicator(closePrice, 5);

        var bbmSMA = new BollingerBandsMiddleIndicator(sma);
        var bbuSMA = new BollingerBandsUpperIndicator(bbmSMA, standardDeviation);
        var bblSMA = new BollingerBandsLowerIndicator(bbmSMA, standardDeviation);

        var bandwidth = new BollingerBandWidthIndicator(bbuSMA, bbmSMA, bblSMA);

        assertNumEquals(0.0, bandwidth.getValue(0));
        assertNumEquals(36.3636, bandwidth.getValue(1));
        assertNumEquals(66.6423, bandwidth.getValue(2));
        assertNumEquals(60.2443, bandwidth.getValue(3));
        assertNumEquals(71.0767, bandwidth.getValue(4));
        assertNumEquals(69.9394, bandwidth.getValue(5));
        assertNumEquals(62.7043, bandwidth.getValue(6));
        assertNumEquals(56.0178, bandwidth.getValue(7));
        assertNumEquals(27.683, bandwidth.getValue(8));
        assertNumEquals(12.6491, bandwidth.getValue(9));
        assertNumEquals(12.6491, bandwidth.getValue(10));
        assertNumEquals(24.2956, bandwidth.getValue(11));
        assertNumEquals(68.3332, bandwidth.getValue(12));
        assertNumEquals(85.1469, bandwidth.getValue(13));
        assertNumEquals(112.8481, bandwidth.getValue(14));
        assertNumEquals(108.1682, bandwidth.getValue(15));
        assertNumEquals(66.9328, bandwidth.getValue(16));
        assertNumEquals(56.5194, bandwidth.getValue(17));
        assertNumEquals(28.1091, bandwidth.getValue(18));
        assertNumEquals(32.5362, bandwidth.getValue(19));
    }
}
