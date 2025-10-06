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

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class DMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public DMAIndicatorTest(NumFactory numFactory) {
        super((data, params) -> new DMAIndicator(data, (int) params[0], 5), numFactory);
    }

    private BarSeries data;
    private double[] results = { 1, 1.5, 2, 2.5, 2.6, 3.2, 3.8, 4, 3.8, 3.8, 3.8, 3.4, 3, 3.6, 3.8, 4.4, 5.4, 6.8, 7.6,
            9, 10, 10.6, 10.8, 10.6, 10, 9, 8, 7, 6, 5, 4 };

    private double[] results3 = { 1, 1.5, 2, 3, 3.33333333333333, 3.66666666666667, 4, 4.33333333333333, 4,
            3.33333333333333, 3.33333333333333, 3.33333333333333, 3, 3.66666666666667, 4, 5.66666666666667,
            6.33333333333333, 8, 9, 10, 11, 11.3333333333333, 11, 10, 9, 8, 7, 6, 5, 4, 3 };

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2, 6, 4, 7, 8, 9, 10, 11, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3,
                        2)
                .build();
    }

    @Test
    public void usingBarCount3UsingClosePrice() {
        int displacement = 5;
        DMAIndicator dmaIndicator = new DMAIndicator(new ClosePriceIndicator(data), 3, displacement);

        for (int i = displacement; i < dmaIndicator.getBarSeries().getBarCount(); i++) {

            assertNumEquals(results3[i - displacement], dmaIndicator.getValue(i));
        }
    }

    @Test
    public void usingBarCount5UsingClosePriceNegativeDisplacement() {
        int displacement = -5;
        DMAIndicator dmaIndicator = new DMAIndicator(new ClosePriceIndicator(data), 5, displacement);

        for (int i = 5; i < dmaIndicator.getBarSeries().getBarCount() - 6; i++) {

            assertNumEquals(results[i - displacement], dmaIndicator.getValue(i));
        }
    }

    @Test
    public void usingBarCount5UsingClosePrice() {
        int displacement = 5;
        DMAIndicator dmaIndicator = new DMAIndicator(new ClosePriceIndicator(data), 5, displacement);

        for (int i = displacement; i < dmaIndicator.getBarSeries().getBarCount(); i++) {

            assertNumEquals(results[i - displacement], dmaIndicator.getValue(i));
        }
    }

    @Test
    public void whenBarCountIs1ResultShouldBeIndicatorValue() {
        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(data), 1);
        for (int i = 5; i < data.getBarCount(); i++) {
            assertEquals(data.getBar(i - 5).getClosePrice(), indicator.getValue(i));
        }
    }

}
