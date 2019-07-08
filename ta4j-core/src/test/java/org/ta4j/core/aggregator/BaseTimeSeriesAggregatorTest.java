package org.ta4j.core.aggregator;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.num.Num;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class BaseTimeSeriesAggregatorTest extends AbstractIndicatorTest<TimeSeries, Num> {

    private BaseTimeSeriesAggregator baseTimeSeriesAggregator = new BaseTimeSeriesAggregator(new BarAggregatorForTest());

    public BaseTimeSeriesAggregatorTest(Function<Number, Num> numFunction) {
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

        final TimeSeries timeSeries = new BaseTimeSeries("name", bars);

        final TimeSeries aggregated = baseTimeSeriesAggregator.aggregate(timeSeries, "newName");

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

        final TimeSeries timeSeries = new BaseTimeSeries("name", bars);

        final TimeSeries aggregated = baseTimeSeriesAggregator.aggregate(timeSeries);

        assertEquals("name", aggregated.getName());
        assertEquals(2, aggregated.getBarCount());
        assertSame(bar0, aggregated.getBar(0));
        assertSame(bar2, aggregated.getBar(1));
    }

    /**
     * This bar aggregator created only for test purposes is returning first and last bar.
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