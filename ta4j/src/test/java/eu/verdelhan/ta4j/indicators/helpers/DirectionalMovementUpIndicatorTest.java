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
package eu.verdelhan.ta4j.indicators.helpers;

import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;
import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class DirectionalMovementUpIndicatorTest {

    @Test
    public void zeroDirectionalMovement()
    {
        MockTick yesterdayTick = new MockTick(0, 0, 10, 2);
        MockTick todayTick = new MockTick(0, 0, 6, 6);
        List<Tick> ticks = new ArrayList<Tick>();
        ticks.add(yesterdayTick);
        ticks.add(todayTick);
        MockTimeSeries series = new MockTimeSeries(ticks);
        DirectionalMovementUpIndicator dup = new DirectionalMovementUpIndicator(series);
        assertDecimalEquals(dup.getValue(1), 0);
    }
    
    @Test
    public void zeroDirectionalMovement2() {
        MockTick yesterdayTick = new MockTick(0, 0, 6, 12);
        MockTick todayTick = new MockTick(0, 0, 12, 6);
        List<Tick> ticks = new ArrayList<Tick>();
        ticks.add(yesterdayTick);
        ticks.add(todayTick);
        MockTimeSeries series = new MockTimeSeries(ticks);
        DirectionalMovementUpIndicator dup = new DirectionalMovementUpIndicator(series);
        assertDecimalEquals(dup.getValue(1), 0);
    }

    @Test
    public void zeroDirectionalMovement3() {
        MockTick yesterdayTick = new MockTick(0, 0, 6, 20);
        MockTick todayTick = new MockTick(0, 0, 12, 4);
        List<Tick> ticks = new ArrayList<Tick>();
        ticks.add(yesterdayTick);
        ticks.add(todayTick);
        MockTimeSeries series = new MockTimeSeries(ticks);
        DirectionalMovementUpIndicator dup = new DirectionalMovementUpIndicator(series);
        assertDecimalEquals(dup.getValue(1), 0);
    }

    @Test
    public void positiveDirectionalMovement() {
        MockTick yesterdayTick = new MockTick(0, 0, 6, 6);
        MockTick todayTick = new MockTick(0, 0, 12, 4);
        List<Tick> ticks = new ArrayList<Tick>();
        ticks.add(yesterdayTick);
        ticks.add(todayTick);
        MockTimeSeries series = new MockTimeSeries(ticks);
        DirectionalMovementUpIndicator dup = new DirectionalMovementUpIndicator(series);
        assertDecimalEquals(dup.getValue(1), 6);
    }
}
