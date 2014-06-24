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
package eu.verdelhan.ta4j;

import eu.verdelhan.ta4j.mocks.MockTick;
import java.util.LinkedList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.junit.Before;
import org.junit.Test;

public class TimeSeriesTest {

    private TimeSeries defaultSeries;

    private TimeSeries subSeries;

    private List<Tick> ticks;

    private String defaultName;

    @Before
    public void setUp() {
        DateTime date = new DateTime();

        ticks = new LinkedList<Tick>();
        ticks.add(new MockTick(date.withDate(2014, 6, 13), 1d));
        ticks.add(new MockTick(date.withDate(2014, 6, 14), 2d));
        ticks.add(new MockTick(date.withDate(2014, 6, 15), 3d));
        ticks.add(new MockTick(date.withDate(2014, 6, 20), 4d));
        ticks.add(new MockTick(date.withDate(2014, 6, 25), 5d));
        ticks.add(new MockTick(date.withDate(2014, 6, 30), 6d));

        defaultName = "Series Name";

        defaultSeries = new TimeSeries(defaultName, ticks);
        subSeries = defaultSeries.subseries(2, 4);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorWithInvalidIndexesShouldThrowException() {
        TimeSeries s = new TimeSeries(null, null, 4, 2);
    }

    @Test
    public void getEndSizeBegin() {
        // Original series
        assertThat(defaultSeries.getBegin()).isEqualTo(0);
        assertThat(defaultSeries.getEnd()).isEqualTo(ticks.size() - 1);
        assertThat(defaultSeries.getSize()).isEqualTo(ticks.size());
        // Sub-series
        assertThat(subSeries.getBegin()).isEqualTo(2);
        assertThat(subSeries.getEnd()).isEqualTo(4);
        assertThat(subSeries.getSize()).isEqualTo(3);
    }

    @Test
    public void getPeriodName() {
        // Original series
        assertThat(defaultSeries.getPeriodName()).endsWith(ticks.get(defaultSeries.getEnd()).getEndTime().toString("hh:mm dd/MM/yyyy"));
        assertThat(defaultSeries.getPeriodName()).startsWith(ticks.get(defaultSeries.getBegin()).getEndTime().toString("hh:mm dd/MM/yyyy"));
        // Sub-series
        assertThat(subSeries.getPeriodName()).endsWith(ticks.get(subSeries.getEnd()).getEndTime().toString("hh:mm dd/MM/yyyy"));
        assertThat(subSeries.getPeriodName()).startsWith(ticks.get(subSeries.getBegin()).getEndTime().toString("hh:mm dd/MM/yyyy"));

    }

    @Test
    public void getName() {
        assertThat(defaultSeries.getName()).isEqualTo(defaultName);
        assertThat(subSeries.getName()).isEqualTo(defaultName);
    }

    @Test
    public void getPeriod() {
        // Original series
        Period origSeriesPeriod = new Period(ticks.get(1).getEndTime().getMillis() - ticks.get(0).getEndTime().getMillis());
        assertThat(defaultSeries.getPeriod()).isEqualTo(origSeriesPeriod);
        // Sub-series
        Period subSeriesPeriod = new Period(ticks.get(3).getEndTime().getMillis() - ticks.get(2).getEndTime().getMillis());
        assertThat(subSeries.getPeriod()).isEqualTo(subSeriesPeriod);
    }

    @Test
    public void subseries() {
        TimeSeries subSeries2 = defaultSeries.subseries(2, 5);
        assertThat(subSeries2.getName()).isEqualTo(defaultSeries.getName());
        assertThat(subSeries2.getBegin()).isEqualTo(2);
        assertThat(subSeries2.getBegin()).isNotEqualTo(defaultSeries.getBegin());
        assertThat(subSeries2.getEnd()).isEqualTo(5);
        assertThat(subSeries2.getEnd()).isEqualTo(defaultSeries.getEnd());
        assertThat(subSeries2.getSize()).isEqualTo(4);
        assertThat(subSeries2.getPeriod()).isNotEqualTo(defaultSeries.getPeriod());
    }
}
