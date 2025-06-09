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
package org.ta4j.core.indicators;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

public class StochasticRSIIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private final ExternalIndicatorTest xls;
    private BarSeries data;

    public StochasticRSIIndicatorTest(NumFactory numFactory) {
        super((data, params) -> new StochasticRSIIndicator(data, (int) params[0]), numFactory);
        xls = new XLSIndicatorTest(this.getClass(), "AAPL_StochRSI.xls", 15, numFactory);
    }

    @Test
    public void xlsTest() throws Exception {
        BarSeries xlsSeries = xls.getSeries();
        Indicator<Num> xlsClose = new ClosePriceIndicator(xlsSeries);
        Indicator<Num> actualIndicator;

        actualIndicator = getIndicator(xlsClose, 14);
        assertNumEquals(52.23449323656383, actualIndicator.getValue(actualIndicator.getBarSeries().getEndIndex() - 1));
    }

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(50.45, 50.30, 50.20, 50.15, 50.05, 50.06, 50.10, 50.08, 50.03, 50.07, 50.01, 50.14, 50.22,
                        50.43, 50.50, 50.56, 50.52, 50.70, 50.55, 50.62, 50.90, 50.82, 50.86, 51.20, 51.30, 51.10)
                .build();
    }

    @Test
    public void stochasticRSI() {
        var subject = new StochasticRSIIndicator(data, 14);
        assertNumEquals(100, subject.getValue(15));
        assertNumEquals(0, subject.getValue(16));
        assertNumEquals(100, subject.getValue(17));
        assertNumEquals(0, subject.getValue(18));
        assertNumEquals(25.349416458760427, subject.getValue(19));
        assertNumEquals(100, subject.getValue(20));
        assertNumEquals(57.09640084169927, subject.getValue(21));
        assertNumEquals(67.14152848195239, subject.getValue(22));
        assertNumEquals(100, subject.getValue(23));
        assertNumEquals(100, subject.getValue(24));
    }

    @Test
    public void testStochasticRSIWithClearMinMax() {
        // Test data: RSI values will be [100, 0, 100, 0] over 3-period
        data = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 15, 10, 15, 10).build();
        var subject = new StochasticRSIIndicator(data, 3);

        // Index 3: RSI = 100, min = 0, max = 100 → (100-0)/(100-0) = 1.0
        assertNumEquals(NaN.NaN, subject.getValue(3));
        // Index 4: RSI = 0, min = 0, max = 100 → (0-0)/(100-0) = 0.0
        assertNumEquals(0.0, subject.getValue(4));
    }

    @Test
    public void testStochasticRSIWithEqualMinMax() {
        // Test data: RSI values will be [100, 100, 100] over 3-period
        data = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 20, 20, 20).build();
        var subject = new StochasticRSIIndicator(data, 3);

        // Index 2: RSI = 100, min = 100, max = 100 → (100-100)/(100-100) = NaN
        assertEquals(NaN.NaN, subject.getValue(2));
        // Index 3: RSI = 100, min = 100, max = 100 → NaN
        assertEquals(NaN.NaN, subject.getValue(3));
    }

    @Test
    public void testCalculateReturnsNaNForIndicesWithinUnstablePeriod() {
        int barCount = 14;
        Indicator<Num> subject = new StochasticRSIIndicator(new ClosePriceIndicator(data), barCount);

        for (int i = 0; i < barCount; i++) {
            assertEquals(NaN.NaN, subject.getValue(i));
        }
    }

    @Test
    public void testGetCountOfUnstableBarsMatchesBarCount() {
        int barCount = 5;
        Indicator<Num> subject = new StochasticRSIIndicator(new RSIIndicator(new ClosePriceIndicator(data), barCount),
                barCount);

        assertEquals(barCount, subject.getCountOfUnstableBars());
    }
}
