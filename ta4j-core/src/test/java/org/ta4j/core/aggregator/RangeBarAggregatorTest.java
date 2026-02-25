/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.aggregator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.util.List;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class RangeBarAggregatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    public RangeBarAggregatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void aggregateTrendingSeriesByRange() {
        List<Bar> bars = AggregatorTestFixtures.trendingBars(numFactory);
        RangeBarAggregator aggregator = new RangeBarAggregator(6d);

        List<Bar> aggregated = aggregator.aggregate(bars);

        assertEquals(2, aggregated.size());
        Bar firstBar = aggregated.getFirst();
        assertNumEquals(100d, firstBar.getOpenPrice());
        assertNumEquals(106d, firstBar.getHighPrice());
        assertNumEquals(99d, firstBar.getLowPrice());
        assertNumEquals(105d, firstBar.getClosePrice());
        assertNumEquals(83d, firstBar.getVolume());
        assertEquals(Duration.ofMinutes(3), firstBar.getTimePeriod());

        Bar secondBar = aggregated.get(1);
        assertNumEquals(105d, secondBar.getOpenPrice());
        assertNumEquals(110d, secondBar.getHighPrice());
        assertNumEquals(104d, secondBar.getLowPrice());
        assertNumEquals(109d, secondBar.getClosePrice());
        assertNumEquals(48d, secondBar.getVolume());
        assertEquals(Duration.ofMinutes(2), secondBar.getTimePeriod());
    }

    @Test
    public void aggregateVolatileSeriesByRange() {
        List<Bar> bars = AggregatorTestFixtures.volatileBars(numFactory);
        RangeBarAggregator aggregator = new RangeBarAggregator(10d);

        List<Bar> aggregated = aggregator.aggregate(bars);

        assertEquals(6, aggregated.size());
        assertNumEquals(100d, aggregated.getFirst().getOpenPrice());
        assertNumEquals(96d, aggregated.getFirst().getClosePrice());
        assertNumEquals(118d, aggregated.getLast().getClosePrice());
    }

    @Test
    public void aggregateFlatSeriesKeepsPendingBarWhenConfigured() {
        List<Bar> bars = AggregatorTestFixtures.flatBars(numFactory);
        RangeBarAggregator aggregator = new RangeBarAggregator(4d, false);

        List<Bar> aggregated = aggregator.aggregate(bars);

        assertEquals(1, aggregated.size());
        Bar pendingBar = aggregated.getFirst();
        assertNumEquals(100d, pendingBar.getOpenPrice());
        assertNumEquals(101d, pendingBar.getHighPrice());
        assertNumEquals(99d, pendingBar.getLowPrice());
        assertNumEquals(100d, pendingBar.getClosePrice());
        assertNumEquals(120d, pendingBar.getVolume());
        assertEquals(Duration.ofMinutes(6), pendingBar.getTimePeriod());
    }

    @Test
    public void aggregateRejectsUnevenIntervals() {
        List<Bar> bars = AggregatorTestFixtures.unevenIntervalBars(numFactory);
        RangeBarAggregator aggregator = new RangeBarAggregator(2d);

        assertThrows(IllegalArgumentException.class, () -> aggregator.aggregate(bars));
    }
}
