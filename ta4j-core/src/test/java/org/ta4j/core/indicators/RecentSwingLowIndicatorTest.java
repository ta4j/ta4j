/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.mocks.MockBarBuilder;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class RecentSwingLowIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    BarSeries series;

    public RecentSwingLowIndicatorTest(NumFactory numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        List<Bar> bars = new ArrayList<>();

        // 0 -Normal movement
        bars.add(new MockBarBuilder(numFactory).openPrice(10).closePrice(10).highPrice(10).lowPrice(10).build());
        // 1 - Normal movement
        bars.add(new MockBarBuilder(numFactory).openPrice(9).closePrice(9).highPrice(9).lowPrice(9).build());
        // 2 - Potential swing low
        bars.add(new MockBarBuilder(numFactory).openPrice(8).closePrice(8).highPrice(8).lowPrice(8).build());
        // 3 -Valley
        bars.add(new MockBarBuilder(numFactory).openPrice(8).closePrice(8).highPrice(8).lowPrice(8).build());
        // 4 -Valley
        bars.add(new MockBarBuilder(numFactory).openPrice(8).closePrice(8).highPrice(8).lowPrice(8).build());
        // 5 - Down after valley
        bars.add(new MockBarBuilder(numFactory).openPrice(9).closePrice(9).highPrice(9).lowPrice(9).build());
        // 6 - Up movement
        bars.add(new MockBarBuilder(numFactory).openPrice(10).closePrice(10).highPrice(10).lowPrice(10).build());
        // 7 - New potential swing low
        bars.add(new MockBarBuilder(numFactory).openPrice(7).closePrice(7).highPrice(7).lowPrice(7).build());
        // 8 -Sharp up
        bars.add(new MockBarBuilder(numFactory).openPrice(10).closePrice(10).highPrice(10).lowPrice(10).build());
        // 9 - Lower swing low
        bars.add(new MockBarBuilder(numFactory).openPrice(6).closePrice(6).highPrice(6).lowPrice(6).build());
        // 10 - Normal movement
        bars.add(new MockBarBuilder(numFactory).openPrice(7).closePrice(7).highPrice(7).lowPrice(7).build());
        // 11 - Up movement
        bars.add(new MockBarBuilder(numFactory).openPrice(8).closePrice(8).highPrice(8).lowPrice(8).build());
        // 12 - New potential swing low
        bars.add(new MockBarBuilder(numFactory).openPrice(5).closePrice(5).highPrice(5).lowPrice(5).build());
        // 13 - Up movement
        bars.add(new MockBarBuilder(numFactory).openPrice(6).closePrice(6).highPrice(6).lowPrice(6).build());
        // 14 -Equal to swing low
        bars.add(new MockBarBuilder(numFactory).openPrice(5).closePrice(5).highPrice(5).lowPrice(5).build());
        // 15 -Equal to swing low
        bars.add(new MockBarBuilder(numFactory).openPrice(5).closePrice(5).highPrice(5).lowPrice(5).build());
        // 16 - Up movement
        bars.add(new MockBarBuilder(numFactory).openPrice(6).closePrice(6).highPrice(6).lowPrice(6).build());
        // 17 - Equal to swing low
        bars.add(new MockBarBuilder(numFactory).openPrice(5).closePrice(5).highPrice(5).lowPrice(5).build());
        // 18 - Up movement
        bars.add(new MockBarBuilder(numFactory).openPrice(6).closePrice(6).highPrice(6).lowPrice(6).build());
        // 19 - Up movement
        bars.add(new MockBarBuilder(numFactory).openPrice(6).closePrice(6).highPrice(6).lowPrice(6).build());

        this.series = new MockBarSeriesBuilder().withNumFactory(numFactory).withBars(bars).build();
    }

    @Test
    public void testGetUnstableBars_whenSetSurroundingBars_ReturnsSameValue() {
        int surroundingBars = 2;
        RecentSwingLowIndicator swingLowIndicator = new RecentSwingLowIndicator(series, surroundingBars);

        assertEquals(surroundingBars * 2, swingLowIndicator.getUnstableBars());
    }

    @Test
    public void testCalculate_BelowSurroundingBars_ReturnsNaN() {
        RecentSwingLowIndicator swingLowIndicator = new RecentSwingLowIndicator(series, 2);

        assertNumEquals(NaN.NaN, swingLowIndicator.getValue(0));
        assertNumEquals(NaN.NaN, swingLowIndicator.getValue(1));
    }

    @Test
    public void testCalculate_With2SurroundingBarsAnd2AllowedEqualBars_ReturnsValue() {
        RecentSwingLowIndicator swingLowIndicator = new RecentSwingLowIndicator(new LowPriceIndicator(series), 2, 2, 2);

        assertNumEquals(NaN.NaN, swingLowIndicator.getValue(2));
        assertNumEquals(NaN.NaN, swingLowIndicator.getValue(3));
        assertNumEquals(8, swingLowIndicator.getValue(4));
        assertNumEquals(8, swingLowIndicator.getValue(5));
        assertNumEquals(8, swingLowIndicator.getValue(6));
        assertNumEquals(8, swingLowIndicator.getValue(7));
        assertNumEquals(8, swingLowIndicator.getValue(8));
        assertNumEquals(6, swingLowIndicator.getValue(9));
        assertNumEquals(6, swingLowIndicator.getValue(10));
        assertNumEquals(6, swingLowIndicator.getValue(11));
        assertNumEquals(5, swingLowIndicator.getValue(12));
        assertNumEquals(5, swingLowIndicator.getValue(13));
        assertNumEquals(5, swingLowIndicator.getValue(14));
        assertNumEquals(5, swingLowIndicator.getValue(15));
        assertNumEquals(5, swingLowIndicator.getValue(16));
        assertNumEquals(5, swingLowIndicator.getValue(17));
        assertNumEquals(5, swingLowIndicator.getValue(18));
        assertNumEquals(5, swingLowIndicator.getValue(19));
    }

    @Test
    public void testCalculate_With2SurroundingBarsAnd1AllowedEqualBars_ReturnsValue() {
        RecentSwingLowIndicator swingLowIndicator = new RecentSwingLowIndicator(new LowPriceIndicator(series), 2, 2, 3);

        assertNumEquals(NaN.NaN, swingLowIndicator.getValue(2));
        assertNumEquals(NaN.NaN, swingLowIndicator.getValue(3));
        assertNumEquals(8, swingLowIndicator.getValue(4));
        assertNumEquals(8, swingLowIndicator.getValue(5));
        assertNumEquals(8, swingLowIndicator.getValue(6));
        assertNumEquals(8, swingLowIndicator.getValue(7));
        assertNumEquals(8, swingLowIndicator.getValue(8));
        assertNumEquals(6, swingLowIndicator.getValue(9));
        assertNumEquals(6, swingLowIndicator.getValue(10));
        assertNumEquals(6, swingLowIndicator.getValue(11));
        assertNumEquals(5, swingLowIndicator.getValue(12));
        assertNumEquals(5, swingLowIndicator.getValue(13));
        assertNumEquals(5, swingLowIndicator.getValue(14));
        assertNumEquals(5, swingLowIndicator.getValue(15));
        assertNumEquals(5, swingLowIndicator.getValue(16));
        assertNumEquals(5, swingLowIndicator.getValue(17));
        assertNumEquals(5, swingLowIndicator.getValue(18));
        assertNumEquals(5, swingLowIndicator.getValue(19));
    }

    @Test
    public void testCalculate_With2SurroundingBarsAnd0AllowedEqualBars_ReturnsValue() {
        RecentSwingLowIndicator swingLowIndicator = new RecentSwingLowIndicator(series, 2);

        assertNumEquals(NaN.NaN, swingLowIndicator.getValue(2));
        assertNumEquals(NaN.NaN, swingLowIndicator.getValue(3));
        assertNumEquals(NaN.NaN, swingLowIndicator.getValue(4));
        assertNumEquals(NaN.NaN, swingLowIndicator.getValue(5));
        assertNumEquals(NaN.NaN, swingLowIndicator.getValue(6));
        assertNumEquals(NaN.NaN, swingLowIndicator.getValue(7));
        assertNumEquals(NaN.NaN, swingLowIndicator.getValue(8));
        assertNumEquals(6, swingLowIndicator.getValue(9));
        assertNumEquals(6, swingLowIndicator.getValue(10));
        assertNumEquals(6, swingLowIndicator.getValue(11));
        assertNumEquals(6, swingLowIndicator.getValue(12));
        assertNumEquals(6, swingLowIndicator.getValue(13));
        assertNumEquals(6, swingLowIndicator.getValue(14));
        assertNumEquals(6, swingLowIndicator.getValue(15));
        assertNumEquals(6, swingLowIndicator.getValue(16));
        assertNumEquals(6, swingLowIndicator.getValue(17));
        assertNumEquals(6, swingLowIndicator.getValue(18));
        assertNumEquals(6, swingLowIndicator.getValue(19));
    }

    @Test
    public void testCalculate_With1SurroundingBarsAnd0AllowedEqualBars_ReturnsValue() {
        RecentSwingLowIndicator swingLowIndicator = new RecentSwingLowIndicator(series, 1);

        assertNumEquals(NaN.NaN, swingLowIndicator.getValue(2));
        assertNumEquals(NaN.NaN, swingLowIndicator.getValue(3));
        assertNumEquals(NaN.NaN, swingLowIndicator.getValue(4));
        assertNumEquals(NaN.NaN, swingLowIndicator.getValue(5));
        assertNumEquals(NaN.NaN, swingLowIndicator.getValue(6));
        assertNumEquals(7, swingLowIndicator.getValue(7));
        assertNumEquals(7, swingLowIndicator.getValue(8));
        assertNumEquals(6, swingLowIndicator.getValue(9));
        assertNumEquals(6, swingLowIndicator.getValue(10));
        assertNumEquals(6, swingLowIndicator.getValue(11));
        assertNumEquals(5, swingLowIndicator.getValue(12));
        assertNumEquals(5, swingLowIndicator.getValue(13));
        assertNumEquals(5, swingLowIndicator.getValue(14));
        assertNumEquals(5, swingLowIndicator.getValue(15));
        assertNumEquals(5, swingLowIndicator.getValue(16));
        assertNumEquals(5, swingLowIndicator.getValue(17));
        assertNumEquals(5, swingLowIndicator.getValue(18));
        assertNumEquals(5, swingLowIndicator.getValue(19));
    }

    @Test
    public void testCalculate_OnMovingBarSeries_ReturnsValue() {
        // movingSeries: [10]
        BarSeries movingSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withMaxBarCount(10).build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(movingSeries);
        RecentSwingLowIndicator swingLowIndicator = new RecentSwingLowIndicator(closePrice, 1, 1, 0);

        // movingSeries: [10, 8]
        movingSeries.addBar(new MockBarBuilder(numFactory).closePrice(8).build());
        assertNumEquals(NaN.NaN, swingLowIndicator.getValue(movingSeries.getEndIndex()));

        // movingSeries: [10, 8, 5]
        movingSeries.addBar(new MockBarBuilder(numFactory).closePrice(5).build());
        assertNumEquals(NaN.NaN, swingLowIndicator.getValue(movingSeries.getEndIndex()));

        // movingSeries: [10, 8, 5, 7]
        movingSeries.addBar(new MockBarBuilder(numFactory).closePrice(7).build());
        assertNumEquals(5, swingLowIndicator.getValue(movingSeries.getEndIndex()));
    }

    @SuppressWarnings("unused")
    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_SurroundingBarsZero() {
        RecentSwingLowIndicator swingLowIndicator = new RecentSwingLowIndicator(series, 0);
    }

    @SuppressWarnings("unused")
    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_AllowedEqualBarsNegative() {
        RecentSwingLowIndicator swingLowIndicator = new RecentSwingLowIndicator(new LowPriceIndicator(series), 1, 1,
                -1);
    }

}
