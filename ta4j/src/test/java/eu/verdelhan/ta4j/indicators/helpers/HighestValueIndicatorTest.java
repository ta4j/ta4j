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

import static eu.verdelhan.ta4j.TATestsUtils.*;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import org.junit.Before;
import org.junit.Test;

public class HighestValueIndicatorTest {

    private TimeSeries data;

    @Before
    public void setUp() {
        data = new MockTimeSeries(1, 2, 3, 4, 3, 4, 5, 6, 4, 3, 3, 4, 3, 2);
    }

    @Test
    public void highestValueUsingTimeFrame5UsingClosePrice() {
        HighestValueIndicator highestValue = new HighestValueIndicator(new ClosePriceIndicator(data), 5);

        assertDecimalEquals(highestValue.getValue(4), "4");
        assertDecimalEquals(highestValue.getValue(5), "4");
        assertDecimalEquals(highestValue.getValue(6), "5");
        assertDecimalEquals(highestValue.getValue(7), "6");
        assertDecimalEquals(highestValue.getValue(8), "6");
        assertDecimalEquals(highestValue.getValue(9), "6");
        assertDecimalEquals(highestValue.getValue(10), "6");
        assertDecimalEquals(highestValue.getValue(11), "6");
        assertDecimalEquals(highestValue.getValue(12), "4");
    }

    @Test
    public void firstHighestValueIndicatorValueShouldBeEqualsToFirstDataValue() {
        HighestValueIndicator highestValue = new HighestValueIndicator(new ClosePriceIndicator(data), 5);
        assertDecimalEquals(highestValue.getValue(0), "1");
    }

    @Test
    public void highestValueIndicatorWhenTimeFrameIsGreaterThanIndex() {
        HighestValueIndicator highestValue = new HighestValueIndicator(new ClosePriceIndicator(data), 500);
        assertDecimalEquals(highestValue.getValue(12), "6");
    }
}
