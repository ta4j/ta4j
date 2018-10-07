package org.ta4j.core;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.Num;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.function.Function;

import static org.junit.Assert.*;
import static org.ta4j.core.TestUtils.assertNumEquals;

public class BarTest extends AbstractIndicatorTest {

    private Bar bar;

    private ZonedDateTime beginTime;

    private ZonedDateTime endTime;

    public BarTest(Function<Number, Num> numFunction) {
        super(null, numFunction);
    }

    @Before
    public void setUp() {
        beginTime = ZonedDateTime.of(2014, 6, 25, 0, 0, 0, 0, ZoneId.systemDefault());
        endTime = ZonedDateTime.of(2014, 6, 25, 1, 0, 0, 0, ZoneId.systemDefault());
        bar = new BaseBar(Duration.ofHours(1), endTime, numFunction);
    }

    @Test
    public void addTrades() {

        bar.addTrade(3.0, 200.0, numFunction);
        bar.addTrade(4.0, 201.0, numFunction);
        bar.addTrade(2.0, 198.0, numFunction);

        assertEquals(3, bar.getTrades());
        assertNumEquals(3 * 200 + 4 * 201 + 2 * 198, bar.getAmount());
        assertNumEquals(200, bar.getOpenPrice());
        assertNumEquals(198, bar.getClosePrice());
        assertNumEquals(198, bar.getLowPrice());
        assertNumEquals(201, bar.getHighPrice());
        assertNumEquals(9, bar.getVolume());
    }

    @Test
    public void getTimePeriod() {
        assertEquals(beginTime, bar.getEndTime().minus(bar.getTimePeriod()));
    }

    @Test
    public void getBeginTime() {
        assertEquals(beginTime, bar.getBeginTime());
    }

    @Test
    public void inPeriod() {
        assertFalse(bar.inPeriod(null));

        assertFalse(bar.inPeriod(beginTime.withDayOfMonth(24)));
        assertFalse(bar.inPeriod(beginTime.withDayOfMonth(26)));
        assertTrue(bar.inPeriod(beginTime.withMinute(30)));

        assertTrue(bar.inPeriod(beginTime));
        assertFalse(bar.inPeriod(endTime));
    }
}
