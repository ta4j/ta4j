/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.aggregator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.List;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
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
}
