/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan
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
package eu.verdelhan.ta4j.slicers;

import eu.verdelhan.ta4j.TimeSeriesSlicer;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.assertj.core.api.Assertions.*;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.junit.Before;
import org.junit.Test;

public class MemorizedSlicerTest {

    private MockTimeSeries series;

    private DateTime date;
    
    private TimeSeriesSlicer slicer; 

    @Before
    public void setUp() {
        this.date = new DateTime(0);
    }

    @Test
    public void applyForRegularSlicer() {
        series = new MockTimeSeries(date.withYear(2000), date.withYear(2001), date.withYear(2002), date
                .withYear(2003), date.withYear(2004));
        Period period = new Period().withYears(1);

        slicer = new MemorizedSlicer(series, period, 1);

        assertThat(slicer.getSlice(0).getBegin()).isEqualTo(0);
        assertThat(slicer.getSlice(1).getBegin()).isEqualTo(1);
        assertThat(slicer.getSlice(2).getBegin()).isEqualTo(2);
        assertThat(slicer.getSlice(3).getBegin()).isEqualTo(3);
        assertThat(slicer.getSlice(4).getBegin()).isEqualTo(4);
    }

    @Test(expected = IllegalArgumentException.class)
    public void periodsPerSliceGreaterThan1() {
        series = new MockTimeSeries(date.withYear(2000), date.withYear(2001), date.withYear(2002), date
                .withYear(2003), date.withYear(2004));
        slicer = new MemorizedSlicer(series, new Period().withYears(1), 0);
    }

    @Test
    public void startDateBeforeTimeSeriesDate() {
        series = new MockTimeSeries(date.withYear(2000), date.withYear(2001), date.withYear(2002), date
                .withYear(2003), date.withYear(2004));
        Period period = new Period().withYears(1);

        slicer = new MemorizedSlicer(series, period, date.withYear(1980), 1);

        assertThat(slicer.getSlice(0).getBegin()).isEqualTo(0);
        assertThat(slicer.getSlice(1).getBegin()).isEqualTo(1);
        assertThat(slicer.getSlice(2).getBegin()).isEqualTo(2);
        assertThat(slicer.getSlice(3).getBegin()).isEqualTo(3);
        assertThat(slicer.getSlice(4).getBegin()).isEqualTo(4);
    }

    @Test
    public void applyForMemorizedSlicer() {
        series = new MockTimeSeries(date.withYear(2000), date.withYear(2001), date.withYear(2002), date
                .withYear(2003), date.withYear(2004));
        Period period = new Period().withYears(1);

        slicer = new MemorizedSlicer(series, period, 3);

        assertThat(slicer.getSlice(0).getBegin()).isEqualTo(0);
        assertThat(slicer.getSlice(0).getEnd()).isEqualTo(0);

        assertThat(slicer.getSlice(1).getBegin()).isEqualTo(0);
        assertThat(slicer.getSlice(1).getEnd()).isEqualTo(1);

        assertThat(slicer.getSlice(2).getBegin()).isEqualTo(0);
        assertThat(slicer.getSlice(2).getEnd()).isEqualTo(2);

        assertThat(slicer.getSlice(3).getBegin()).isEqualTo(1);
        assertThat(slicer.getSlice(3).getEnd()).isEqualTo(3);

        assertThat(slicer.getSlice(4).getBegin()).isEqualTo(2);
        assertThat(slicer.getSlice(4).getEnd()).isEqualTo(4);
    }

    @Test
    public void applyForFullyMemorizedSlicer() {
        series = new MockTimeSeries(date.withYear(2000), date.withYear(2001), date.withYear(2002), date
                .withYear(2003), date.withYear(2004));
        Period period = new Period().withYears(1);

        slicer = new MemorizedSlicer(series, period, series.getSize());

        assertThat(slicer.getSlice(0).getBegin()).isEqualTo(0);
        assertThat(slicer.getSlice(0).getEnd()).isEqualTo(0);

        assertThat(slicer.getSlice(1).getBegin()).isEqualTo(0);
        assertThat(slicer.getSlice(1).getEnd()).isEqualTo(1);

        assertThat(slicer.getSlice(2).getBegin()).isEqualTo(0);
        assertThat(slicer.getSlice(2).getEnd()).isEqualTo(2);

        assertThat(slicer.getSlice(3).getBegin()).isEqualTo(0);
        assertThat(slicer.getSlice(3).getEnd()).isEqualTo(3);

        assertThat(slicer.getSlice(4).getBegin()).isEqualTo(0);
        assertThat(slicer.getSlice(4).getEnd()).isEqualTo(4);
    }

    @Test
    public void applyForSeries() {
        series = new MockTimeSeries(date.withYear(2000), date.withYear(2001), date.withYear(2002), date
                .withYear(2003), date.withYear(2004));
        Period period = new Period().withYears(1);

        TimeSeriesSlicer slicer = new MemorizedSlicer(series, period, 3);

        TimeSeriesSlicer newSlicer = slicer.applyForSeries(series);

        assertThat(newSlicer).isEqualTo(slicer);

        series = new MockTimeSeries(date.withYear(2000), date.withYear(2000), date.withYear(2000), date
                .withYear(2001), date.withYear(2001), date.withYear(2001), date.withYear(2002), date.withYear(2002),
                date.withYear(2002), date.withYear(2002), date.withYear(2003));

        newSlicer = slicer.applyForSeries(series);

        assertThat(newSlicer.getNumberOfSlices()).isEqualTo(4);

        assertThat(newSlicer.getSlice(0).getBegin()).isEqualTo(0);
        assertThat(newSlicer.getSlice(0).getEnd()).isEqualTo(2);

        assertThat(newSlicer.getSlice(1).getBegin()).isEqualTo(0);
        assertThat(newSlicer.getSlice(1).getEnd()).isEqualTo(5);

        assertThat(newSlicer.getSlice(2).getBegin()).isEqualTo(0);
        assertThat(newSlicer.getSlice(2).getEnd()).isEqualTo(9);

        assertThat(newSlicer.getSlice(3).getBegin()).isEqualTo(3);
        assertThat(newSlicer.getSlice(3).getEnd()).isEqualTo(10);
    }

    @Test
    public void splitByYearOneDatePerYear() {

        series = new MockTimeSeries(date.withYear(2000), date.withYear(2001), date.withYear(2002), date
                .withYear(2003), date.withYear(2004));
        Period period = new Period().withYears(1);

        TimeSeriesSlicer split = new MemorizedSlicer(series, period, 3);

        assertThat(split.getNumberOfSlices()).isEqualTo(5);

        assertThat(split.getSlice(0).getBegin()).isEqualTo(0);
        assertThat(split.getSlice(0).getEnd()).isEqualTo(0);

        assertThat(split.getSlice(1).getBegin()).isEqualTo(0);
        assertThat(split.getSlice(1).getEnd()).isEqualTo(1);

        assertThat(split.getSlice(2).getBegin()).isEqualTo(0);
        assertThat(split.getSlice(2).getEnd()).isEqualTo(2);

        assertThat(split.getSlice(3).getBegin()).isEqualTo(1);
        assertThat(split.getSlice(3).getEnd()).isEqualTo(3);

        assertThat(split.getSlice(4).getBegin()).isEqualTo(2);
        assertThat(split.getSlice(4).getEnd()).isEqualTo(4);
    }

    @Test
    public void splitByYearForcingJuly() {
        Period period = new Period().withYears(1);

        series = new MockTimeSeries(date.withDate(2000, 1, 1), date.withDate(2000, 2, 1), date.withDate(2000, 3, 1),
                date.withDate(2001, 1, 1), date.withDate(2001, 2, 1), date.withDate(2001, 12, 12), date.withDate(2002,
                        1, 1), date.withDate(2002, 2, 1), date.withDate(2002, 3, 1), date.withDate(2002, 5, 1), date
                        .withDate(2003, 3, 1));

        TimeSeriesSlicer split = new MemorizedSlicer(series, period, date.withYear(2000).withMonthOfYear(7), 2);

        assertThat(split.getNumberOfSlices()).isEqualTo(3);

        assertThat(split.getSlice(0).getBegin()).isEqualTo(3);
        assertThat(split.getSlice(0).getEnd()).isEqualTo(4);

        assertThat(split.getSlice(1).getBegin()).isEqualTo(3);
        assertThat(split.getSlice(1).getEnd()).isEqualTo(9);

        assertThat(split.getSlice(2).getBegin()).isEqualTo(5);
        assertThat(split.getSlice(2).getEnd()).isEqualTo(10);
    }

    @Test
    public void splitByYearWithHolesBetweenSlices() {

        series = new MockTimeSeries(date.withYear(2000), date.withYear(2000), date.withYear(2000), date
                .withYear(2001), date.withYear(2001), date.withYear(2001), date.withYear(2002), date.withYear(2002),
                date.withYear(2002), date.withYear(2002), date.withYear(2005), date.withYear(2005));

        Period period = new Period().withYears(1);
        TimeSeriesSlicer split = new MemorizedSlicer(series, period, 3);

        assertThat(split.getNumberOfSlices()).isEqualTo(4);

        assertThat(split.getSlice(0).getBegin()).isEqualTo(0);
        assertThat(split.getSlice(0).getEnd()).isEqualTo(2);

        assertThat(split.getSlice(1).getBegin()).isEqualTo(0);
        assertThat(split.getSlice(1).getEnd()).isEqualTo(5);

        assertThat(split.getSlice(2).getBegin()).isEqualTo(0);
        assertThat(split.getSlice(2).getEnd()).isEqualTo(9);

        assertThat(split.getSlice(3).getBegin()).isEqualTo(3);
        assertThat(split.getSlice(3).getEnd()).isEqualTo(11);

    }

    @Test
    public void splitByYearBeginningInJuly() {
        Period period = new Period().withYears(1);

        series = new MockTimeSeries(date.withDate(2000, 7, 1), date.withDate(2000, 8, 1), date.withDate(2000, 9, 15),
                date.withDate(2001, 1, 1), date.withDate(2001, 1, 3), date.withDate(2001, 12, 31), date.withDate(2002,
                        1, 1), date.withDate(2002, 1, 2), date.withDate(2002, 1, 3), date.withDate(2002, 5, 5), date
                        .withDate(2003, 3, 3));

        TimeSeriesSlicer split = new MemorizedSlicer(series, period, 2);

        assertThat(split.getNumberOfSlices()).isEqualTo(3);

        assertThat(split.getSlice(0).getBegin()).isEqualTo(0);
        assertThat(split.getSlice(0).getEnd()).isEqualTo(4);

        assertThat(split.getSlice(1).getBegin()).isEqualTo(0);
        assertThat(split.getSlice(1).getEnd()).isEqualTo(9);

        assertThat(split.getSlice(2).getBegin()).isEqualTo(5);
        assertThat(split.getSlice(2).getEnd()).isEqualTo(10);
    }

    @Test
    public void splitByYearBeginingInJulyOverridingPeriodBeginTo1of1of2000() {
        Period period = new Period().withYears(1);

        series = new MockTimeSeries(date.withDate(2000, 1, 1), date.withDate(2000, 8, 1), date.withDate(2000, 9, 15),
                date.withDate(2001, 1, 1), date.withDate(2001, 1, 3), date.withDate(2001, 12, 31), date.withDate(2002,
                        1, 1), date.withDate(2002, 1, 2), date.withDate(2002, 1, 3), date.withDate(2002, 5, 5), date
                        .withDate(2003, 3, 3));
        TimeSeriesSlicer split = new MemorizedSlicer(series, period, date.withDate(2000, 1, 1), 3);

        assertThat(split.getNumberOfSlices()).isEqualTo(4);

        assertThat(split.getSlice(0).getBegin()).isEqualTo(0);
        assertThat(split.getSlice(0).getEnd()).isEqualTo(2);

        assertThat(split.getSlice(1).getBegin()).isEqualTo(0);
        assertThat(split.getSlice(1).getEnd()).isEqualTo(5);

        assertThat(split.getSlice(2).getBegin()).isEqualTo(0);
        assertThat(split.getSlice(2).getEnd()).isEqualTo(9);

        assertThat(split.getSlice(3).getBegin()).isEqualTo(3);
        assertThat(split.getSlice(3).getEnd()).isEqualTo(10);
    }

    @Test
    public void splitByHour() {
        Period period = new Period().withHours(1);

        DateTime openTime = new DateTime(0).withTime(10, 0, 0, 0);

        series = new MockTimeSeries(openTime, openTime.plusMinutes(1), openTime.plusMinutes(2), openTime
                .plusMinutes(10), openTime.plusMinutes(15), openTime.plusMinutes(25), openTime.plusHours(1), openTime
                .plusHours(2), openTime.plusHours(7), openTime.plusHours(10).plusMinutes(5), openTime.plusHours(10)
                .plusMinutes(10), openTime.plusHours(10).plusMinutes(20), openTime.plusHours(10).plusMinutes(30));

        TimeSeriesSlicer split = new MemorizedSlicer(series, period, 3);

        assertThat(split.getNumberOfSlices()).isEqualTo(5);

        assertThat(split.getSlice(0).getBegin()).isEqualTo(0);
        assertThat(split.getSlice(0).getEnd()).isEqualTo(5);

        assertThat(split.getSlice(1).getBegin()).isEqualTo(0);
        assertThat(split.getSlice(1).getEnd()).isEqualTo(6);

        assertThat(split.getSlice(2).getBegin()).isEqualTo(0);
        assertThat(split.getSlice(2).getEnd()).isEqualTo(7);

        assertThat(split.getSlice(3).getBegin()).isEqualTo(6);
        assertThat(split.getSlice(3).getEnd()).isEqualTo(8);

        assertThat(split.getSlice(4).getBegin()).isEqualTo(7);
        assertThat(split.getSlice(4).getEnd()).isEqualTo(12);

    }
    
    @Test
    public void averageTicksPerSlice()
    {
        Period period = new Period().withYears(1);
        series = new MockTimeSeries(date.withDate(2000, 1, 1), date.withDate(2000, 8, 1), date.withDate(2000, 9, 15),
                date.withDate(2001, 1, 1), date.withDate(2001, 1, 3), date.withDate(2001, 12, 31), date.withDate(2002,
                        1, 1), date.withDate(2002, 1, 2), date.withDate(2002, 1, 3), date.withDate(2002, 5, 5), date
                        .withDate(2003, 3, 3));
        MemorizedSlicer slicer = new MemorizedSlicer(series, period, 3);
        assertThat(slicer.getAverageTicksPerSlice()).isEqualTo(27d/4);
    }

}
