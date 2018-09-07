/*******************************************************************************
 *   The MIT License (MIT)
 *
 *   Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2018 Ta4j Organization 
 *   & respective authors (see AUTHORS)
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy of
 *   this software and associated documentation files (the "Software"), to deal in
 *   the Software without restriction, including without limitation the rights to
 *   use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 *   the Software, and to permit persons to whom the Software is furnished to do so,
 *   subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in all
 *   copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 *   FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *   COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 *   IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *   CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *******************************************************************************/
package org.ta4j.core;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.MaxPriceIndicator;
import org.ta4j.core.indicators.helpers.MinPriceIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.num.PrecisionNum;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.trading.rules.FixedRule;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import static org.junit.Assert.*;


public class TimeSeriesTest extends AbstractIndicatorTest<TimeSeries,Num> {

    private TimeSeries defaultSeries;

    private TimeSeries subseries;

    private TimeSeries emptySeries;

    private List<Bar> bars;

    private String defaultName;

    public TimeSeriesTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        bars = new LinkedList<>();
        bars.add(new MockBar(ZonedDateTime.of(2014, 6, 13, 0, 0, 0, 0, ZoneId.systemDefault()), 1d,numFunction));
        bars.add(new MockBar(ZonedDateTime.of(2014, 6, 14, 0, 0, 0, 0, ZoneId.systemDefault()), 2d,numFunction));
        bars.add(new MockBar(ZonedDateTime.of(2014, 6, 15, 0, 0, 0, 0, ZoneId.systemDefault()), 3d,numFunction));
        bars.add(new MockBar(ZonedDateTime.of(2014, 6, 20, 0, 0, 0, 0, ZoneId.systemDefault()), 4d,numFunction));
        bars.add(new MockBar(ZonedDateTime.of(2014, 6, 25, 0, 0, 0, 0, ZoneId.systemDefault()), 5d,numFunction));
        bars.add(new MockBar(ZonedDateTime.of(2014, 6, 30, 0, 0, 0, 0, ZoneId.systemDefault()), 6d,numFunction)); 
        
        defaultName = "Series Name";

        defaultSeries = new BaseTimeSeries.SeriesBuilder()
                .withNumTypeOf(numFunction)
                .withName(defaultName)
                .withBars(bars)
                .build();
        
        subseries = defaultSeries.getSubSeries(2,5);
        emptySeries = new BaseTimeSeries.SeriesBuilder().withNumTypeOf(numFunction).build();

        Strategy strategy = new BaseStrategy(new FixedRule(0, 2, 3, 6), new FixedRule(1, 4, 7, 8));
        strategy.setUnstablePeriod(2); // Strategy would need a real test class


    }
    
    /**
     * Tests if the addBar(bar, boolean) function works correct.
     */
    @Test
    public void replaceBarTest() {
    	TimeSeries series = new BaseTimeSeries.SeriesBuilder().withNumTypeOf(numFunction).build();
    	series.addBar(new MockBar(ZonedDateTime.now(ZoneId.systemDefault()), 1d,numFunction), true);
    	assertEquals(series.getBarCount(),1);
    	TestUtils.assertNumEquals(series.getLastBar().getClosePrice(), series.numOf(1));
    	series.addBar(new MockBar(ZonedDateTime.now(ZoneId.systemDefault()).plusMinutes(1), 2d,numFunction), false);
    	series.addBar(new MockBar(ZonedDateTime.now(ZoneId.systemDefault()).plusMinutes(2), 3d,numFunction), false);
    	assertEquals(series.getBarCount(), 3);
    	TestUtils.assertNumEquals(series.getLastBar().getClosePrice(), series.numOf(3));
    	series.addBar(new MockBar(ZonedDateTime.now(ZoneId.systemDefault()).plusMinutes(3), 4d,numFunction), true);
    	series.addBar(new MockBar(ZonedDateTime.now(ZoneId.systemDefault()).plusMinutes(4), 5d,numFunction), true);
    	assertEquals(series.getBarCount(), 3);
    	TestUtils.assertNumEquals(series.getLastBar().getClosePrice(), series.numOf(5));
    }

    @Test
    public void getEndGetBeginGetBarCountIsEmpty() {

        // Default series
        assertEquals(0, defaultSeries.getBeginIndex());
        assertEquals(bars.size() - 1, defaultSeries.getEndIndex());
        assertEquals(bars.size(), defaultSeries.getBarCount());
        assertFalse(defaultSeries.isEmpty());
        // Constrained series
        assertEquals(0, subseries.getBeginIndex());
        assertEquals(2, subseries.getEndIndex());
        assertEquals(3, subseries.getBarCount());
        assertFalse(subseries.isEmpty());
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
        assertNotEquals(bars, subseries.getBarData());
        // Empty series
        assertEquals(0, emptySeries.getBarData().size());
    }

    @Test
    public void getSeriesPeriodDescription() {
        // Default series
        assertTrue(defaultSeries.getSeriesPeriodDescription().endsWith(bars.get(defaultSeries.getEndIndex()).getEndTime().format(DateTimeFormatter.ISO_DATE_TIME)));
        assertTrue(defaultSeries.getSeriesPeriodDescription().startsWith(bars.get(defaultSeries.getBeginIndex()).getEndTime().format(DateTimeFormatter.ISO_DATE_TIME)));
        // Constrained series
        assertTrue(subseries.getSeriesPeriodDescription().endsWith(bars.get(4).getEndTime().format(DateTimeFormatter.ISO_DATE_TIME)));
        assertTrue(subseries.getSeriesPeriodDescription().startsWith(bars.get(2).getEndTime().format(DateTimeFormatter.ISO_DATE_TIME)));
        // Empty series
        assertEquals("", emptySeries.getSeriesPeriodDescription());
    }

    @Test
    public void getName() {
        assertEquals(defaultName, defaultSeries.getName());
        assertEquals(defaultName, subseries.getName());
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

    public void maximumBarCountOnConstrainedSeriesShouldNotThrowException() {
        subseries.setMaximumBarCount(10);
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

    @Test(expected = NullPointerException.class)
    public void addNullBarshouldThrowException() {
        defaultSeries.addBar(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void addBarWithEndTimePriorToSeriesEndTimeShouldThrowException() {
        defaultSeries.addBar(new MockBar(ZonedDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault()), 99d,numFunction));
    }

    @Test
    public void addBar() {
        defaultSeries = new BaseTimeSeries.SeriesBuilder().withNumTypeOf(numFunction).build();
        Bar firstBar = new MockBar(ZonedDateTime.of(2014, 6, 13, 0, 0, 0, 0, ZoneId.systemDefault()), 1d,numFunction);
        Bar secondBar = new MockBar(ZonedDateTime.of(2014, 6, 14, 0, 0, 0, 0, ZoneId.systemDefault()), 2d,numFunction);

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

    @Test
    public void addPriceTest(){
        ClosePriceIndicator cp = new ClosePriceIndicator(defaultSeries);
        MaxPriceIndicator mxPrice = new MaxPriceIndicator(defaultSeries);
        MinPriceIndicator mnPrice = new MinPriceIndicator(defaultSeries);
        PreviousValueIndicator prevValue = new PreviousValueIndicator(cp, 1);

        Num adding1 = numOf(100);
        Num prevClose = defaultSeries.getBar(defaultSeries.getEndIndex()-1).getClosePrice();
        Num currentMin = mnPrice.getValue(defaultSeries.getEndIndex());
        Num currentClose = cp.getValue(defaultSeries.getEndIndex());

        TestUtils.assertNumEquals(currentClose, defaultSeries.getLastBar().getClosePrice());
        defaultSeries.addPrice(adding1);
        TestUtils.assertNumEquals(adding1, cp.getValue(defaultSeries.getEndIndex())); // adding1 is new close
        TestUtils.assertNumEquals(adding1, mxPrice.getValue(defaultSeries.getEndIndex())); // adding1 also new max
        TestUtils.assertNumEquals(currentMin, mnPrice.getValue(defaultSeries.getEndIndex())); // min stays same
        TestUtils.assertNumEquals(prevClose, prevValue.getValue(defaultSeries.getEndIndex())); // previous close stays same

        Num adding2 = numOf(0);
        defaultSeries.addPrice(adding2);
        TestUtils.assertNumEquals(adding2, cp.getValue(defaultSeries.getEndIndex())); // adding2 is new close
        TestUtils.assertNumEquals(adding1, mxPrice.getValue(defaultSeries.getEndIndex())); // max stays 100
        TestUtils.assertNumEquals(adding2, mnPrice.getValue(defaultSeries.getEndIndex())); // min is new adding2
        TestUtils.assertNumEquals(prevClose, prevValue.getValue(defaultSeries.getEndIndex())); // previous close stays same
    }
    
    /**
     * Tests if the {@link BaseTimeSeries#addTrade(Number, Number)} method works correct.
     */
    @Test
    public void addTradeTest() {
    	TimeSeries series = new BaseTimeSeries.SeriesBuilder().withNumTypeOf(numFunction).build();
    	series.addBar(new MockBar(ZonedDateTime.now(ZoneId.systemDefault()), 1d,numFunction));
    	series.addTrade(200, 11.5);
    	TestUtils.assertNumEquals(series.numOf(200),series.getLastBar().getVolume());
    	TestUtils.assertNumEquals(series.numOf(11.5),series.getLastBar().getClosePrice());
    	series.addTrade(BigDecimal.valueOf(200), BigDecimal.valueOf(100));
    	TestUtils.assertNumEquals(series.numOf(400),series.getLastBar().getVolume());
    	TestUtils.assertNumEquals(series.numOf(100),series.getLastBar().getClosePrice());
    }
    

    @Test(expected = IllegalArgumentException.class)
    public void wrongBarTypeDouble(){
        TimeSeries series = new BaseTimeSeries.SeriesBuilder().withNumTypeOf(DoubleNum.class).build();
        series.addBar(new BaseBar(ZonedDateTime.now(), 1, 1, 1, 1, 1, PrecisionNum::valueOf));
    }

    @Test(expected = IllegalArgumentException.class)
    public void wrongBarTypeBigDecimal(){
        TimeSeries series = new BaseTimeSeries.SeriesBuilder().withNumTypeOf(PrecisionNum::valueOf).build();
        series.addBar(new BaseBar(ZonedDateTime.now(),1,1,1,1,1, DoubleNum::valueOf));
    }
}
