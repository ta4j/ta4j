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
package org.ta4j.core.aggregator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.num.Num;

public class BaseBarSeriesAggregatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    private final BaseBarSeriesAggregator baseBarSeriesAggregator = new BaseBarSeriesAggregator(
            new BarAggregatorForTest());

    public BaseBarSeriesAggregatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Test
    public void testAggregateWithNewName() {
        final List<Bar> bars = new LinkedList<>();
        final ZonedDateTime time = ZonedDateTime.of(2019, 6, 12, 4, 1, 0, 0, ZoneId.systemDefault());

        final Bar bar0 = new MockBar(time, 1d, 2d, 3d, 4d, 5d, 6d, 7, numFunction);
        final Bar bar1 = new MockBar(time.plusDays(1), 2d, 3d, 3d, 4d, 5d, 6d, 7, numFunction);
        final Bar bar2 = new MockBar(time.plusDays(2), 3d, 4d, 4d, 5d, 6d, 7d, 7, numFunction);
        bars.add(bar0);
        bars.add(bar1);
        bars.add(bar2);

        final BarSeries barSeries = new BaseBarSeries("name", bars);

        final BarSeries aggregated = baseBarSeriesAggregator.aggregate(barSeries, "newName");

        assertEquals("newName", aggregated.getName());
        assertEquals(2, aggregated.getBarCount());
        assertSame(bar0, aggregated.getBar(0));
        assertSame(bar2, aggregated.getBar(1));
    }

    @Test
    public void testAggregateWithTheSameName() {
        final List<Bar> bars = new LinkedList<>();
        final ZonedDateTime time = ZonedDateTime.of(2019, 6, 12, 4, 1, 0, 0, ZoneId.systemDefault());

        final Bar bar0 = new MockBar(time, 1d, 2d, 3d, 4d, 5d, 6d, 7, numFunction);
        final Bar bar1 = new MockBar(time.plusDays(1), 2d, 3d, 3d, 4d, 5d, 6d, 7, numFunction);
        final Bar bar2 = new MockBar(time.plusDays(2), 3d, 4d, 4d, 5d, 6d, 7d, 7, numFunction);
        bars.add(bar0);
        bars.add(bar1);
        bars.add(bar2);

        final BarSeries barSeries = new BaseBarSeries("name", bars);

        final BarSeries aggregated = baseBarSeriesAggregator.aggregate(barSeries);

        assertEquals("name", aggregated.getName());
        assertEquals(2, aggregated.getBarCount());
        assertSame(bar0, aggregated.getBar(0));
        assertSame(bar2, aggregated.getBar(1));
    }

    /**
     * This bar aggregator created only for test purposes is returning first and
     * last bar.
     */
    private static class BarAggregatorForTest implements BarAggregator {
        @Override
        public List<Bar> aggregate(List<Bar> bars) {
            final List<Bar> aggregated = new ArrayList<>();
            if (bars.isEmpty()) {
                return aggregated;
            }
            int lastBarIndex = bars.size() - 1;

            aggregated.add(bars.get(0));
            aggregated.add(bars.get(lastBarIndex));
            return aggregated;
        }
    }
}