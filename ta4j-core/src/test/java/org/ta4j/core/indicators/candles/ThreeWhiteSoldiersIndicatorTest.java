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
package org.ta4j.core.indicators.candles;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

public class ThreeWhiteSoldiersIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    private BarSeries series;

    public ThreeWhiteSoldiersIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        List<Bar> bars = new ArrayList<Bar>();
        // open, close, high, low
        bars.add(new MockBar(19, 19, 22, 15, numFunction));
        bars.add(new MockBar(10, 18, 20, 8, numFunction));
        bars.add(new MockBar(17, 16, 21, 15, numFunction));
        bars.add(new MockBar(15.6, 18, 18.1, 14, numFunction));
        bars.add(new MockBar(16, 19.9, 20, 15, numFunction));
        bars.add(new MockBar(16.8, 23, 23, 16.7, numFunction));
        bars.add(new MockBar(17, 25, 25, 17, numFunction));
        bars.add(new MockBar(23, 16.8, 24, 15, numFunction));
        series = new MockBarSeries(bars);
    }

    @Test
    public void getValue() {
        ThreeWhiteSoldiersIndicator tws = new ThreeWhiteSoldiersIndicator(series, 3, series.numOf(0.1));
        assertFalse(tws.getValue(0));
        assertFalse(tws.getValue(1));
        assertFalse(tws.getValue(2));
        assertFalse(tws.getValue(3));
        assertFalse(tws.getValue(4));
        assertTrue(tws.getValue(5));
        assertFalse(tws.getValue(6));
        assertFalse(tws.getValue(7));
    }
}
