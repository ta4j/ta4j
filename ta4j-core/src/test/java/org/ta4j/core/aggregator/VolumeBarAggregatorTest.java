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

public class VolumeBarAggregatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    public VolumeBarAggregatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void aggregateTrendingSeriesByVolumeThreshold() {
        List<Bar> bars = AggregatorTestFixtures.trendingBars(numFactory);
        VolumeBarAggregator aggregator = new VolumeBarAggregator(100d);

        List<Bar> aggregated = aggregator.aggregate(bars);

        assertEquals(1, aggregated.size());
        Bar firstBar = aggregated.getFirst();
        assertNumEquals(100d, firstBar.getOpenPrice());
        assertNumEquals(108d, firstBar.getHighPrice());
        assertNumEquals(99d, firstBar.getLowPrice());
        assertNumEquals(107d, firstBar.getClosePrice());
        assertNumEquals(105d, firstBar.getVolume());
        assertEquals(Duration.ofMinutes(4), firstBar.getTimePeriod());
    }

    @Test
    public void aggregateVolatileSeriesByVolumeThreshold() {
        List<Bar> bars = AggregatorTestFixtures.volatileBars(numFactory);
        VolumeBarAggregator aggregator = new VolumeBarAggregator(100d);

        List<Bar> aggregated = aggregator.aggregate(bars);

        assertEquals(2, aggregated.size());
        Bar firstBar = aggregated.getFirst();
        assertNumEquals(100d, firstBar.getOpenPrice());
        assertNumEquals(112d, firstBar.getHighPrice());
        assertNumEquals(90d, firstBar.getLowPrice());
        assertNumEquals(92d, firstBar.getClosePrice());
        assertNumEquals(120d, firstBar.getVolume());

        Bar secondBar = aggregated.get(1);
        assertNumEquals(92d, secondBar.getOpenPrice());
        assertNumEquals(118d, secondBar.getHighPrice());
        assertNumEquals(88d, secondBar.getLowPrice());
        assertNumEquals(90d, secondBar.getClosePrice());
        assertNumEquals(105d, secondBar.getVolume());
    }

    @Test
    public void aggregateFlatSeriesKeepsPendingBarWhenConfigured() {
        List<Bar> bars = AggregatorTestFixtures.flatBars(numFactory);
        VolumeBarAggregator aggregator = new VolumeBarAggregator(200d, false);

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
    public void aggregateFlatSeriesDropsPendingBarByDefault() {
        List<Bar> bars = AggregatorTestFixtures.flatBars(numFactory);
        VolumeBarAggregator aggregator = new VolumeBarAggregator(200d);

        List<Bar> aggregated = aggregator.aggregate(bars);

        assertEquals(0, aggregated.size());
    }

    @Test
    public void aggregateRejectsUnevenIntervals() {
        List<Bar> bars = AggregatorTestFixtures.unevenIntervalBars(numFactory);
        VolumeBarAggregator aggregator = new VolumeBarAggregator(20d);

        assertThrows(IllegalArgumentException.class, () -> aggregator.aggregate(bars));
    }

    @Test
    public void aggregateRejectsInconsistentSourceTimePeriods() {
        List<Bar> bars = AggregatorTestFixtures.inconsistentPeriodBars(numFactory);
        VolumeBarAggregator aggregator = new VolumeBarAggregator(20d);

        assertThrows(IllegalArgumentException.class, () -> aggregator.aggregate(bars));
    }

    @Test
    public void aggregateEmptyBarsReturnsEmptyList() {
        VolumeBarAggregator aggregator = new VolumeBarAggregator(20d);

        List<Bar> aggregated = aggregator.aggregate(List.of());

        assertEquals(0, aggregated.size());
    }

    @Test
    public void constructorRejectsInvalidVolumeThreshold() {
        assertThrows(NullPointerException.class, () -> new VolumeBarAggregator(null));
        assertThrows(IllegalArgumentException.class, () -> new VolumeBarAggregator(0d));
        assertThrows(IllegalArgumentException.class, () -> new VolumeBarAggregator(-1d));
        assertThrows(IllegalArgumentException.class, () -> new VolumeBarAggregator(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new VolumeBarAggregator(Double.NEGATIVE_INFINITY));
    }
}
