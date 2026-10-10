/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.aggregator;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HeikinAshiBarAggregatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    public HeikinAshiBarAggregatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    private final HeikinAshiBarAggregator unit = new HeikinAshiBarAggregator();

    @Test
    public void testAggregate() {
        var endTime = Instant.parse("2024-01-01T01:00:00Z");
        var timePeriod = Duration.ofHours(1);

        Bar bar1 = new BaseBar(timePeriod, null, endTime, numFactory.numOf(100), numFactory.numOf(105),
                numFactory.numOf(95), numFactory.numOf(100), numFactory.numOf(10), numFactory.numOf(50), 1);
        var bar2 = new BaseBar(timePeriod, null, endTime.plus(1, ChronoUnit.HOURS), numFactory.numOf(100),
                numFactory.numOf(110), numFactory.numOf(98), numFactory.numOf(105), numFactory.numOf(20),
                numFactory.numOf(100), 2);

        var ohlcBars = List.of(bar1, bar2);
        var haBars = unit.aggregate(ohlcBars);
        assertEquals(2, haBars.size());

        // First HA bar should be identical to the original bar, since no previous HA
        // data
        var firstHA = haBars.getFirst();
        assertEquals(bar1.getOpenPrice(), firstHA.getOpenPrice());
        assertEquals(bar1.getHighPrice(), firstHA.getHighPrice());
        assertEquals(bar1.getLowPrice(), firstHA.getLowPrice());
        assertEquals(bar1.getClosePrice(), firstHA.getClosePrice());

        // Second HA bar uses first bar’s HA open/close in the formula
        var secondHA = haBars.get(1);
        var haClose2Expected = bar2.getOpenPrice()
                .plus(bar2.getHighPrice())
                .plus(bar2.getLowPrice())
                .plus(bar2.getClosePrice())
                .dividedBy(numFactory.numOf(4));
        var haOpen2Expected = firstHA.getOpenPrice().plus(firstHA.getClosePrice()).dividedBy(numFactory.numOf(2));
        assertEquals(haOpen2Expected, secondHA.getOpenPrice());
        assertEquals(haClose2Expected, secondHA.getClosePrice());
    }

}