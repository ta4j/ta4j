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
package eu.verdelhan.ta4j.indicators.statistics;

import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import org.junit.Before;
import org.junit.Test;

public class StandardErrorIndicatorTest {
    private TimeSeries data;

    @Before
    public void setUp() {
        data = new MockTimeSeries(10, 20, 30, 40, 50, 40, 40, 50, 40, 30, 20, 10);
    }

    @Test
    public void usingTimeFrame5UsingClosePrice() {
        StandardErrorIndicator se = new StandardErrorIndicator(new ClosePriceIndicator(data), 5);

        assertDecimalEquals(se.getValue(0), 0);
        assertDecimalEquals(se.getValue(1), 3.5355);
        assertDecimalEquals(se.getValue(2), 4.714);
        assertDecimalEquals(se.getValue(3), 5.5902);
        assertDecimalEquals(se.getValue(4), 6.3246);
        assertDecimalEquals(se.getValue(5), 4.5607);
        assertDecimalEquals(se.getValue(6), 2.8284);
        assertDecimalEquals(se.getValue(7), 2.1909);
        assertDecimalEquals(se.getValue(8), 2.1909);
        assertDecimalEquals(se.getValue(9), 2.8284);
        assertDecimalEquals(se.getValue(10), 4.5607);
        assertDecimalEquals(se.getValue(11), 6.3246);
    }

    @Test
    public void shouldBeZeroWhenTimeFrameIs1() {
        StandardErrorIndicator se = new StandardErrorIndicator(new ClosePriceIndicator(data), 1);
        assertDecimalEquals(se.getValue(1), 0);
        assertDecimalEquals(se.getValue(3), 0);
    }
}
