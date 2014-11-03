/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan & respective authors
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

public class StandardDeviationIndicatorTest {
    private TimeSeries data;

    @Before
    public void setUp() {
        data = new MockTimeSeries(1, 2, 3, 4, 3, 4, 5, 4, 3, 0, 9);
    }

    @Test
    public void standardDeviationUsingTimeFrame4UsingClosePrice() throws Exception {
        StandardDeviationIndicator sdv = new StandardDeviationIndicator(new ClosePriceIndicator(data), 4);

        assertDecimalEquals(sdv.getValue(0), 0);
        assertDecimalEquals(sdv.getValue(1), Math.sqrt(0.5));
        assertDecimalEquals(sdv.getValue(2), Math.sqrt(2.0));
        assertDecimalEquals(sdv.getValue(3), Math.sqrt(5.0));
        assertDecimalEquals(sdv.getValue(4), Math.sqrt(2.0));
        assertDecimalEquals(sdv.getValue(5), 1);
        assertDecimalEquals(sdv.getValue(6), Math.sqrt(2.0));
        assertDecimalEquals(sdv.getValue(7), Math.sqrt(2.0));
        assertDecimalEquals(sdv.getValue(8), Math.sqrt(2.0));
        assertDecimalEquals(sdv.getValue(9), Math.sqrt(14.0));
        assertDecimalEquals(sdv.getValue(10), Math.sqrt(42.0));
    }

    @Test
    public void firstValueShouldBeZero() throws Exception {
        StandardDeviationIndicator sdv = new StandardDeviationIndicator(new ClosePriceIndicator(data), 4);
        assertDecimalEquals(sdv.getValue(0), 0);
    }

    @Test
    public void standardDeviationShouldBeZeroWhenTimeFrameIs1() {
        StandardDeviationIndicator sdv = new StandardDeviationIndicator(new ClosePriceIndicator(data), 1);
        assertDecimalEquals(sdv.getValue(3), 0);
        assertDecimalEquals(sdv.getValue(8), 0);
    }

    @Test
    public void standardDeviationUsingTimeFrame2UsingClosePrice() throws Exception {
        StandardDeviationIndicator sdv = new StandardDeviationIndicator(new ClosePriceIndicator(data), 2);

        assertDecimalEquals(sdv.getValue(0), 0);
        assertDecimalEquals(sdv.getValue(1), Math.sqrt(0.5));
        assertDecimalEquals(sdv.getValue(2), Math.sqrt(0.5));
        assertDecimalEquals(sdv.getValue(3), Math.sqrt(0.5));
        assertDecimalEquals(sdv.getValue(9), Math.sqrt(4.5));
        assertDecimalEquals(sdv.getValue(10), Math.sqrt(40.5));
    }
}
