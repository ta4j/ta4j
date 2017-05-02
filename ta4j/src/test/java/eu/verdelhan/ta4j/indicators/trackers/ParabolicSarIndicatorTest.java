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
package eu.verdelhan.ta4j.indicators.trackers;

import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;
import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class ParabolicSarIndicatorTest {

    @Test
    public void trendSwitchTest() {
        List<Tick> ticks = new ArrayList<Tick>();
        ticks.add(new MockTick(0, 10, 13, 8));
        ticks.add(new MockTick(0, 8, 11, 6));
        ticks.add(new MockTick(0, 6, 9, 4));
        ticks.add(new MockTick(0, 11, 15, 9));
        ticks.add(new MockTick(0, 13, 15, 9));
        ParabolicSarIndicator sar = new ParabolicSarIndicator(new MockTimeSeries(ticks), 1);

        assertDecimalEquals(sar.getValue(0), 10);
        assertDecimalEquals(sar.getValue(1), 8);
        assertDecimalEquals(sar.getValue(2), 11);
        assertDecimalEquals(sar.getValue(3), 4);
        assertDecimalEquals(sar.getValue(4), 4);
    }
    
    @Test
    public void trendSwitchTest2() {
        List<Tick> ticks = new ArrayList<Tick>();
        ticks.add(new MockTick(0, 10, 13, 11));
        ticks.add(new MockTick(0, 10, 15, 13));
        ticks.add(new MockTick(0, 12, 18, 11));
        ticks.add(new MockTick(0, 10, 15, 9));
        ticks.add(new MockTick(0, 9, 15, 9));
        
        ParabolicSarIndicator sar = new ParabolicSarIndicator(new MockTimeSeries(ticks), 1);

        assertDecimalEquals(sar.getValue(0), 10);
        assertDecimalEquals(sar.getValue(1), 10);
        assertDecimalEquals(sar.getValue(2), 0.04 * (18 - 10) + 10);
        assertDecimalEquals(sar.getValue(3), 18);
        assertDecimalEquals(sar.getValue(4), 18);
    }

    @Test
    public void upTrendTest() {
        List<Tick> ticks = new ArrayList<Tick>();
        ticks.add(new MockTick(0, 10, 13, 11));
        ticks.add(new MockTick(0, 17, 15, 11.38));
        ticks.add(new MockTick(0, 18, 16, 14));
        ticks.add(new MockTick(0, 19, 17, 12));
        ticks.add(new MockTick(0, 20, 18, 9));
        
        ParabolicSarIndicator sar = new ParabolicSarIndicator(new MockTimeSeries(ticks), 1);

        assertDecimalEquals(sar.getValue(0), 10);
        assertDecimalEquals(sar.getValue(1), 17);
        assertDecimalEquals(sar.getValue(2), 11.38);
        assertDecimalEquals(sar.getValue(3), 11.38);
        assertDecimalEquals(sar.getValue(4), 18);
    }
    
    @Test
    public void downTrendTest() {
        List<Tick> ticks = new ArrayList<Tick>();
        ticks.add(new MockTick(0, 20, 18, 9));
        ticks.add(new MockTick(0, 19, 17, 12));
        ticks.add(new MockTick(0, 18, 16, 14));
        ticks.add(new MockTick(0, 17, 15, 11.38));
        ticks.add(new MockTick(0, 10, 13, 11));
        ticks.add(new MockTick(0, 10, 30, 11));
        
        ParabolicSarIndicator sar = new ParabolicSarIndicator(new MockTimeSeries(ticks), 1);

        assertDecimalEquals(sar.getValue(0), 20);
        assertDecimalEquals(sar.getValue(1), 19);
        assertDecimalEquals(sar.getValue(2), 0.04 * (14 - 19) + 19);
        double value = 0.06 * (11.38 - 18.8) + 18.8;
        assertDecimalEquals(sar.getValue(3), value);
        assertDecimalEquals(sar.getValue(4), 0.08 * (11 - value) + value);
        assertDecimalEquals(sar.getValue(5), 11);
    }
}