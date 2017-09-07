/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package eu.verdelhan.ta4j;

import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.trading.rules.FixedRule;
import java.util.LinkedList;
import java.util.List;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class TimeSeriesTest {

    private TimeSeries defaultSeries;
    
    private TimeSeries constrainedSeries;

    private TimeSeries emptySeries;

    private Strategy strategy;

    private List<Tick> ticks;

    private String defaultName;

    @Before
    public void setUp() {
        ticks = new LinkedList<>();
        ticks.add(new MockTick(ZonedDateTime.of(2014, 6, 13, 0, 0, 0, 0, ZoneId.systemDefault()), 1d));
        ticks.add(new MockTick(ZonedDateTime.of(2014, 6, 14, 0, 0, 0, 0, ZoneId.systemDefault()), 2d));
        ticks.add(new MockTick(ZonedDateTime.of(2014, 6, 15, 0, 0, 0, 0, ZoneId.systemDefault()), 3d));
        ticks.add(new MockTick(ZonedDateTime.of(2014, 6, 20, 0, 0, 0, 0, ZoneId.systemDefault()), 4d));
        ticks.add(new MockTick(ZonedDateTime.of(2014, 6, 25, 0, 0, 0, 0, ZoneId.systemDefault()), 5d));
        ticks.add(new MockTick(ZonedDateTime.of(2014, 6, 30, 0, 0, 0, 0, ZoneId.systemDefault()), 6d));

        defaultName = "Series Name";

        defaultSeries = new BaseTimeSeries(defaultName, ticks);
        constrainedSeries = new BaseTimeSeries(defaultSeries, 2, 4);
        emptySeries = new BaseTimeSeries();

        strategy = new BaseStrategy(new FixedRule(0, 2, 3, 6), new FixedRule(1, 4, 7, 8));
        strategy.setUnstablePeriod(2); // Strategy would need a real test class
    }

    @Test
    public void getEndGetBeginGetTickCountIsEmpty() {
        // Default series
        assertEquals(0, defaultSeries.getBeginIndex());
        assertEquals(ticks.size() - 1, defaultSeries.getEndIndex());
        assertEquals(ticks.size(), defaultSeries.getTickCount());
        assertFalse(defaultSeries.isEmpty());
        // Constrained series
        assertEquals(2, constrainedSeries.getBeginIndex());
        assertEquals(4, constrainedSeries.getEndIndex());
        assertEquals(3, constrainedSeries.getTickCount());
        assertFalse(constrainedSeries.isEmpty());
        // Empty series
        assertEquals(-1, emptySeries.getBeginIndex());
        assertEquals(-1, emptySeries.getEndIndex());
        assertEquals(0, emptySeries.getTickCount());
        assertTrue(emptySeries.isEmpty());
    }

    @Test
    public void getTickData() {
        // Default series
        assertEquals(ticks, defaultSeries.getTickData());
        // Constrained series
        assertEquals(ticks, constrainedSeries.getTickData());
        // Empty series
        assertEquals(0, emptySeries.getTickData().size());
    }

    @Test
    public void getSeriesPeriodDescription() {
        // Default series
        assertTrue(defaultSeries.getSeriesPeriodDescription().endsWith(ticks.get(defaultSeries.getEndIndex()).getEndTime().format(DateTimeFormatter.ISO_DATE_TIME)));
        assertTrue(defaultSeries.getSeriesPeriodDescription().startsWith(ticks.get(defaultSeries.getBeginIndex()).getEndTime().format(DateTimeFormatter.ISO_DATE_TIME)));
        // Constrained series
        assertTrue(constrainedSeries.getSeriesPeriodDescription().endsWith(ticks.get(constrainedSeries.getEndIndex()).getEndTime().format(DateTimeFormatter.ISO_DATE_TIME)));
        assertTrue(constrainedSeries.getSeriesPeriodDescription().startsWith(ticks.get(constrainedSeries.getBeginIndex()).getEndTime().format(DateTimeFormatter.ISO_DATE_TIME)));
        // Empty series
        assertEquals("", emptySeries.getSeriesPeriodDescription());
    }

    @Test
    public void getName() {
        assertEquals(defaultName, defaultSeries.getName());
        assertEquals(defaultName, constrainedSeries.getName());
    }

    @Test
    public void getTickWithRemovedIndexOnMovingSeriesShouldReturnFirstRemainingTick() {
        Tick tick = defaultSeries.getTick(4);
        defaultSeries.setMaximumTickCount(2);
        
        assertSame(tick, defaultSeries.getTick(0));
        assertSame(tick, defaultSeries.getTick(1));
        assertSame(tick, defaultSeries.getTick(2));
        assertSame(tick, defaultSeries.getTick(3));
        assertSame(tick, defaultSeries.getTick(4));
        assertNotSame(tick, defaultSeries.getTick(5));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getTickOnMovingAndEmptySeriesShouldThrowException() {
        defaultSeries.setMaximumTickCount(2);
        ticks.clear(); // Should not be used like this
        defaultSeries.getTick(1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getTickWithNegativeIndexShouldThrowException() {
        defaultSeries.getTick(-1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getTickWithIndexGreaterThanTickCountShouldThrowException() {
        defaultSeries.getTick(10);
    }

    @Test
    public void getTickOnMovingSeries() {
        Tick tick = defaultSeries.getTick(4);
        defaultSeries.setMaximumTickCount(2);
        assertEquals(tick, defaultSeries.getTick(4));
    }

    @Test
    public void constrainedSeriesWithIndexes() {
        TimeSeries constrSeries = new BaseTimeSeries(defaultSeries, 2, 5);
        assertEquals(defaultSeries.getName(), constrSeries.getName());
        assertEquals(2, constrSeries.getBeginIndex());
        assertNotEquals(defaultSeries.getBeginIndex(), constrSeries.getBeginIndex());
        assertEquals(5, constrSeries.getEndIndex());
        assertEquals(defaultSeries.getEndIndex(), constrSeries.getEndIndex());
        assertEquals(4, constrSeries.getTickCount());
    }

    @Test(expected = IllegalStateException.class)
    public void constrainedSeriesOnSeriesWithMaximumTickCountShouldThrowException() {
        defaultSeries.setMaximumTickCount(3);
        new BaseTimeSeries(defaultSeries, 0, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constrainedSeriesWithInvalidIndexesShouldThrowException() {
        new BaseTimeSeries(defaultSeries, 4, 2);
    }
    
    @Test(expected = IllegalStateException.class)
    public void maximumTickCountOnConstrainedSeriesShouldThrowException() {
        constrainedSeries.setMaximumTickCount(10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeMaximumTickCountShouldThrowException() {
        defaultSeries.setMaximumTickCount(-1);
    }

    @Test
    public void setMaximumTickCount() {
        // Before
        assertEquals(0, defaultSeries.getBeginIndex());
        assertEquals(ticks.size() - 1, defaultSeries.getEndIndex());
        assertEquals(ticks.size(), defaultSeries.getTickCount());

        defaultSeries.setMaximumTickCount(3);

        // After
        assertEquals(0, defaultSeries.getBeginIndex());
        assertEquals(5, defaultSeries.getEndIndex());
        assertEquals(3, defaultSeries.getTickCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void addNullTickShouldThrowException() {
        defaultSeries.addTick(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void addTickWithEndTimePriorToSeriesEndTimeShouldThrowException() {
        defaultSeries.addTick(new MockTick(ZonedDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault()), 99d));
    }
    
    @Test
    public void addTick() {
        defaultSeries = new BaseTimeSeries();
        Tick firstTick = new MockTick(ZonedDateTime.of(2014, 6, 13, 0, 0, 0, 0, ZoneId.systemDefault()), 1d);
        Tick secondTick = new MockTick(ZonedDateTime.of(2014, 6, 14, 0, 0, 0, 0, ZoneId.systemDefault()), 2d);

        assertEquals(0, defaultSeries.getTickCount());
        assertEquals(-1, defaultSeries.getBeginIndex());
        assertEquals(-1, defaultSeries.getEndIndex());

        defaultSeries.addTick(firstTick);
        assertEquals(1, defaultSeries.getTickCount());
        assertEquals(0, defaultSeries.getBeginIndex());
        assertEquals(0, defaultSeries.getEndIndex());

        defaultSeries.addTick(secondTick);
        assertEquals(2, defaultSeries.getTickCount());
        assertEquals(0, defaultSeries.getBeginIndex());
        assertEquals(1, defaultSeries.getEndIndex());
    }
}
