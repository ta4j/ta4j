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
package eu.verdelhan.ta4j.series;

import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.LinkedList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.junit.Before;
import org.junit.Test;

public class ConstrainedTimeSeriesTest {

    private static TimeSeries series;

    private ConstrainedTimeSeries constrained;

    private List<Tick> ticks;

    @Before
    public void setUp() {
        ticks = new LinkedList<Tick>();
        ticks.add(new MockTick(new DateTime().withDate(2007, 6, 6), 1d));
        ticks.add(new MockTick(new DateTime().withDate(2007, 6, 7), 2d));
        ticks.add(new MockTick(new DateTime().withDate(2007, 6, 9), 3d));
        ticks.add(new MockTick(new DateTime().withDate(2007, 6, 10), 4d));
        
        series = new MockTimeSeries(ticks);
        constrained = new ConstrainedTimeSeries(series, series.getBegin(), series.getEnd());
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructurTesteShouldThrowsException() {
        ConstrainedTimeSeries constrainedException = new ConstrainedTimeSeries(series, series.getEnd(), series.getBegin());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getTickTestWrongIndex() {
        constrained.getTick(series.getEnd() + 1);
        constrained.getTick(series.getBegin() - 1);
    }

    @Test
    public void getEndSizeBegin() {
        assertThat(constrained.getBegin()).isEqualTo(series.getBegin());
        assertThat(constrained.getEnd()).isEqualTo(series.getEnd());
        assertThat(constrained.getSize()).isEqualTo(series.getSize());
    }

    @Test
    public void getPeriodName() {
        assertThat(constrained.getPeriodName().endsWith(series.getTick(series.getEnd()).getEndTime().toString("hh:mm dd/MM/yyyy"))).isTrue();
        assertThat(constrained.getPeriodName().startsWith(series.getTick(series.getBegin()).getEndTime().toString("hh:mm dd/MM/yyyy"))).isTrue();
    }
    
    @Test
    public void getName() {
        assertThat(constrained.getName().endsWith(series.getName())).isTrue();
    }
    
    @Test
    public void getPeriodTest() {
        assertThat(constrained.getPeriod()).isEqualTo(new Period(series.getTick(series.getBegin() + 1).getEndTime().getMillis() - series.getTick(series.getBegin()).getEndTime().getMillis()));
    }
}
