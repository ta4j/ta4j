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
package org.ta4j.core.indicators.volume;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

public class TimeSegmentedVolumeIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries series;

    public TimeSegmentedVolumeIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        List<Bar> bars = new ArrayList<>();
        bars.add(new MockBar(10, 10, 10, 10, numFunction));
        bars.add(new MockBar(9, 9, 9, 9, 10, numFunction));
        bars.add(new MockBar(10, 10, 10, 10, 10, numFunction));
        bars.add(new MockBar(11, 11, 11, 11, 10, numFunction));
        bars.add(new MockBar(12, 12, 12, 12, 10, numFunction));
        bars.add(new MockBar(11, 11, 11, 11, 10, numFunction));
        bars.add(new MockBar(11, 11, 11, 11, 10, numFunction));
        series = new MockBarSeries(bars);
    }

    @Test
    public void whenIndexIsLessThanBarCount_returnNaN() {
        TimeSegmentedVolumeIndicator tsv = new TimeSegmentedVolumeIndicator(series, 5);

        assertTrue(tsv.getValue(0).isNaN());
        assertTrue(tsv.getValue(1).isNaN());
        assertTrue(tsv.getValue(2).isNaN());
        assertTrue(tsv.getValue(3).isNaN());
        assertTrue(tsv.getValue(4).isNaN());
        assertFalse(tsv.getValue(5).isNaN());
    }

    @Test
    public void whenBarCountIsOne_returnCorrectValue() {
        TimeSegmentedVolumeIndicator tsv = new TimeSegmentedVolumeIndicator(series, 1);

        assertTrue(tsv.getValue(0).isNaN());
        assertNumEquals(-10, tsv.getValue(1));
        assertNumEquals(10, tsv.getValue(2));
        assertNumEquals(10, tsv.getValue(3));
        assertNumEquals(10, tsv.getValue(4));
        assertNumEquals(-10, tsv.getValue(5));
    }

    @Test
    public void whenBarCountIsThree_returnCorrectValue() {
        TimeSegmentedVolumeIndicator tsv = new TimeSegmentedVolumeIndicator(series, 3);

        assertTrue(tsv.getValue(0).isNaN());
        assertTrue(tsv.getValue(1).isNaN());
        assertTrue(tsv.getValue(2).isNaN());
        assertNumEquals(10, tsv.getValue(3));
        assertNumEquals(30, tsv.getValue(4));
        assertNumEquals(10, tsv.getValue(5));
        assertNumEquals(0, tsv.getValue(6));
    }
}
