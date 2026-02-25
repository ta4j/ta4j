/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.aggregator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarBuilder;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class RenkoBarAggregatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    public RenkoBarAggregatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void aggregateTrendingSeriesCreatesAscendingBricks() {
        List<Bar> bars = AggregatorTestFixtures.barsFromClosePrices(numFactory, 100d, 101d, 103d, 105d, 107d, 109d);
        RenkoBarAggregator aggregator = new RenkoBarAggregator(2d, 2);

        List<Bar> aggregated = aggregator.aggregate(bars);

        assertEquals(4, aggregated.size());
        assertNumEquals(100d, aggregated.get(0).getOpenPrice());
        assertNumEquals(102d, aggregated.get(0).getClosePrice());
        assertNumEquals(102d, aggregated.get(1).getOpenPrice());
        assertNumEquals(104d, aggregated.get(1).getClosePrice());
        assertNumEquals(104d, aggregated.get(2).getOpenPrice());
        assertNumEquals(106d, aggregated.get(2).getClosePrice());
        assertNumEquals(106d, aggregated.get(3).getOpenPrice());
        assertNumEquals(108d, aggregated.get(3).getClosePrice());
    }

    @Test
    public void aggregateInitialDowntrendCreatesDescendingBricks() {
        List<Bar> bars = AggregatorTestFixtures.barsFromClosePrices(numFactory, 100d, 99d, 97d, 95d, 93d);
        RenkoBarAggregator aggregator = new RenkoBarAggregator(2d, 2);

        List<Bar> aggregated = aggregator.aggregate(bars);

        assertEquals(3, aggregated.size());
        assertNumEquals(98d, aggregated.get(0).getClosePrice());
        assertNumEquals(96d, aggregated.get(1).getClosePrice());
        assertNumEquals(94d, aggregated.get(2).getClosePrice());
    }

    @Test
    public void aggregateVolatileSeriesHandlesReversalAmount() {
        List<Bar> bars = AggregatorTestFixtures.barsFromClosePrices(numFactory, 100d, 104d, 99d, 95d, 101d, 107d);
        RenkoBarAggregator aggregator = new RenkoBarAggregator(2d, 2);

        List<Bar> aggregated = aggregator.aggregate(bars);
        double[] expectedCloses = new double[] { 102d, 104d, 102d, 100d, 98d, 96d, 98d, 100d, 102d, 104d, 106d };

        assertEquals(expectedCloses.length, aggregated.size());
        for (int i = 0; i < expectedCloses.length; i++) {
            assertNumEquals(expectedCloses[i], aggregated.get(i).getClosePrice());
        }
    }

    @Test
    public void aggregateRequiresConfiguredReversalDistanceBeforeDirectionChange() {
        List<Bar> bars = AggregatorTestFixtures.barsFromClosePrices(numFactory, 100d, 104d, 99d);
        RenkoBarAggregator aggregator = new RenkoBarAggregator(2d, 3);

        List<Bar> aggregated = aggregator.aggregate(bars);

        assertEquals(2, aggregated.size());
        assertNumEquals(102d, aggregated.get(0).getClosePrice());
        assertNumEquals(104d, aggregated.get(1).getClosePrice());
    }

    @Test
    public void aggregateAssignsMetricsToFirstBrickWhenMultipleBricksComeFromSingleSourceBar() {
        List<Bar> bars = AggregatorTestFixtures.barsFromClosePrices(numFactory, 100d, 106d);
        RenkoBarAggregator aggregator = new RenkoBarAggregator(2d, 2);

        List<Bar> aggregated = aggregator.aggregate(bars);

        assertEquals(3, aggregated.size());
        assertNumEquals(20d, aggregated.get(0).getVolume());
        assertNumEquals(numFactory.zero(), aggregated.get(1).getVolume());
        assertNumEquals(numFactory.zero(), aggregated.get(2).getVolume());
    }

    @Test
    public void aggregateFlatSeriesCreatesNoBricks() {
        List<Bar> bars = AggregatorTestFixtures.barsFromClosePrices(numFactory, 100d, 100.5d, 99.5d, 100d, 100.2d);
        RenkoBarAggregator aggregator = new RenkoBarAggregator(2d, 2);

        List<Bar> aggregated = aggregator.aggregate(bars);

        assertEquals(0, aggregated.size());
    }

    @Test
    public void aggregateRejectsUnevenIntervals() {
        List<Bar> bars = AggregatorTestFixtures.unevenIntervalBars(numFactory);
        RenkoBarAggregator aggregator = new RenkoBarAggregator(2d, 2);

        assertThrows(IllegalArgumentException.class, () -> aggregator.aggregate(bars));
    }

    @Test
    public void aggregateRejectsInconsistentSourceTimePeriods() {
        List<Bar> bars = AggregatorTestFixtures.inconsistentPeriodBars(numFactory);
        RenkoBarAggregator aggregator = new RenkoBarAggregator(2d, 2);

        assertThrows(IllegalArgumentException.class, () -> aggregator.aggregate(bars));
    }

    @Test
    public void aggregateRejectsNullSourceBars() {
        List<Bar> bars = new ArrayList<>(AggregatorTestFixtures.trendingBars(numFactory));
        bars.set(3, null);
        RenkoBarAggregator aggregator = new RenkoBarAggregator(2d, 2);

        assertThrows(IllegalArgumentException.class, () -> aggregator.aggregate(bars));
    }

    @Test
    public void aggregateRejectsMissingClosePrice() {
        Instant endTime = Instant.parse("2026-01-02T00:01:00Z");
        Bar firstBar = new MockBarBuilder(numFactory).timePeriod(Duration.ofMinutes(1))
                .endTime(endTime)
                .openPrice(100d)
                .highPrice(101d)
                .lowPrice(99d)
                .closePrice(100d)
                .volume(10d)
                .amount(1000d)
                .trades(1)
                .build();
        Bar secondBar = new MockBarBuilder(numFactory).timePeriod(Duration.ofMinutes(1))
                .endTime(endTime.plus(Duration.ofMinutes(1)))
                .openPrice(100d)
                .highPrice(102d)
                .lowPrice(98d)
                .closePrice((Num) null)
                .volume(10d)
                .amount(1000d)
                .trades(1)
                .build();
        RenkoBarAggregator aggregator = new RenkoBarAggregator(2d, 2);

        assertThrows(IllegalArgumentException.class, () -> aggregator.aggregate(List.of(firstBar, secondBar)));
    }

    @Test
    public void aggregateEmptyBarsReturnsEmptyList() {
        RenkoBarAggregator aggregator = new RenkoBarAggregator(2d, 2);

        List<Bar> aggregated = aggregator.aggregate(List.of());

        assertEquals(0, aggregated.size());
    }

    @Test
    public void constructorRejectsInvalidParameters() {
        assertThrows(NullPointerException.class, () -> new RenkoBarAggregator(null, 2));
        assertThrows(IllegalArgumentException.class, () -> new RenkoBarAggregator(0d, 2));
        assertThrows(IllegalArgumentException.class, () -> new RenkoBarAggregator(-1d, 2));
        assertThrows(IllegalArgumentException.class, () -> new RenkoBarAggregator(Double.NaN, 2));
        assertThrows(IllegalArgumentException.class, () -> new RenkoBarAggregator(1d, 0));
        assertThrows(IllegalArgumentException.class, () -> new RenkoBarAggregator(1d, -1));
    }
}
