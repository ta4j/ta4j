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
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.num.Num;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import static org.junit.Assert.*;


public class TimeSeriesAggregationTest extends AbstractIndicatorTest<TimeSeries,Num> {

    private TimeSeries series;

    private TimeSeries upscaledSeries;
    
    private List<Bar> bars;
    
    private List<Bar> upscaledBars;

    private String defaultName;

    public TimeSeriesAggregationTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }
    
    public List<Bar> getOneDayBar(){
    	List<Bar> bars = new LinkedList<>();  
        bars.add(new MockBar(ZonedDateTime.of(2019, 6, 12, 4, 1, 0, 0, ZoneId.systemDefault()), 1d, 2d, 3d, 4d, 5d, 6d, 7, numFunction));
        bars.add(new MockBar(ZonedDateTime.of(2019, 6, 12, 4, 2, 0, 0, ZoneId.systemDefault()), 2d, 3d, 3d, 4d, 5d, 6d, 7, numFunction));
        bars.add(new MockBar(ZonedDateTime.of(2019, 6, 12, 4, 3, 0, 0, ZoneId.systemDefault()), 3d, 4d, 4d, 5d, 6d, 7d, 7, numFunction));
        bars.add(new MockBar(ZonedDateTime.of(2019, 6, 12, 4, 4, 0, 0, ZoneId.systemDefault()), 4d, 5d, 6d, 5d, 7d, 8d, 7, numFunction));
        bars.add(new MockBar(ZonedDateTime.of(2019, 6, 12, 4, 5, 0, 0, ZoneId.systemDefault()), 5d, 9d, 3d, 11d, 2d, 6d, 7, numFunction));
        
        bars.add(new MockBar(ZonedDateTime.of(2019, 6, 12, 4, 6, 0, 0, ZoneId.systemDefault()), 6d, 10d, 9d, 4d, 8d, 3d, 7, numFunction));
        bars.add(new MockBar(ZonedDateTime.of(2019, 6, 12, 4, 7, 0, 0, ZoneId.systemDefault()), 3d, 3d, 4d, 95d, 21d, 74d, 7, numFunction));
        bars.add(new MockBar(ZonedDateTime.of(2019, 6, 12, 4, 8, 0, 0, ZoneId.systemDefault()), 4d, 7d, 63d, 59d, 56d, 89d, 7, numFunction));
        bars.add(new MockBar(ZonedDateTime.of(2019, 6, 12, 4, 9, 0, 0, ZoneId.systemDefault()), 5d, 93d, 3d, 21d, 29d, 62d, 7, numFunction));
        bars.add(new MockBar(ZonedDateTime.of(2019, 6, 12, 4, 10, 0, 0, ZoneId.systemDefault()), 6d, 10d, 91d, 43d, 84d, 32d, 7, numFunction));
        
        bars.add(new MockBar(ZonedDateTime.of(2019, 6, 12, 4, 11, 0, 0, ZoneId.systemDefault()), 4d, 10d, 943d, 49d, 8d, 43d, 7, numFunction));
        bars.add(new MockBar(ZonedDateTime.of(2019, 6, 12, 4, 12, 0, 0, ZoneId.systemDefault()), 3d, 3d, 43d, 92d, 21d, 784d, 7, numFunction));
        bars.add(new MockBar(ZonedDateTime.of(2019, 6, 12, 4, 13, 0, 0, ZoneId.systemDefault()), 4d, 74d, 53d, 52d, 56d, 89d, 7, numFunction));
        bars.add(new MockBar(ZonedDateTime.of(2019, 6, 12, 4, 14, 0, 0, ZoneId.systemDefault()), 5d, 93d, 31d, 221d, 29d, 62d, 7, numFunction));
        bars.add(new MockBar(ZonedDateTime.of(2019, 6, 12, 4, 15, 0, 0, ZoneId.systemDefault()), 6d, 10d, 991d, 43d, 84d, 32d, 7, numFunction));
        
        bars.add(new MockBar(ZonedDateTime.of(2019, 6, 12, 4, 16, 0, 0, ZoneId.systemDefault()), 6d, 108d, 1991d, 433d, 847d, 322d, 7, numFunction));
        return bars;
    }


    @Before
    public void setUp() {
        bars = getOneDayBar();
        
        defaultName = "Series Name";

        series = new BaseTimeSeries.SeriesBuilder()
                .withNumTypeOf(numFunction)
                .withName(defaultName)
                .withBars(bars)
                .build();
        
        upscaledSeries = BaseTimeSeries.aggregateTimeSeries(defaultName, series, Duration.ofDays(5));
        upscaledBars = BaseTimeSeries.aggregateBars(bars, Duration.ofDays(10));
    }
    
    /**
     * Tests if the bars are upscaled correctly from 1day to 5day
     */
    @Test
    public void upscaledTo5DayBars() {
    	
    	List<Bar> bars = upscaledSeries.getBarData();
    	
    	// must be 3 bars
    	assertEquals(3, bars.size());
    	
    	// first bar must have ohlcv (1, 6, 4, 9, 25)
    	Bar bar1 = bars.get(0);
    	TestUtils.assertNumEquals(bar1.getOpenPrice().numOf(1),bar1.getOpenPrice());
    	TestUtils.assertNumEquals(bar1.getOpenPrice().numOf(6),bar1.getMaxPrice());
    	TestUtils.assertNumEquals(bar1.getOpenPrice().numOf(4), bar1.getMinPrice());
    	TestUtils.assertNumEquals(bar1.getOpenPrice().numOf(9), bar1.getClosePrice());
    	TestUtils.assertNumEquals(bar1.getOpenPrice().numOf(33), bar1.getVolume());
    	
    	// second bar must have ohlcv (6, 91, 4, 10, 260)
    	Bar bar2 = bars.get(1);
    	TestUtils.assertNumEquals(bar2.getOpenPrice().numOf(6), bar2.getOpenPrice());
    	TestUtils.assertNumEquals(bar2.getOpenPrice().numOf(91), bar2.getMaxPrice());
    	TestUtils.assertNumEquals(bar2.getOpenPrice().numOf(4), bar2.getMinPrice());
    	TestUtils.assertNumEquals(bar2.getOpenPrice().numOf(10), bar2.getClosePrice());
    	TestUtils.assertNumEquals(bar2.getOpenPrice().numOf(260), bar2.getVolume());
    	
    	// second bar must have ohlcv (1d, 6d, 4d, 9d, 25)
    	Bar bar3 = bars.get(2);
    	TestUtils.assertNumEquals(bar3.getOpenPrice().numOf(4), bar3.getOpenPrice());
    	TestUtils.assertNumEquals(bar3.getOpenPrice().numOf(991), bar3.getMaxPrice());
    	TestUtils.assertNumEquals(bar3.getOpenPrice().numOf(43), bar3.getMinPrice());
    	TestUtils.assertNumEquals(bar3.getOpenPrice().numOf(10), bar3.getClosePrice());
    	TestUtils.assertNumEquals(bar3.getOpenPrice().numOf(1010), bar3.getVolume());
    }
    
    /**
     * Tests if the bars are upscaled correctly from 1day to 10day
     */
    @Test
    public void upscaledTo10DayBars() {
    	
    	List<Bar> bars = upscaledBars;
    	
    	// must be 1 bars
    	assertEquals(1, bars.size());
    	
    	// first bar must have ohlcv (1, 91, 4, 10, 293)
    	Bar bar1 = bars.get(0);
    	TestUtils.assertNumEquals(bar1.getOpenPrice().numOf(1), bar1.getOpenPrice());
    	TestUtils.assertNumEquals(bar1.getOpenPrice().numOf(91), bar1.getMaxPrice());
    	TestUtils.assertNumEquals(bar1.getOpenPrice().numOf(4), bar1.getMinPrice());
    	TestUtils.assertNumEquals(bar1.getOpenPrice().numOf(10), bar1.getClosePrice());
    	TestUtils.assertNumEquals(bar1.getOpenPrice().numOf(293), bar1.getVolume());
    }

}
