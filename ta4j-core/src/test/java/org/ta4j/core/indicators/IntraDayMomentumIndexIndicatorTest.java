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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class IntraDayMomentumIndexIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries mockBarSeries;

    public IntraDayMomentumIndexIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        mockBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        mockBarSeries.barBuilder().openPrice(10).closePrice(9).highPrice(10).lowPrice(9).add();
        mockBarSeries.barBuilder().openPrice(10).closePrice(11).highPrice(11).lowPrice(10).volume(10).add();
        mockBarSeries.barBuilder().openPrice(11).closePrice(12).highPrice(12).lowPrice(10).volume(10).add();
        mockBarSeries.barBuilder().openPrice(10).closePrice(12).highPrice(12).lowPrice(10).volume(10).add();
        mockBarSeries.barBuilder().openPrice(9).closePrice(12).highPrice(12).lowPrice(9).volume(10).add();
        mockBarSeries.barBuilder().openPrice(9).closePrice(8).highPrice(9).lowPrice(8).volume(10).add();
        mockBarSeries.barBuilder().openPrice(11).closePrice(8).highPrice(11).lowPrice(8).volume(10).add();
        mockBarSeries.barBuilder().openPrice(10).closePrice(13).highPrice(13).lowPrice(9).volume(10).add();
        mockBarSeries.barBuilder().openPrice(11).closePrice(2).highPrice(11).lowPrice(2).volume(10).add();
    };

    @Test
    public void givenBarCount_whenGetValueForIndexWithinBarCount_thenReturnNaN() {
        var imi = new IntraDayMomentumIndexIndicator(mockBarSeries, 5);

        assertTrue(imi.getValue(0).isNaN());
        assertTrue(imi.getValue(1).isNaN());
        assertTrue(imi.getValue(2).isNaN());
        assertTrue(imi.getValue(3).isNaN());
        assertTrue(imi.getValue(4).isNaN());
        assertFalse(imi.getValue(5).isNaN());
    }

    @Test
    public void givenBarCountOf1_whenGetValue_thenReturnCorrectValue() {
        var imi = new IntraDayMomentumIndexIndicator(mockBarSeries, 1);

        assertTrue(imi.getValue(0).isNaN());
        assertNumEquals(100, imi.getValue(1));
        assertNumEquals(100, imi.getValue(2));
        assertNumEquals(100, imi.getValue(3));
        assertNumEquals(100, imi.getValue(4));
        assertNumEquals(0, imi.getValue(5));
        assertNumEquals(0, imi.getValue(6));
    }

    @Test
    public void givenBarCountOf3_whenGetValue_thenReturnCorrectValue() {
        var imi = new IntraDayMomentumIndexIndicator(mockBarSeries, 3);

        assertTrue(imi.getValue(0).isNaN());
        assertTrue(imi.getValue(1).isNaN());
        assertTrue(imi.getValue(2).isNaN());
        assertNumEquals(100, imi.getValue(3));
        assertNumEquals(100, imi.getValue(4));
        assertNumEquals(83.33333333333334, imi.getValue(5));
        assertNumEquals(42.85714285714286, imi.getValue(6));
        assertNumEquals(42.85714285714286, imi.getValue(7));
        assertNumEquals(20, imi.getValue(8));
    }

}
