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

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

import java.util.function.Function;
import static org.junit.Assert.assertEquals;

import org.ta4j.core.Bar;
import static org.ta4j.core.TestUtils.assertNumEquals;
import org.ta4j.core.mocks.MockBar;

public class RecentSwingLowIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    BarSeries series;

    public RecentSwingLowIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        List<Bar> bars = new ArrayList<>();
        bars.add(new MockBar(10, 10, 10, 10, numFunction)); // 0 - Normal movement
        bars.add(new MockBar(9, 9, 9, 9, numFunction)); // 1 - Normal movement
        bars.add(new MockBar(8, 8, 8, 8, numFunction)); // 2 - Potential swing low
        bars.add(new MockBar(8, 8, 8, 8, numFunction)); // 3 - Valley
        bars.add(new MockBar(8, 8, 8, 8, numFunction)); // 4 - Valley
        bars.add(new MockBar(9, 9, 9, 9, numFunction)); // 5 - Down after valley
        bars.add(new MockBar(10, 10, 10, 10, numFunction)); // 6 - Up movement
        bars.add(new MockBar(7, 7, 7, 7, numFunction)); // 7 - New potential swing low
        bars.add(new MockBar(10, 10, 10, 10, numFunction)); // 8 - Sharp up
        bars.add(new MockBar(6, 6, 6, 6, numFunction)); // 9 - Lower swing low
        bars.add(new MockBar(7, 7, 7, 7, numFunction)); // 10 - Normal movement
        bars.add(new MockBar(8, 8, 8, 8, numFunction)); // 11 - Up movement
        this.series = new MockBarSeries(bars);
    }

    @Test
    public void testGetUnstableBars_whenSetSurroundingBars_ReturnsSameValue() {
        int surroundingBars = 2;
        RecentSwingLowIndicator swingLowIndicator = new RecentSwingLowIndicator(series, surroundingBars);

        assertEquals(surroundingBars, swingLowIndicator.getUnstableBars());
    }

    @Test
    public void testCalculate_BelowSurroundingBars_ReturnsNaN() {
        RecentSwingLowIndicator swingLowIndicator = new RecentSwingLowIndicator(series, 2);

        assertNumEquals(NaN.NaN, swingLowIndicator.getValue(0));
        assertNumEquals(NaN.NaN, swingLowIndicator.getValue(1));
    }

    @Test
    public void testCalculate_With2SurroundingBarsAnd2AllowedEqualBars_ReturnsValue() {
        RecentSwingLowIndicator swingLowIndicator = new RecentSwingLowIndicator(series, 2, 2);

        assertNumEquals(8, swingLowIndicator.getValue(2));
        assertNumEquals(8, swingLowIndicator.getValue(3));
        assertNumEquals(8, swingLowIndicator.getValue(4));
        assertNumEquals(8, swingLowIndicator.getValue(5));
        assertNumEquals(8, swingLowIndicator.getValue(6));
        assertNumEquals(8, swingLowIndicator.getValue(7));
        assertNumEquals(8, swingLowIndicator.getValue(8));
        assertNumEquals(6, swingLowIndicator.getValue(9));
        assertNumEquals(6, swingLowIndicator.getValue(10));
        assertNumEquals(6, swingLowIndicator.getValue(11));
    }

    @Test
    public void testCalculate_With2SurroundingBarsAnd1AllowedEqualBars_ReturnsValue() {
        RecentSwingLowIndicator swingLowIndicator = new RecentSwingLowIndicator(series, 2, 1);

        assertNumEquals(NaN.NaN, swingLowIndicator.getValue(2));
        assertNumEquals(8, swingLowIndicator.getValue(3));
        assertNumEquals(8, swingLowIndicator.getValue(4));
        assertNumEquals(8, swingLowIndicator.getValue(5));
        assertNumEquals(8, swingLowIndicator.getValue(6));
        assertNumEquals(8, swingLowIndicator.getValue(7));
        assertNumEquals(8, swingLowIndicator.getValue(8));
        assertNumEquals(6, swingLowIndicator.getValue(9));
        assertNumEquals(6, swingLowIndicator.getValue(10));
        assertNumEquals(6, swingLowIndicator.getValue(11));
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
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_SurroundingBarsZero() {
        RecentSwingLowIndicator swingLowIndicator = new RecentSwingLowIndicator(series, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_AllowedEqualBarsNegative() {
        RecentSwingLowIndicator swingLowIndicator = new RecentSwingLowIndicator(series, 1, -1);
    }

}
