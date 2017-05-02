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

public class VarianceIndicatorTest {
    private TimeSeries data;

    @Before
    public void setUp() {
        data = new MockTimeSeries(1, 2, 3, 4, 3, 4, 5, 4, 3, 0, 9);
    }

    @Test
    public void varianceUsingTimeFrame4UsingClosePrice() {
        VarianceIndicator var = new VarianceIndicator(new ClosePriceIndicator(data), 4);

        assertDecimalEquals(var.getValue(0), 0);
        assertDecimalEquals(var.getValue(1), 0.25);
        assertDecimalEquals(var.getValue(2), 2.0/3);
        assertDecimalEquals(var.getValue(3), 1.25);
        assertDecimalEquals(var.getValue(4), 0.5);
        assertDecimalEquals(var.getValue(5), 0.25);
        assertDecimalEquals(var.getValue(6), 0.5);
        assertDecimalEquals(var.getValue(7), 0.5);
        assertDecimalEquals(var.getValue(8), 0.5);
        assertDecimalEquals(var.getValue(9), 3.5);
        assertDecimalEquals(var.getValue(10), 10.5);
    }

    @Test
    public void firstValueShouldBeZero() {
        VarianceIndicator var = new VarianceIndicator(new ClosePriceIndicator(data), 4);
        assertDecimalEquals(var.getValue(0), 0);
    }

    @Test
    public void varianceShouldBeZeroWhenTimeFrameIs1() {
        VarianceIndicator var = new VarianceIndicator(new ClosePriceIndicator(data), 1);
        assertDecimalEquals(var.getValue(3), 0);
        assertDecimalEquals(var.getValue(8), 0);
    }

    @Test
    public void varianceUsingTimeFrame2UsingClosePrice() {
        VarianceIndicator var = new VarianceIndicator(new ClosePriceIndicator(data), 2);

        assertDecimalEquals(var.getValue(0), 0);
        assertDecimalEquals(var.getValue(1), 0.25);
        assertDecimalEquals(var.getValue(2), 0.25);
        assertDecimalEquals(var.getValue(3), 0.25);
        assertDecimalEquals(var.getValue(9), 2.25);
        assertDecimalEquals(var.getValue(10), 20.25);
    }
}
