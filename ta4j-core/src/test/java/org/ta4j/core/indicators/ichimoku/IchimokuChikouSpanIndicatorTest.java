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
package org.ta4j.core.indicators.ichimoku;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

public class IchimokuChikouSpanIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    public IchimokuChikouSpanIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    private Bar bar(int i) {
        return new MockBar(i, this::numOf);
    }

    private BarSeries barSeries(int count) {
        final List<Bar> bars = IntStream.range(0, count).boxed().map(this::bar).collect(toList());
        return new BaseBarSeries(bars);
    }

    @Test
    public void testCalculateWithDefaultParam() {
        final BarSeries barSeries = barSeries(27);

        final IchimokuChikouSpanIndicator indicator = new IchimokuChikouSpanIndicator(barSeries);

        assertEquals(numOf(26), indicator.getValue(0));
        assertEquals(NaN.NaN, indicator.getValue(1));
        assertEquals(NaN.NaN, indicator.getValue(2));
        assertEquals(NaN.NaN, indicator.getValue(3));
        assertEquals(NaN.NaN, indicator.getValue(4));
        assertEquals(NaN.NaN, indicator.getValue(5));
        assertEquals(NaN.NaN, indicator.getValue(6));
        assertEquals(NaN.NaN, indicator.getValue(7));
        assertEquals(NaN.NaN, indicator.getValue(8));
        assertEquals(NaN.NaN, indicator.getValue(9));
        assertEquals(NaN.NaN, indicator.getValue(10));
        assertEquals(NaN.NaN, indicator.getValue(11));
        assertEquals(NaN.NaN, indicator.getValue(12));
        assertEquals(NaN.NaN, indicator.getValue(13));
        assertEquals(NaN.NaN, indicator.getValue(14));
        assertEquals(NaN.NaN, indicator.getValue(15));
        assertEquals(NaN.NaN, indicator.getValue(16));
        assertEquals(NaN.NaN, indicator.getValue(17));
        assertEquals(NaN.NaN, indicator.getValue(18));
        assertEquals(NaN.NaN, indicator.getValue(19));
        assertEquals(NaN.NaN, indicator.getValue(20));
        assertEquals(NaN.NaN, indicator.getValue(21));
        assertEquals(NaN.NaN, indicator.getValue(22));
        assertEquals(NaN.NaN, indicator.getValue(23));
        assertEquals(NaN.NaN, indicator.getValue(24));
        assertEquals(NaN.NaN, indicator.getValue(25));
        assertEquals(NaN.NaN, indicator.getValue(26));
    }

    @Test
    public void testCalculateWithSpecifiedValue() {
        final BarSeries barSeries = barSeries(11);

        final IchimokuChikouSpanIndicator indicator = new IchimokuChikouSpanIndicator(barSeries, 3);

        assertEquals(numOf(3), indicator.getValue(0));
        assertEquals(numOf(4), indicator.getValue(1));
        assertEquals(numOf(5), indicator.getValue(2));
        assertEquals(numOf(6), indicator.getValue(3));
        assertEquals(numOf(7), indicator.getValue(4));
        assertEquals(numOf(8), indicator.getValue(5));
        assertEquals(numOf(9), indicator.getValue(6));
        assertEquals(numOf(10), indicator.getValue(7));
        assertEquals(NaN.NaN, indicator.getValue(8));
        assertEquals(NaN.NaN, indicator.getValue(9));
        assertEquals(NaN.NaN, indicator.getValue(10));
    }

}