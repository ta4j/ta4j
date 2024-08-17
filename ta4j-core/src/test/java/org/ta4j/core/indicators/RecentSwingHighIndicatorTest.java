/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

public class RecentSwingHighIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    BarSeries series;

    public RecentSwingHighIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        List<Bar> bars = new ArrayList<>();
        bars.add(new MockBar(10, 10, 10, 10, numFunction)); // 0 - Normal movement
        bars.add(new MockBar(11, 11, 11, 11, numFunction)); // 1 - Normal movement
        bars.add(new MockBar(12, 12, 12, 12, numFunction)); // 2 - Potential swing high
        bars.add(new MockBar(12, 12, 12, 12, numFunction)); // 3 - Plateau
        bars.add(new MockBar(12, 12, 12, 12, numFunction)); // 4 - Plateau
        bars.add(new MockBar(11, 11, 11, 11, numFunction)); // 5 - Down after plateau
        bars.add(new MockBar(10, 10, 10, 10, numFunction)); // 6 - Down movement
        bars.add(new MockBar(13, 13, 13, 13, numFunction)); // 7 - New potential swing high
        bars.add(new MockBar(10, 10, 10, 10, numFunction)); // 8 - Sharp down
        bars.add(new MockBar(14, 14, 14, 14, numFunction)); // 9 - Higher swing high
        bars.add(new MockBar(13, 13, 13, 13, numFunction)); // 10 - Normal movement
        bars.add(new MockBar(12, 12, 12, 12, numFunction)); // 11 - Down movement
        bars.add(new MockBar(13, 13, 13, 13, numFunction)); // 12 - Up movement
        bars.add(new MockBar(15, 15, 15, 15, numFunction)); // 13 - New potential swing high
        bars.add(new MockBar(13, 13, 13, 13, numFunction)); // 14 - Down movement
        bars.add(new MockBar(15, 15, 15, 15, numFunction)); // 15 - Equal high to swing high
        bars.add(new MockBar(13, 13, 13, 13, numFunction)); // 16 - Down movement
        bars.add(new MockBar(15, 15, 15, 15, numFunction)); // 17 - Equal high to swing high
        bars.add(new MockBar(13, 13, 13, 13, numFunction)); // 18 - Down movement

        this.series = new MockBarSeries(bars);
    }

    @Test
    public void testCalculate_Using2SurroundingBarsAnd2EqualBars_ReturnsValue() {
        RecentSwingHighIndicator swingHighIndicator = new RecentSwingHighIndicator(new HighPriceIndicator(series), 2, 2,
                2);

        assertNumEquals(NaN.NaN, swingHighIndicator.getValue(0));
        assertNumEquals(NaN.NaN, swingHighIndicator.getValue(1));
        assertNumEquals(NaN.NaN, swingHighIndicator.getValue(2));
        assertNumEquals(NaN.NaN, swingHighIndicator.getValue(3));
        assertNumEquals(12, swingHighIndicator.getValue(4));
        assertNumEquals(12, swingHighIndicator.getValue(5));
        assertNumEquals(12, swingHighIndicator.getValue(6));
        assertNumEquals(12, swingHighIndicator.getValue(7));
        assertNumEquals(12, swingHighIndicator.getValue(8));
        assertNumEquals(14, swingHighIndicator.getValue(9));
        assertNumEquals(14, swingHighIndicator.getValue(10));
        assertNumEquals(14, swingHighIndicator.getValue(11));
        assertNumEquals(14, swingHighIndicator.getValue(12));
        assertNumEquals(15, swingHighIndicator.getValue(13));
        assertNumEquals(15, swingHighIndicator.getValue(14));
        assertNumEquals(15, swingHighIndicator.getValue(15));
        assertNumEquals(15, swingHighIndicator.getValue(16));
        assertNumEquals(15, swingHighIndicator.getValue(17));
        assertNumEquals(15, swingHighIndicator.getValue(18));
    }

    @Test
    public void testCalculate_Using2SurroundingBarsAnd1EqualBars_ReturnsValue() {
        RecentSwingHighIndicator swingHighIndicator = new RecentSwingHighIndicator(new HighPriceIndicator(series), 2, 2,
                1);

        assertNumEquals(NaN.NaN, swingHighIndicator.getValue(0));
        assertNumEquals(NaN.NaN, swingHighIndicator.getValue(1));
        assertNumEquals(NaN.NaN, swingHighIndicator.getValue(2));
        assertNumEquals(NaN.NaN, swingHighIndicator.getValue(3));
        assertNumEquals(12, swingHighIndicator.getValue(4));
        assertNumEquals(12, swingHighIndicator.getValue(5));
        assertNumEquals(12, swingHighIndicator.getValue(6));
        assertNumEquals(12, swingHighIndicator.getValue(7));
        assertNumEquals(12, swingHighIndicator.getValue(8));
        assertNumEquals(14, swingHighIndicator.getValue(9));
        assertNumEquals(14, swingHighIndicator.getValue(10));
        assertNumEquals(14, swingHighIndicator.getValue(11));
        assertNumEquals(14, swingHighIndicator.getValue(12));
        assertNumEquals(15, swingHighIndicator.getValue(13));
        assertNumEquals(15, swingHighIndicator.getValue(14));
        assertNumEquals(15, swingHighIndicator.getValue(15));
        assertNumEquals(15, swingHighIndicator.getValue(16));
        assertNumEquals(15, swingHighIndicator.getValue(17));
        assertNumEquals(15, swingHighIndicator.getValue(18));
    }

    @Test
    public void testCalculate_Using2SurroundingBarsAnd0EqualBars_ReturnsValue() {
        RecentSwingHighIndicator swingHighIndicator = new RecentSwingHighIndicator(series, 2);

        assertNumEquals(NaN.NaN, swingHighIndicator.getValue(0));
        assertNumEquals(NaN.NaN, swingHighIndicator.getValue(1));
        assertNumEquals(NaN.NaN, swingHighIndicator.getValue(2));
        assertNumEquals(NaN.NaN, swingHighIndicator.getValue(3));
        assertNumEquals(NaN.NaN, swingHighIndicator.getValue(4));
        assertNumEquals(NaN.NaN, swingHighIndicator.getValue(5));
        assertNumEquals(NaN.NaN, swingHighIndicator.getValue(6));
        assertNumEquals(NaN.NaN, swingHighIndicator.getValue(7));
        assertNumEquals(NaN.NaN, swingHighIndicator.getValue(8));
        assertNumEquals(14, swingHighIndicator.getValue(9));
        assertNumEquals(14, swingHighIndicator.getValue(10));
        assertNumEquals(14, swingHighIndicator.getValue(11));
        assertNumEquals(14, swingHighIndicator.getValue(12));
        assertNumEquals(14, swingHighIndicator.getValue(13));
        assertNumEquals(14, swingHighIndicator.getValue(14));
        assertNumEquals(14, swingHighIndicator.getValue(15));
        assertNumEquals(14, swingHighIndicator.getValue(16));
        assertNumEquals(14, swingHighIndicator.getValue(17));
        assertNumEquals(14, swingHighIndicator.getValue(18));
    }

    @Test
    public void testGetUnstableBars_whenSetSurroundingBars_ReturnsSameValue() {
        int surroundingBars = 2;
        RecentSwingHighIndicator swingHighIndicator = new RecentSwingHighIndicator(series, surroundingBars);

        assertEquals(surroundingBars * 2, swingHighIndicator.getUnstableBars());
    }

    @Test
    public void testCalculate_Using1SurroundingBar_ReturnsValue() {
        RecentSwingHighIndicator swingHighIndicator = new RecentSwingHighIndicator(new HighPriceIndicator(series), 1, 1,
                2);

        assertNumEquals(NaN.NaN, swingHighIndicator.getValue(0));
        assertNumEquals(NaN.NaN, swingHighIndicator.getValue(1));
        assertNumEquals(12, swingHighIndicator.getValue(2));
        assertNumEquals(12, swingHighIndicator.getValue(3));
        assertNumEquals(12, swingHighIndicator.getValue(4));
        assertNumEquals(12, swingHighIndicator.getValue(5));
        assertNumEquals(12, swingHighIndicator.getValue(6));
        assertNumEquals(13, swingHighIndicator.getValue(7));
        assertNumEquals(13, swingHighIndicator.getValue(8));
        assertNumEquals(14, swingHighIndicator.getValue(9));
        assertNumEquals(14, swingHighIndicator.getValue(10));
        assertNumEquals(14, swingHighIndicator.getValue(11));
    }

    @Test
    public void testCalculate_OnMovingBarSeries_ReturnsValue() {
        BarSeries movingSeries = new MockBarSeries(numFunction, 1); // movingSeries: [1]
        ClosePriceIndicator closePrice = new ClosePriceIndicator(movingSeries);
        RecentSwingHighIndicator swingHighIndicator = new RecentSwingHighIndicator(closePrice, 1, 1, 0);

        movingSeries.addBar(new MockBar(movingSeries.getLastBar().getEndTime().plusDays(1), 2, numFunction)); // movingSeries:
        // [1, 2]
        assertNumEquals(NaN.NaN, swingHighIndicator.getValue(movingSeries.getEndIndex()));

        movingSeries.addBar(new MockBar(movingSeries.getLastBar().getEndTime().plusDays(1), 3, numFunction)); // movingSeries:
        // [1, 2,
        // 3]
        assertNumEquals(NaN.NaN, swingHighIndicator.getValue(movingSeries.getEndIndex()));

        movingSeries.addBar(new MockBar(movingSeries.getLastBar().getEndTime().plusDays(1), 2, numFunction)); // movingSeries:
        // [1, 2,
        // 3, 2]
        assertNumEquals(3, swingHighIndicator.getValue(movingSeries.getEndIndex()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_SurroundingBarsZero() {
        RecentSwingHighIndicator swingHighIndicator = new RecentSwingHighIndicator(series, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_AllowedEqualBarsNegative() {
        RecentSwingHighIndicator swingHighIndicator = new RecentSwingHighIndicator(new HighPriceIndicator(series), 1, 1,
                -1);
    }
}
