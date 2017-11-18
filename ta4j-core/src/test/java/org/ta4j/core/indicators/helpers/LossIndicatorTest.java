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

import org.junit.Before;
import org.junit.Test;
import static org.ta4j.core.TATestsUtils.assertDecimalEquals;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.mocks.MockTimeSeries;

public class LossIndicatorTest {

    private TimeSeries data;

    @Before
    public void setUp() {
        data = new MockTimeSeries(1, 2, 3, 4, 3, 4, 7, 4, 3, 3, 5, 3, 2);
    }

    @Test
    public void lossUsingClosePrice() {
        LossIndicator loss = new LossIndicator(new ClosePriceIndicator(data));
        assertDecimalEquals(loss.getValue(0), 0);
        assertDecimalEquals(loss.getValue(1), 0);
        assertDecimalEquals(loss.getValue(2), 0);
        assertDecimalEquals(loss.getValue(3), 0);
        assertDecimalEquals(loss.getValue(4), 1);
        assertDecimalEquals(loss.getValue(5), 0);
        assertDecimalEquals(loss.getValue(6), 0);
        assertDecimalEquals(loss.getValue(7), 3);
        assertDecimalEquals(loss.getValue(8), 1);
        assertDecimalEquals(loss.getValue(9), 0);
        assertDecimalEquals(loss.getValue(10), 0);
        assertDecimalEquals(loss.getValue(11), 2);
        assertDecimalEquals(loss.getValue(12), 1);
    }
}
