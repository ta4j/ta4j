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
package org.ta4j.core.aggregator;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class DurationBarAggregatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    public DurationBarAggregatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    private List<Bar> getOneDayBars() {
        final List<Bar> bars = new LinkedList<>();
        final Instant time = Instant.parse("2019-06-12T04:01:00Z");

        // days 1 - 5
        bars.add(new MockBarBuilder(numFactory).endTime(time)
                .openPrice(1d)
                .closePrice(2d)
                .highPrice(3d)
                .lowPrice(4d)
                .amount(5d)
                .volume(6d)
                .trades(7)
                .build());
        bars.add(new MockBarBuilder(numFactory).endTime(time.plus(Duration.ofDays(1)))
                .openPrice(2d)
                .closePrice(3d)
                .highPrice(3d)
                .lowPrice(4d)
                .amount(5d)
                .volume(6d)
                .trades(7)
                .build());
        bars.add(new MockBarBuilder(numFactory).endTime(time.plus(Duration.ofDays(2)))
                .openPrice(3d)
                .closePrice(4d)
                .highPrice(4d)
                .lowPrice(5d)
                .amount(6d)
                .volume(7d)
                .trades(7)
                .build());
        bars.add(new MockBarBuilder(numFactory).endTime(time.plus(Duration.ofDays(3)))
                .openPrice(4d)
                .closePrice(5d)
                .highPrice(6d)
                .lowPrice(5d)
                .amount(7d)
                .volume(8d)
                .trades(7)
                .build());
        bars.add(new MockBarBuilder(numFactory).endTime(time.plus(Duration.ofDays(4)))
                .openPrice(5d)
                .closePrice(9d)
                .highPrice(3d)
                .lowPrice(11d)
                .amount(2d)
                .volume(6d)
                .trades(7)
                .build());

        // days 6 - 10
        bars.add(new MockBarBuilder(numFactory).endTime(time.plus(Duration.ofDays(5)))
                .openPrice(6d)
                .closePrice(10d)
                .highPrice(9d)
                .lowPrice(4d)
                .amount(8d)
                .volume(3d)
                .trades(7)
                .build());
        bars.add(new MockBarBuilder(numFactory).endTime(time.plus(Duration.ofDays(6)))
                .openPrice(3d)
                .closePrice(3d)
                .highPrice(4d)
                .lowPrice(95d)
                .amount(21d)
                .volume(74d)
                .trades(7)
                .build());
        bars.add(new MockBarBuilder(numFactory).endTime(time.plus(Duration.ofDays(7)))
                .openPrice(4d)
                .closePrice(7d)
                .highPrice(63d)
                .lowPrice(59d)
                .amount(56d)
                .volume(89d)
                .trades(7)
                .build());
        bars.add(new MockBarBuilder(numFactory).endTime(time.plus(Duration.ofDays(8)))
                .openPrice(5d)
                .closePrice(93d)
                .highPrice(3d)
                .lowPrice(21d)
                .amount(29d)
                .volume(62d)
                .trades(7)
                .build());
        bars.add(new MockBarBuilder(numFactory).endTime(time.plus(Duration.ofDays(9)))
                .openPrice(6d)
                .closePrice(10d)
                .highPrice(91d)
                .lowPrice(43d)
                .amount(84d)
                .volume(32d)
                .trades(7)
                .build());

        // days 11 - 15
        bars.add(new MockBarBuilder(numFactory).endTime(time.plus(Duration.ofDays(10)))
                .openPrice(4d)
                .closePrice(10d)
                .highPrice(943d)
                .lowPrice(49d)
                .amount(8d)
                .volume(43d)
                .trades(7)
                .build());
        bars.add(new MockBarBuilder(numFactory).endTime(time.plus(Duration.ofDays(11)))
                .openPrice(3d)
                .closePrice(3d)
                .highPrice(43d)
                .lowPrice(92d)
                .amount(21d)
                .volume(784d)
                .trades(7)
                .build());
        bars.add(new MockBarBuilder(numFactory).endTime(time.plus(Duration.ofDays(12)))
                .openPrice(4d)
                .closePrice(74d)
                .highPrice(53d)
                .lowPrice(52d)
                .amount(56d)
                .volume(89d)
                .trades(7)
                .build());
        bars.add(new MockBarBuilder(numFactory).endTime(time.plus(Duration.ofDays(13)))
                .openPrice(5d)
                .closePrice(93d)
                .highPrice(31d)
                .lowPrice(221d)
                .amount(29d)
                .volume(62d)
                .trades(7)
                .build());
        bars.add(new MockBarBuilder(numFactory).endTime(time.plus(Duration.ofDays(14)))
                .openPrice(6d)
                .closePrice(10d)
                .highPrice(991d)
                .lowPrice(43d)
                .amount(84d)
                .volume(32d)
                .trades(7)
                .build());

        // day 16
        bars.add(new MockBarBuilder(numFactory).endTime(time.plus(Duration.ofDays(15)))
                .openPrice(6d)
                .closePrice(108d)
                .highPrice(1991d)
                .lowPrice(433d)
                .amount(847d)
                .volume(322d)
                .trades(7)
                .build());
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
        assertNumEquals(num1.getNumFactory().numOf(1), bar1.getOpenPrice());
        assertNumEquals(num1.getNumFactory().numOf(6), bar1.getHighPrice());
        assertNumEquals(num1.getNumFactory().numOf(4), bar1.getLowPrice());
        assertNumEquals(num1.getNumFactory().numOf(9), bar1.getClosePrice());
        assertNumEquals(num1.getNumFactory().numOf(33), bar1.getVolume());

        // bar 2 must have ohlcv (6, 91, 4, 10, 260)
        final Bar bar2 = bars.get(1);
        final Num num2 = bar2.getOpenPrice();
        assertNumEquals(num2.getNumFactory().numOf(6), bar2.getOpenPrice());
        assertNumEquals(num2.getNumFactory().numOf(91), bar2.getHighPrice());
        assertNumEquals(num2.getNumFactory().numOf(4), bar2.getLowPrice());
        assertNumEquals(num2.getNumFactory().numOf(10), bar2.getClosePrice());
        assertNumEquals(num2.getNumFactory().numOf(260), bar2.getVolume());

        // bar 3 must have ohlcv (1d, 6d, 4d, 9d, 25)
        Bar bar3 = bars.get(2);
        Num num3 = bar3.getOpenPrice();
        assertNumEquals(num3.getNumFactory().numOf(4), bar3.getOpenPrice());
        assertNumEquals(num3.getNumFactory().numOf(991), bar3.getHighPrice());
        assertNumEquals(num3.getNumFactory().numOf(43), bar3.getLowPrice());
        assertNumEquals(num3.getNumFactory().numOf(10), bar3.getClosePrice());
        assertNumEquals(num3.getNumFactory().numOf(1010), bar3.getVolume());
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
        assertNumEquals(num1.getNumFactory().numOf(1), bar1.getOpenPrice());
        assertNumEquals(num1.getNumFactory().numOf(91), bar1.getHighPrice());
        assertNumEquals(num1.getNumFactory().numOf(4), bar1.getLowPrice());
        assertNumEquals(num1.getNumFactory().numOf(10), bar1.getClosePrice());
        assertNumEquals(num1.getNumFactory().numOf(293), bar1.getVolume());
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
        Instant now = Instant.now();
        BarSeries barSeries = new BaseBarSeriesBuilder().withNumFactory(numFactory).build();

        barSeries.barBuilder()
                .timePeriod(Duration.ofMinutes(1))
                .endTime(now.plus(Duration.ofMinutes(1)))
                .openPrice(1)
                .highPrice(1)
                .closePrice(2)
                .lowPrice(1)
                .volume(1)
                .add();
        barSeries.barBuilder()
                .timePeriod(Duration.ofMinutes(1))
                .endTime(now.plus(Duration.ofMinutes(2)))
                .openPrice(1)
                .highPrice(1)
                .closePrice(3)
                .lowPrice(1)
                .volume(1)
                .add();
        ;
        barSeries.barBuilder()
                .timePeriod(Duration.ofMinutes(1))
                .endTime(now.plus(Duration.ofMinutes(60)))
                .openPrice(1)
                .highPrice(1)
                .closePrice(1)
                .lowPrice(1)
                .volume(1)
                .add();
        ;

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
