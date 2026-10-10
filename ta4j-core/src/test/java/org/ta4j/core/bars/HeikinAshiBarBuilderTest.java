/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.bars;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HeikinAshiBarBuilderTest extends AbstractIndicatorTest<BarSeries, Num> {

    public HeikinAshiBarBuilderTest(NumFactory numFactory) {
        super(numFactory);
    }

    private final HeikinAshiBarBuilder unit = new HeikinAshiBarBuilder(numFactory);

    @Test
    public void testBuild() {
        var inputBar = new BaseBar(Duration.ofHours(1), null, Instant.parse("2024-01-01T01:00:00Z"),
                numFactory.numOf(100), numFactory.numOf(110), numFactory.numOf(95), numFactory.numOf(105),
                numFactory.numOf(10), numFactory.numOf(1000), 1);

        // No previous HA data: should return bar as-is.
        var resultBar = unit.timePeriod(inputBar.getTimePeriod())
                .endTime(inputBar.getEndTime())
                .openPrice(inputBar.getOpenPrice())
                .highPrice(inputBar.getHighPrice())
                .lowPrice(inputBar.getLowPrice())
                .closePrice(inputBar.getClosePrice())
                .volume(inputBar.getVolume())
                .amount(inputBar.getAmount())
                .trades(inputBar.getTrades())
                .build();

        assertEquals(inputBar.getOpenPrice(), resultBar.getOpenPrice());
        assertEquals(inputBar.getHighPrice(), resultBar.getHighPrice());
        assertEquals(inputBar.getLowPrice(), resultBar.getLowPrice());
        assertEquals(inputBar.getClosePrice(), resultBar.getClosePrice());

        // Setup for second bar with previous HA data
        var builderWithPrevious = new HeikinAshiBarBuilder().previousHeikinAshiOpenPrice(numFactory.numOf(100))
                .previousHeikinAshiClosePrice(numFactory.numOf(105))
                .timePeriod(inputBar.getTimePeriod())
                .endTime(inputBar.getEndTime())
                .openPrice(inputBar.getOpenPrice())
                .highPrice(inputBar.getHighPrice())
                .lowPrice(inputBar.getLowPrice())
                .closePrice(inputBar.getClosePrice())
                .volume(inputBar.getVolume())
                .amount(inputBar.getAmount())
                .trades(inputBar.getTrades());

        var haBar = builderWithPrevious.build();

        // Heikin-Ashi formula checks
        var haCloseExpected = (numFactory.numOf(100)
                .plus(numFactory.numOf(110))
                .plus(numFactory.numOf(95))
                .plus(numFactory.numOf(105))).dividedBy(numFactory.numOf(4));
        var haOpenExpected = (numFactory.numOf(100).plus(numFactory.numOf(105))).dividedBy(numFactory.numOf(2));

        assertEquals(haOpenExpected, haBar.getOpenPrice());
        assertEquals(haCloseExpected, haBar.getClosePrice());
    }

}