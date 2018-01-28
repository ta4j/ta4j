/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan, Ta4j Organization & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.trading.rules.FixedRule;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;

public class TimeSeriesTest {

    private TimeSeries defaultSeries;

    private TimeSeries constrainedSeries;

    private TimeSeries emptySeries;

    private List<Bar> bars;

    private String defaultName;

    @SuppressWarnings("deprecation") // it is purposed to test the deprecated sub series creation
    @Before
    public void setUp() {
        bars = new LinkedList<>();
        bars.add(new MockBar(ZonedDateTime.of(2014, 6, 13, 0, 0, 0, 0, ZoneId.systemDefault()), 1d));
        bars.add(new MockBar(ZonedDateTime.of(2014, 6, 14, 0, 0, 0, 0, ZoneId.systemDefault()), 2d));
        bars.add(new MockBar(ZonedDateTime.of(2014, 6, 15, 0, 0, 0, 0, ZoneId.systemDefault()), 3d));
        bars.add(new MockBar(ZonedDateTime.of(2014, 6, 20, 0, 0, 0, 0, ZoneId.systemDefault()), 4d));
        bars.add(new MockBar(ZonedDateTime.of(2014, 6, 25, 0, 0, 0, 0, ZoneId.systemDefault()), 5d));
        bars.add(new MockBar(ZonedDateTime.of(2014, 6, 30, 0, 0, 0, 0, ZoneId.systemDefault()), 6d));

        defaultName = "Series Name";

        defaultSeries = new BaseTimeSeries.SeriesBuilder()
                .withNumTypeOf(TATestsUtils.CURENCT_NUM_FUNCTION)
                .withName(defaultName)
                .withBars(bars)
                .build();

        constrainedSeries = new BaseTimeSeries(defaultSeries,2, 4);
        emptySeries = new BaseTimeSeries();

        Strategy strategy = new BaseStrategy(new FixedRule(0, 2, 3, 6), new FixedRule(1, 4, 7, 8));
        strategy.setUnstablePeriod(2); // Strategy would need a real test class


    }

    @Test
    public void getEndGetBeginGetBarCountIsEmpty() {

        // Default series
        assertEquals(0, defaultSeries.getBeginIndex());
        assertEquals(bars.size() - 1, defaultSeries.getEndIndex());
        assertEquals(bars.size(), defaultSeries.getBarCount());
        assertFalse(defaultSeries.isEmpty());
        // Constrained series
        assertEquals(2, constrainedSeries.getBeginIndex());
        assertEquals(4, constrainedSeries.getEndIndex());
        assertEquals(3, constrainedSeries.getBarCount());
        assertFalse(constrainedSeries.isEmpty());
        // Empty series
        assertEquals(-1, emptySeries.getBeginIndex());
        assertEquals(-1, emptySeries.getEndIndex());
        assertEquals(0, emptySeries.getBarCount());
        assertTrue(emptySeries.isEmpty());
    }

    @Test
    public void getBarData() {
        // Default series
        assertEquals(bars, defaultSeries.getBarData());
        // Constrained series
        assertEquals(bars, constrainedSeries.getBarData());
        // Empty series
        assertEquals(0, emptySeries.getBarData().size());
    }

    @Test
    public void getSeriesPeriodDescription() {
        // Default series
        assertTrue(defaultSeries.getSeriesPeriodDescription().endsWith(bars.get(defaultSeries.getEndIndex()).getEndTime().format(DateTimeFormatter.ISO_DATE_TIME)));
        assertTrue(defaultSeries.getSeriesPeriodDescription().startsWith(bars.get(defaultSeries.getBeginIndex()).getEndTime().format(DateTimeFormatter.ISO_DATE_TIME)));
        // Constrained series
        assertTrue(constrainedSeries.getSeriesPeriodDescription().endsWith(bars.get(constrainedSeries.getEndIndex()).getEndTime().format(DateTimeFormatter.ISO_DATE_TIME)));
        assertTrue(constrainedSeries.getSeriesPeriodDescription().startsWith(bars.get(constrainedSeries.getBeginIndex()).getEndTime().format(DateTimeFormatter.ISO_DATE_TIME)));
        // Empty series
        assertEquals("", emptySeries.getSeriesPeriodDescription());
    }

    @Test
    public void getName() {
        assertEquals(defaultName, defaultSeries.getName());
        assertEquals(defaultName, constrainedSeries.getName());
    }

    @Test
    public void getBarWithRemovedIndexOnMovingSeriesShouldReturnFirstRemainingBar() {
        Bar bar = defaultSeries.getBar(4);
        defaultSeries.setMaximumBarCount(2);

        assertSame(bar, defaultSeries.getBar(0));
        assertSame(bar, defaultSeries.getBar(1));
        assertSame(bar, defaultSeries.getBar(2));
        assertSame(bar, defaultSeries.getBar(3));
        assertSame(bar, defaultSeries.getBar(4));
        assertNotSame(bar, defaultSeries.getBar(5));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getBarOnMovingAndEmptySeriesShouldThrowException() {
        defaultSeries.setMaximumBarCount(2);
        bars.clear(); // Should not be used like this
        defaultSeries.getBar(1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getBarWithNegativeIndexShouldThrowException() {
        defaultSeries.getBar(-1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getBarWithIndexGreaterThanBarCountShouldThrowException() {
        defaultSeries.getBar(10);
    }

    @Test
    public void getBarOnMovingSeries() {
        Bar bar = defaultSeries.getBar(4);
        defaultSeries.setMaximumBarCount(2);
        assertEquals(bar, defaultSeries.getBar(4));
    }

    @Test
    public void subSeriesCreation() {
        TimeSeries subSeries = defaultSeries.getSubSeries(2, 5);
        assertEquals(defaultSeries.getName(), subSeries.getName());
        assertEquals(0, subSeries.getBeginIndex());
        assertEquals(defaultSeries.getBeginIndex(), subSeries.getBeginIndex());
        assertEquals(2, subSeries.getEndIndex());
        assertNotEquals(defaultSeries.getEndIndex(), subSeries.getEndIndex());
        assertEquals(3, subSeries.getBarCount());

        subSeries = defaultSeries.getSubSeries(-1000,1000);
        assertEquals(0, subSeries.getBeginIndex());
        assertEquals(defaultSeries.getEndIndex(),subSeries.getEndIndex());
    }

    @Test(expected = IllegalArgumentException.class)
    public void SubseriesWithWrongArguments() {
        defaultSeries.getSubSeries(10, 9);
    }


    @Test(expected = IllegalStateException.class)
    public void maximumBarCountOnConstrainedSeriesShouldThrowException() {
        constrainedSeries.setMaximumBarCount(10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeMaximumBarCountShouldThrowException() {
        defaultSeries.setMaximumBarCount(-1);
    }

    @Test
    public void setMaximumBarCount() {
        // Before
        assertEquals(0, defaultSeries.getBeginIndex());
        assertEquals(bars.size() - 1, defaultSeries.getEndIndex());
        assertEquals(bars.size(), defaultSeries.getBarCount());

        defaultSeries.setMaximumBarCount(3);

        // After
        assertEquals(0, defaultSeries.getBeginIndex());
        assertEquals(5, defaultSeries.getEndIndex());
        assertEquals(3, defaultSeries.getBarCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void addNullBarshouldThrowException() {
        defaultSeries.addBar(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void addBarWithEndTimePriorToSeriesEndTimeShouldThrowException() {
        defaultSeries.addBar(new MockBar(ZonedDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault()), 99d));
    }

    @Test
    public void addBar() {
        defaultSeries = new BaseTimeSeries();
        Bar firstBar = new MockBar(ZonedDateTime.of(2014, 6, 13, 0, 0, 0, 0, ZoneId.systemDefault()), 1d);
        Bar secondBar = new MockBar(ZonedDateTime.of(2014, 6, 14, 0, 0, 0, 0, ZoneId.systemDefault()), 2d);

        assertEquals(0, defaultSeries.getBarCount());
        assertEquals(-1, defaultSeries.getBeginIndex());
        assertEquals(-1, defaultSeries.getEndIndex());

        defaultSeries.addBar(firstBar);
        assertEquals(1, defaultSeries.getBarCount());
        assertEquals(0, defaultSeries.getBeginIndex());
        assertEquals(0, defaultSeries.getEndIndex());

        defaultSeries.addBar(secondBar);
        assertEquals(2, defaultSeries.getBarCount());
        assertEquals(0, defaultSeries.getBeginIndex());
        assertEquals(1, defaultSeries.getEndIndex());
    }
}
