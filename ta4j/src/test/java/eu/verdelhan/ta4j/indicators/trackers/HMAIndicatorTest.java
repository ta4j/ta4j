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
package eu.verdelhan.ta4j.indicators.trackers;

import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import org.junit.Before;
import org.junit.Test;

public class HMAIndicatorTest {

    private TimeSeries data;

    @Before
    public void setUp() {
        data = new MockTimeSeries(
                84.53, 87.39, 84.55,
                82.83, 82.58, 83.74,
                83.33, 84.57, 86.98,
                87.10, 83.11, 83.60,
                83.66, 82.76, 79.22,
                79.03, 78.18, 77.42,
                74.65, 77.48, 76.87
        );
    }

    @Test
    public void hmaUsingTimeFrame9UsingClosePrice() {
        // Example from http://traders.com/Documentation/FEEDbk_docs/2010/12/TradingIndexesWithHullMA.xls
        HMAIndicator hma = new HMAIndicator(new ClosePriceIndicator(data), 9);
        assertDecimalEquals(hma.getValue(10), 86.3204);
        assertDecimalEquals(hma.getValue(11), 85.3705);
        assertDecimalEquals(hma.getValue(12), 84.1044);
        assertDecimalEquals(hma.getValue(13), 83.0197);
        assertDecimalEquals(hma.getValue(14), 81.3913);
        assertDecimalEquals(hma.getValue(15), 79.6511);
        assertDecimalEquals(hma.getValue(16), 78.0443);
        assertDecimalEquals(hma.getValue(17), 76.8832);
        assertDecimalEquals(hma.getValue(18), 75.5363);
        assertDecimalEquals(hma.getValue(19), 75.1713);
        assertDecimalEquals(hma.getValue(20), 75.3597);
    }

}
