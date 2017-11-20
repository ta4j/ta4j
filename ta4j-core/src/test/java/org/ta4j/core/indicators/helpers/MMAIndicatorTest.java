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
package org.ta4j.core.indicators.helpers;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import static org.ta4j.core.TATestsUtils.assertDecimalEquals;
import org.ta4j.core.Tick;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.mocks.MockTick;
import org.ta4j.core.mocks.MockTimeSeries;

public class MMAIndicatorTest {

    private TimeSeries data;

    @Before
    public void setUp() {
        data = new MockTimeSeries(
                64.75, 63.79, 63.73,
                63.73, 63.55, 63.19,
                63.91, 63.85, 62.95,
                63.37, 61.33, 61.51);
    }

    @Test
    public void mmaFirstValueShouldBeEqualsToFirstDataValue() {
        MMAIndicator mma = new MMAIndicator(new ClosePriceIndicator(data), 1);
        assertDecimalEquals(mma.getValue(0), 64.75);
    }

    @Test
    public void mmaUsingTimeFrame10UsingClosePrice() {
        MMAIndicator mma = new MMAIndicator(new ClosePriceIndicator(data), 10);
        assertDecimalEquals(mma.getValue(9), 63.9983);
        assertDecimalEquals(mma.getValue(10), 63.7315);
        assertDecimalEquals(mma.getValue(11), 63.5093);
    }

    @Test
    public void stackOverflowError() {
        List<Tick> bigListOfTicks = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            bigListOfTicks.add(new MockTick(i));
        }
        MockTimeSeries bigSeries = new MockTimeSeries(bigListOfTicks);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(bigSeries);
        MMAIndicator mma = new MMAIndicator(closePrice, 10);
        // if a StackOverflowError is thrown here, then the RecursiveCachedIndicator does not work as intended.
        assertDecimalEquals(mma.getValue(9999), 9990.0);
    }
}
