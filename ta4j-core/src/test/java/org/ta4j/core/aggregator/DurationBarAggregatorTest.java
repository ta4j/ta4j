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
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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

public class DurationBarAggregatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    public DurationBarAggregatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    private List<Bar> getOneDayBars() {
        final List<Bar> bars = new LinkedList<>();
        final ZonedDateTime time = ZonedDateTime.of(2019, 6, 12, 4, 1, 0, 0, ZoneId.systemDefault());

        // days 1 - 5
        bars.add(new MockBar(time, 1d, 2d, 3d, 4d, 5d, 6d, 7, numFunction));
        bars.add(new MockBar(time.plusDays(1), 2d, 3d, 3d, 4d, 5d, 6d, 7, numFunction));
        bars.add(new MockBar(time.plusDays(2), 3d, 4d, 4d, 5d, 6d, 7d, 7, numFunction));
        bars.add(new MockBar(time.plusDays(3), 4d, 5d, 6d, 5d, 7d, 8d, 7, numFunction));
        bars.add(new MockBar(time.plusDays(4), 5d, 9d, 3d, 11d, 2d, 6d, 7, numFunction));

        // days 6 - 10
        bars.add(new MockBar(time.plusDays(5), 6d, 10d, 9d, 4d, 8d, 3d, 7, numFunction));
        bars.add(new MockBar(time.plusDays(6), 3d, 3d, 4d, 95d, 21d, 74d, 7, numFunction));
        bars.add(new MockBar(time.plusDays(7), 4d, 7d, 63d, 59d, 56d, 89d, 7, numFunction));
        bars.add(new MockBar(time.plusDays(8), 5d, 93d, 3d, 21d, 29d, 62d, 7, numFunction));
        bars.add(new MockBar(time.plusDays(9), 6d, 10d, 91d, 43d, 84d, 32d, 7, numFunction));

        // days 11 - 15
        bars.add(new MockBar(time.plusDays(10), 4d, 10d, 943d, 49d, 8d, 43d, 7, numFunction));
        bars.add(new MockBar(time.plusDays(11), 3d, 3d, 43d, 92d, 21d, 784d, 7, numFunction));
        bars.add(new MockBar(time.plusDays(12), 4d, 74d, 53d, 52d, 56d, 89d, 7, numFunction));
        bars.add(new MockBar(time.plusDays(13), 5d, 93d, 31d, 221d, 29d, 62d, 7, numFunction));
        bars.add(new MockBar(time.plusDays(14), 6d, 10d, 991d, 43d, 84d, 32d, 7, numFunction));

        // day 16
        bars.add(new MockBar(time.plusDays(15), 6d, 108d, 1991d, 433d, 847d, 322d, 7, numFunction));
        return bars;
    }

    /**
     * Tests if the bars are upscaled correctly from 1day to 5day
     */
    @Test
    public void upscaledTo5DayBars() {
        final DurationBarAggregator barAggregator = new DurationBarAggregator(Duration.ofDays(5), true);

        final List<Bar> bars = barAggregator.aggregate(getOneDayBars());

        // must be 3 bars
        assertEquals(3, bars.size());

        // bar 1 must have ohlcv (1, 6, 4, 9, 25)
        final Bar bar1 = bars.get(0);
        final Num num1 = bar1.getOpenPrice();
        assertNumEquals(num1.numOf(1), bar1.getOpenPrice());
        assertNumEquals(num1.numOf(6), bar1.getHighPrice());
        assertNumEquals(num1.numOf(4), bar1.getLowPrice());
        assertNumEquals(num1.numOf(9), bar1.getClosePrice());
        assertNumEquals(num1.numOf(33), bar1.getVolume());

        // bar 2 must have ohlcv (6, 91, 4, 10, 260)
        final Bar bar2 = bars.get(1);
        final Num num2 = bar2.getOpenPrice();
        assertNumEquals(num2.numOf(6), bar2.getOpenPrice());
        assertNumEquals(num2.numOf(91), bar2.getHighPrice());
        assertNumEquals(num2.numOf(4), bar2.getLowPrice());
        assertNumEquals(num2.numOf(10), bar2.getClosePrice());
        assertNumEquals(num2.numOf(260), bar2.getVolume());

        // bar 3 must have ohlcv (1d, 6d, 4d, 9d, 25)
        Bar bar3 = bars.get(2);
        Num num3 = bar3.getOpenPrice();
        assertNumEquals(num3.numOf(4), bar3.getOpenPrice());
        assertNumEquals(num3.numOf(991), bar3.getHighPrice());
        assertNumEquals(num3.numOf(43), bar3.getLowPrice());
        assertNumEquals(num3.numOf(10), bar3.getClosePrice());
        assertNumEquals(num3.numOf(1010), bar3.getVolume());
    }

    /**
     * Tests if the bars are upscaled correctly from 1day to 10day
     */
    @Test
    public void upscaledTo10DayBars() {
        final DurationBarAggregator barAggregator = new DurationBarAggregator(Duration.ofDays(10), true);
        final List<Bar> bars = barAggregator.aggregate(getOneDayBars());

        // must be 1 bars
        assertEquals(1, bars.size());

        // bar 1 must have ohlcv (1, 91, 4, 10, 293)
        final Bar bar1 = bars.get(0);
        final Num num1 = bar1.getOpenPrice();
        assertNumEquals(num1.numOf(1), bar1.getOpenPrice());
        assertNumEquals(num1.numOf(91), bar1.getHighPrice());
        assertNumEquals(num1.numOf(4), bar1.getLowPrice());
        assertNumEquals(num1.numOf(10), bar1.getClosePrice());
        assertNumEquals(num1.numOf(293), bar1.getVolume());
    }

    /**
     * Tests if the bars are upscaled correctly from 1day to 10day, allowed not
     * final bars too
     */
    @Test
    public void upscaledTo10DayBarsNotOnlyFinalBars() {
        final DurationBarAggregator barAggregator = new DurationBarAggregator(Duration.ofDays(10), false);
        final List<Bar> bars = barAggregator.aggregate(getOneDayBars());

        // must be 2 bars
        assertEquals(2, bars.size());
    }

    @Test
    public void testWithGapsInSeries() {
        ZonedDateTime now = ZonedDateTime.now();
        BarSeries barSeries = new BaseBarSeries();

        barSeries.addBar(Duration.ofMinutes(1), now.plusMinutes(1), 1, 1, 1, 2, 1);
        barSeries.addBar(Duration.ofMinutes(1), now.plusMinutes(2), 1, 1, 1, 3, 1);
        barSeries.addBar(Duration.ofMinutes(1), now.plusMinutes(60), 1, 1, 1, 1, 1);

        BarSeries aggregated2MinSeries = new BaseBarSeriesAggregator(
                new DurationBarAggregator(Duration.ofMinutes(2), false)).aggregate(barSeries, "");
        BarSeries aggregated4MinSeries = new BaseBarSeriesAggregator(
                new DurationBarAggregator(Duration.ofMinutes(4), false)).aggregate(barSeries, "");

        assertEquals(2, aggregated2MinSeries.getBarCount());
        assertEquals(2, aggregated4MinSeries.getBarCount());

        assertNumEquals(3, aggregated2MinSeries.getBar(0).getClosePrice());
        assertNumEquals(3, aggregated4MinSeries.getBar(0).getClosePrice());

        assertNumEquals(2, aggregated2MinSeries.getBar(0).getVolume());
        assertNumEquals(2, aggregated4MinSeries.getBar(0).getVolume());

        assertNumEquals(1, aggregated2MinSeries.getBar(1).getClosePrice());
        assertNumEquals(1, aggregated4MinSeries.getBar(1).getClosePrice());

        assertNumEquals(1, aggregated2MinSeries.getBar(1).getVolume());
        assertNumEquals(1, aggregated4MinSeries.getBar(1).getVolume());

    }

}
