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
import static org.ta4j.core.num.NaN.NaN;

public class RelativeVolumeStandardDeviationIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private List<Bar> mockBarList;
    private BarSeries mockBarSeries;

    public RelativeVolumeStandardDeviationIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        mockBarList = new ArrayList<>();
        mockBarList.add(new MockBar(10, 9, 10, 9, 10, numFunction));
        mockBarList.add(new MockBar(10, 11, 11, 10, 11, numFunction));
        mockBarList.add(new MockBar(11, 12, 12, 10, 12, numFunction));
        mockBarList.add(new MockBar(10, 12, 12, 10, 13, numFunction));
        mockBarList.add(new MockBar(9, 12, 12, 9, 11, numFunction));
        mockBarList.add(new MockBar(9, 8, 9, 8, 10, numFunction));
        mockBarList.add(new MockBar(11, 8, 11, 8, 12, numFunction));
        mockBarList.add(new MockBar(10, 13, 13, 9, 15, numFunction));
        mockBarList.add(new MockBar(11, 2, 11, 2, 12, numFunction));

        mockBarSeries = new MockBarSeries(mockBarList);
    }

    @Test
    public void givenBarCount_whenGetValueForIndexWithinBarCount_thenReturnNaN() {
        RelativeVolumeStandardDeviationIndicator subject = new RelativeVolumeStandardDeviationIndicator(mockBarSeries,
                5);

        assertTrue(subject.getValue(0).isNaN());
        assertTrue(subject.getValue(1).isNaN());
        assertTrue(subject.getValue(2).isNaN());
        assertTrue(subject.getValue(3).isNaN());
        assertTrue(subject.getValue(4).isNaN());
        assertFalse(subject.getValue(5).isNaN());
    }

    @Test
    public void givenBarCountOf2_whenGetValue_thenReturnCorrectValue() {
        RelativeVolumeStandardDeviationIndicator subject = new RelativeVolumeStandardDeviationIndicator(mockBarSeries,
                2);

        assertTrue(subject.getValue(0).isNaN());
        assertNumEquals(NaN, subject.getValue(1));
        assertNumEquals(1, subject.getValue(2));
        assertNumEquals(1, subject.getValue(3));
        assertNumEquals(-1, subject.getValue(4));
        assertNumEquals(-1, subject.getValue(5));
        assertNumEquals(1, subject.getValue(6));
        assertNumEquals(1, subject.getValue(7));
        assertNumEquals(-1, subject.getValue(8));
    }

    @Test
    public void givenBarCountOf3_whenGetValue_thenReturnCorrectValue() {
        RelativeVolumeStandardDeviationIndicator subject = new RelativeVolumeStandardDeviationIndicator(mockBarSeries,
                3);

        assertTrue(subject.getValue(0).isNaN());
        assertTrue(subject.getValue(1).isNaN());
        assertTrue(subject.getValue(2).isNaN());
        assertNumEquals(1.224744871391589, subject.getValue(3));
        assertNumEquals(-1.224744871391589, subject.getValue(4));
        assertNumEquals(-1.069044967649698, subject.getValue(5));
        assertNumEquals(1.224744871391589, subject.getValue(6));
        assertNumEquals(1.2977713690461, subject.getValue(7));
        assertNumEquals(-0.7071067811865475, subject.getValue(8));
    }

}
