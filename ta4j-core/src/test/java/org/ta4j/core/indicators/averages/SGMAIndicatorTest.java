/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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
package org.ta4j.core.indicators.averages;

import static org.junit.Assert.*;
import static org.ta4j.core.TestUtils.*;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.CsvTestUtils;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class SGMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public SGMAIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void sgmaIndicatorTest() {

        MockIndicator mock = CsvTestUtils.getCsvFile(SGMAIndicatorTest.class, "SGMA.csv", numFactory);

        BarSeries barSeries = mock.getBarSeries();

        SGMAIndicator ma = new SGMAIndicator(new ClosePriceIndicator(barSeries), 9, 2);

        for (int i = 0; i < barSeries.getBarCount(); i++) {

            Num expected = mock.getValue(i);
            Num value = ma.getValue(i);
            assertNumEquals(expected.doubleValue(), value);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void evenBarCountThrowsException() {
        MockIndicator mock = CsvTestUtils.getCsvFile(SGMAIndicatorTest.class, "SGMA.csv", numFactory);

        BarSeries barSeries = mock.getBarSeries();

        new SGMAIndicator(new ClosePriceIndicator(barSeries), 10, 2);

        fail("Should have thrown an exception");
    }

    @Test(expected = IllegalArgumentException.class)
    public void barCountShouldBeGreaterThanPolynomialOrderThrowsException() {
        MockIndicator mock = CsvTestUtils.getCsvFile(SGMAIndicatorTest.class, "SGMA.csv", numFactory);

        BarSeries barSeries = mock.getBarSeries();

        new SGMAIndicator(new ClosePriceIndicator(barSeries), 3, 5);

        fail("Should have thrown an exception");
    }
}
