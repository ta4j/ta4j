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
package eu.verdelhan.ta4j.indicators.helpers;

import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import org.junit.Before;
import org.junit.Test;

public class CumulatedLossesIndicatorTest {

    private TimeSeries data;

    @Before
    public void setUp() {
        data = new MockTimeSeries(1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2);
    }

    @Test
    public void averageGainUsingTimeFrame5UsingClosePrice() {
        CumulatedLossesIndicator losses = new CumulatedLossesIndicator(new ClosePriceIndicator(data), 5);

        assertDecimalEquals(losses.getValue(0), 0);
        assertDecimalEquals(losses.getValue(1), 0);
        assertDecimalEquals(losses.getValue(2), 0);
        assertDecimalEquals(losses.getValue(3), 0);
        assertDecimalEquals(losses.getValue(4), 1);
        assertDecimalEquals(losses.getValue(5), 1);
        assertDecimalEquals(losses.getValue(6), 1);
        assertDecimalEquals(losses.getValue(7), 2);
        assertDecimalEquals(losses.getValue(8), 3);
        assertDecimalEquals(losses.getValue(9), 2);
        assertDecimalEquals(losses.getValue(10), 2);
        assertDecimalEquals(losses.getValue(11), 3);
        assertDecimalEquals(losses.getValue(12), 3);
    }
}
