/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.Assert.*;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.bars.TimeBarBuilder;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class BarTest extends AbstractIndicatorTest<BarSeries, Num> {

    private Bar bar;

    private Instant beginTime;

    private Instant endTime;

    public BarTest(final NumFactory numFactory) {
        super(null, numFactory);
    }

    @Before
    public void setUp() {
        this.beginTime = Instant.parse("2014-06-25T00:00:00Z");
        this.endTime = Instant.parse("2014-06-25T01:00:00Z");
        this.bar = new TimeBarBuilder(this.numFactory).timePeriod(Duration.ofHours(1))
                .endTime(this.endTime)
                .volume(0)
                .amount(0)
                .build();
    }

    @Test
    public void createBars() {
        var barByBeginTime = new TimeBarBuilder(this.numFactory).timePeriod(Duration.ofHours(1))
                .beginTime(this.beginTime)
                .volume(0)
                .amount(0)
                .build();

        var barByEndTime = new TimeBarBuilder(this.numFactory).timePeriod(Duration.ofHours(1))
                .endTime(this.endTime)
                .volume(0)
                .amount(0)
                .build();

        var barByBeginTimeAndEndTime = new TimeBarBuilder(this.numFactory).timePeriod(Duration.ofHours(1))
                .beginTime(this.beginTime)
                .endTime(this.endTime)
                .volume(0)
                .amount(0)
                .build();

        var barWithoutTimePeriod = new TimeBarBuilder(this.numFactory).beginTime(this.beginTime)
                .endTime(this.endTime)
                .volume(0)
                .amount(0)
                .build();

        assertEquals(barByBeginTime.getBeginTime(), barByEndTime.getBeginTime());
        assertEquals(barByBeginTime.getEndTime(), barByEndTime.getEndTime());
        assertEquals(barByBeginTimeAndEndTime.getTimePeriod(), barWithoutTimePeriod.getTimePeriod());
        assertEquals(barByBeginTimeAndEndTime.getTimePeriod(), Duration.between(beginTime, endTime));
        assertNotEquals(barByBeginTimeAndEndTime.getTimePeriod(), Duration.between(endTime, beginTime));
        assertEquals(barWithoutTimePeriod.getTimePeriod(), Duration.between(beginTime, endTime));
    }

    @Test(expected = NullPointerException.class)
    @SuppressWarnings("unused")
    public void createBarsWithMissingBeginTime() {
        // TimePeriod is not given and cannot be computed due to missing beginTime.
        var bar = new TimeBarBuilder(this.numFactory).endTime(endTime).volume(0).amount(0).build();
    }

    @Test(expected = NullPointerException.class)
    @SuppressWarnings("unused")
    public void createBarsWithMissingEndTime() {
        // TimePeriod is not given and cannot be computed due to missing endTime.
        var bar = new TimeBarBuilder(this.numFactory).beginTime(beginTime).volume(0).amount(0).build();
    }

    @Test(expected = IllegalArgumentException.class)
    @SuppressWarnings("unused")
    public void createBarsWithInvalidTimePeriod() {
        var barByBeginTime = new TimeBarBuilder(this.numFactory).timePeriod(Duration.ofHours(2))
                .beginTime(this.beginTime)
                .endTime(this.endTime)
                .volume(0)
                .amount(0)
                .build();
    }

    @Test
    public void addTrades() {

        this.bar.addTrade(numOf(3.0), numOf(200.0));
        this.bar.addTrade(numOf(4.0), numOf(201.0));
        this.bar.addTrade(numOf(2.0), numOf(198.0));

        assertEquals(3, this.bar.getTrades());
        assertEquals(numOf(3 * 200 + 4 * 201 + 2 * 198), this.bar.getAmount());
        assertEquals(numOf(200), this.bar.getOpenPrice());
        assertEquals(numOf(198), this.bar.getClosePrice());
        assertEquals(numOf(198), this.bar.getLowPrice());
        assertEquals(numOf(201), this.bar.getHighPrice());
        assertEquals(numOf(9), this.bar.getVolume());
    }

    @Test
    public void getTimePeriod() {
        assertEquals(this.beginTime, this.bar.getEndTime().minus(this.bar.getTimePeriod()));
    }

    @Test
    public void getBeginTime() {
        assertEquals(this.beginTime, this.bar.getBeginTime());
    }

    @Test
    public void getDateName() {
        assertNotNull(bar.getDateName());
    }

    @Test
    public void getSimpleDateName() {
        assertNotNull(bar.getSimpleDateName());
    }

    @Test
    public void inPeriod() {
        assertFalse(this.bar.inPeriod(null));

        ZonedDateTime zonedBeginTime = beginTime.atZone(ZoneOffset.UTC);
        assertFalse(bar.inPeriod(zonedBeginTime.withDayOfMonth(24).toInstant()));
        assertFalse(bar.inPeriod(zonedBeginTime.withDayOfMonth(26).toInstant()));
        assertTrue(bar.inPeriod(zonedBeginTime.withMinute(30).toInstant()));

        assertTrue(this.bar.inPeriod(this.beginTime));
        assertFalse(this.bar.inPeriod(this.endTime));
    }

    @Test
    public void doesNotThrowNullPointerException() {
        var bar = new TimeBarBuilder().timePeriod(Duration.ofHours(1)).endTime(endTime).build();
        // TODO use Junit5: org.junit.jupiter.api.Assertions.assertDoesNotThrow instead:
        assertNotNull(bar.toString());
    }

    @Test
    public void equals() {
        final Bar bar1 = new TimeBarBuilder(this.numFactory).timePeriod(Duration.ofHours(1))
                .endTime(this.endTime)
                .build();
        final Bar bar2 = new TimeBarBuilder(this.numFactory).timePeriod(Duration.ofHours(1))
                .endTime(this.endTime)
                .build();

        assertEquals(bar1, bar2);
        assertNotSame(bar1, bar2);
    }

    @Test
    public void hashCode2() {
        final Bar bar1 = new TimeBarBuilder(this.numFactory).timePeriod(Duration.ofHours(1))
                .endTime(this.endTime)
                .build();
        final Bar bar2 = new TimeBarBuilder(this.numFactory).timePeriod(Duration.ofHours(1))
                .endTime(this.endTime)
                .build();

        assertEquals(bar1.hashCode(), bar2.hashCode());
    }

    @Test
    public void numFactoryPrefersOpenPrice() {
        var bar = new TimeBarBuilder(numFactory).timePeriod(Duration.ofSeconds(1))
                .beginTime(Instant.now())
                .openPrice(1)
                .closePrice(2)
                .build();

        assertSame(bar.getOpenPrice().getClass(), bar.numFactory().one().getClass());
    }

    @Test
    public void numFactoryFallsBackToClosePrice() {
        var bar = new TimeBarBuilder(numFactory).timePeriod(Duration.ofSeconds(1))
                .beginTime(Instant.now())
                .closePrice(2)
                .build();

        assertSame(bar.getClosePrice().getClass(), bar.numFactory().one().getClass());
    }

    @Test
    public void numFactoryThrowsWhenNoPricesAvailable() {
        var bar = new TimeBarBuilder(numFactory).timePeriod(Duration.ofSeconds(1)).beginTime(Instant.now()).build();

        assertThrows(IllegalArgumentException.class, bar::numFactory);
    }
}
