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
import org.junit.Ignore;
import org.junit.Test;
import org.ta4j.core.*;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.GainIndicator;
import org.ta4j.core.indicators.helpers.LossIndicator;
import org.ta4j.core.indicators.volume.MoneyFlowIndexIndicator;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.Assert.*;
import static org.ta4j.core.TestUtils.assertIndicatorEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

public class IntraDayMomentumIndexIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public IntraDayMomentumIndexIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Test
    public void givenBarCount_whenGetValueForIndexWithinBarCount_thenReturnNaN() {
        List<Bar> bars = new ArrayList<>();
        bars.add(new MockBar(10, 9, 10, 9, numFunction));
        bars.add(new MockBar(10, 11, 11, 10, 10, numFunction));
        bars.add(new MockBar(11, 12, 12, 10, 10, numFunction));
        bars.add(new MockBar(10, 11, 12, 10, 10, numFunction));
        bars.add(new MockBar(9, 10, 10, 9, 10, numFunction));
        bars.add(new MockBar(9, 8, 9, 8, 10, numFunction));
        bars.add(new MockBar(11, 10, 11, 9, 10, numFunction));
        BarSeries series = new MockBarSeries(bars);

        IntraDayMomentumIndexIndicator imi = new IntraDayMomentumIndexIndicator(series, 5);

        assertTrue(imi.getValue(0).isNaN());
        assertTrue(imi.getValue(1).isNaN());
        assertTrue(imi.getValue(2).isNaN());
        assertTrue(imi.getValue(3).isNaN());
        assertTrue(imi.getValue(4).isNaN());
        assertFalse(imi.getValue(5).isNaN());
    }

    @Test
    public void givenBarCountOf1_whenGetValue_thenReturnCorrectValue() {
        List<Bar> bars = new ArrayList<>();
        bars.add(new MockBar(10, 9, 10, 9, numFunction));
        bars.add(new MockBar(10, 11, 11, 10, 10, numFunction));
        bars.add(new MockBar(11, 12, 12, 10, 10, numFunction));
        bars.add(new MockBar(10, 11, 12, 10, 10, numFunction));
        bars.add(new MockBar(9, 10, 10, 9, 10, numFunction));
        bars.add(new MockBar(9, 8, 9, 8, 10, numFunction));
        bars.add(new MockBar(11, 10, 11, 9, 10, numFunction));
        BarSeries series = new MockBarSeries(bars);

        IntraDayMomentumIndexIndicator imi = new IntraDayMomentumIndexIndicator(series, 1);

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
        List<Bar> bars = new ArrayList<>();
        bars.add(new MockBar(10, 9, 10, 9, numFunction));
        bars.add(new MockBar(10, 11, 11, 10, 10, numFunction));
        bars.add(new MockBar(11, 12, 12, 10, 10, numFunction));
        bars.add(new MockBar(10, 12, 12, 10, 10, numFunction));
        bars.add(new MockBar(9, 12, 12, 9, 10, numFunction));
        bars.add(new MockBar(9, 8, 9, 8, 10, numFunction));
        bars.add(new MockBar(11, 8, 11, 8, 10, numFunction));
        bars.add(new MockBar(10, 13, 13, 9, 10, numFunction));
        bars.add(new MockBar(11, 2, 11, 2, 10, numFunction));
        BarSeries series = new MockBarSeries(bars);

        IntraDayMomentumIndexIndicator imi = new IntraDayMomentumIndexIndicator(series, 3);

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
