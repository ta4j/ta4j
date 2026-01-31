/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.aggregator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class BaseBarSeriesAggregatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    private final BaseBarSeriesAggregator baseBarSeriesAggregator = new BaseBarSeriesAggregator(
            new BarAggregatorForTest());

    public BaseBarSeriesAggregatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void testAggregateWithNewName() {
        final BarSeries barSeries = new MockBarSeriesBuilder().withName("name").build();
        final Instant time = Instant.parse("2019-06-12T04:01:00Z");

        var bar0 = barSeries.barBuilder()
                .endTime(time)
                .openPrice(1d)
                .closePrice(2d)
                .highPrice(3d)
                .lowPrice(4d)
                .volume(5d)
                .amount(6d)
                .trades(7)
                .build();
        var bar1 = barSeries.barBuilder()
                .endTime(time.plus(Duration.ofDays(1)))
                .openPrice(2d)
                .closePrice(3d)
                .highPrice(3d)
                .lowPrice(4d)
                .volume(5d)
                .amount(6d)
                .trades(7)
                .build();
        var bar2 = barSeries.barBuilder()
                .endTime(time.plus(Duration.ofDays(2)))
                .openPrice(3d)
                .closePrice(4d)
                .highPrice(4d)
                .lowPrice(5d)
                .volume(6d)
                .amount(7d)
                .trades(7)
                .build();
        barSeries.addBar(bar0);
        barSeries.addBar(bar1);
        barSeries.addBar(bar2);

        final BarSeries aggregated = baseBarSeriesAggregator.aggregate(barSeries, "newName");

        assertEquals("newName", aggregated.getName());
        assertEquals(2, aggregated.getBarCount());
        assertSame(bar0, aggregated.getBar(0));
        assertSame(bar2, aggregated.getBar(1));
    }

    @Test
    public void testAggregateWithTheSameName() {
        final BarSeries barSeries = new MockBarSeriesBuilder().withName("name").build();
        final Instant time = Instant.parse("2019-06-12T04:01:00Z");

        var bar0 = barSeries.barBuilder()
                .endTime(time)
                .openPrice(1d)
                .closePrice(2d)
                .highPrice(3d)
                .lowPrice(4d)
                .volume(5d)
                .amount(6d)
                .trades(7)
                .build();
        var bar1 = barSeries.barBuilder()
                .endTime(time.plus(Duration.ofDays(1)))
                .openPrice(2d)
                .closePrice(3d)
                .highPrice(3d)
                .lowPrice(4d)
                .volume(5d)
                .amount(6d)
                .trades(7)
                .build();
        var bar2 = barSeries.barBuilder()
                .endTime(time.plus(Duration.ofDays(2)))
                .openPrice(3d)
                .closePrice(4d)
                .highPrice(4d)
                .lowPrice(5d)
                .volume(6d)
                .amount(7d)
                .trades(7)
                .build();
        barSeries.addBar(bar0);
        barSeries.addBar(bar1);
        barSeries.addBar(bar2);

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
