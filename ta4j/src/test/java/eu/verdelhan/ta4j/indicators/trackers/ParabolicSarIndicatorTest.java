/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2015 Marc de Verdelhan & respective authors (see AUTHORS)
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

//    private TimeSeries series;
//
//    @Before
//    public void setUp() {
//        List<Tick> ticks = new ArrayList<Tick>();
//        ticks.add(new MockTick(0, 9.41, 9.6, 9.4));
//        ticks.add(new MockTick(0, 9.85, 9.9, 9.5));
//        ticks.add(new MockTick(0, 9.33, 9.8, 9.3));
//        ticks.add(new MockTick(0, 9.06, 9.2, 9.0));
//        ticks.add(new MockTick(0, 9.40, 9.4, 9.2));
//        ticks.add(new MockTick(0, 9.22, 9.5, 9.1));
//        ticks.add(new MockTick(0, 9.31, 9.3, 8.9));
//        ticks.add(new MockTick(0, 9.76, 9.9, 9.6));
//        ticks.add(new MockTick(0, 10.00, 10.2, 9.8));
//        ticks.add(new MockTick(0, 9.59, 10.1, 9.6));
//        ticks.add(new MockTick(0, 10.40, 10.5, 9.5));
//        ticks.add(new MockTick(0, 11.23, 11.3, 10.8));
//        ticks.add(new MockTick(0, 11.44, 11.6, 11.3));
//        ticks.add(new MockTick(0, 11.44, 11.6, 11.2));
//        ticks.add(new MockTick(0, 11.78, 11.8, 11.3));
//        ticks.add(new MockTick(0, 11.88, 11.9, 11.7));
//        ticks.add(new MockTick(0, 11.67, 11.9, 11.6));
//        ticks.add(new MockTick(0, 11.33, 11.6, 11.3));
//        ticks.add(new MockTick(0, 11.05, 11.4, 11.1));
//        ticks.add(new MockTick(0, 11.09, 11.2, 10.9));
//        ticks.add(new MockTick(0, 11.35, 11.4, 11.1));
//        ticks.add(new MockTick(0, 11.27, 11.3, 11.1));
//        ticks.add(new MockTick(0, 11.00, 11.3, 11.0));
//        ticks.add(new MockTick(0, 10.76, 10.9, 10.8));
//        ticks.add(new MockTick(0, 10.54, 10.8, 10.5));
//        ticks.add(new MockTick(0, 10.68, 10.7, 10.6));
//        ticks.add(new MockTick(0, 10.09, 10.8, 10.1));
//        ticks.add(new MockTick(0, 9.89, 10.0, 9.8));
//        ticks.add(new MockTick(0, 10.04, 10.1, 9.8));
//        ticks.add(new MockTick(0, 9.63, 9.8, 9.5));
//        ticks.add(new MockTick(0, 9.66, 9.8, 9.6));
//        ticks.add(new MockTick(0, 9.36, 9.5, 9.3));
//        ticks.add(new MockTick(0, 9.37, 9.4, 9.1));
//        ticks.add(new MockTick(0, 9.10, 9.5, 9.1));
//        ticks.add(new MockTick(0, 9.43, 9.4, 9.0));
//        ticks.add(new MockTick(0, 9.52, 9.6, 9.3));
//        ticks.add(new MockTick(0, 9.81, 10.0, 9.8));
//        ticks.add(new MockTick(0, 9.91, 10.0, 9.9));
//        series = new MockTimeSeries(ticks);
//    }

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

//    @Test
//    public void fullTest() {
//
//        ParabolicSarIndicator sar = new ParabolicSarIndicator(series, 1);
//
//        assertDecimalEquals(sar.getValue(0), 9.410);
//        assertDecimalEquals(sar.getValue(1), 9.410);
//        assertDecimalEquals(sar.getValue(2), 9.350);
//        assertDecimalEquals(sar.getValue(3), 9.850);
//        assertDecimalEquals(sar.getValue(4), 9.816);
//        assertDecimalEquals(sar.getValue(5), 9.783);
//        assertDecimalEquals(sar.getValue(6), 9.752);
//        assertDecimalEquals(sar.getValue(7), 9.704);
//        assertDecimalEquals(sar.getValue(8), 8.950);
//        assertDecimalEquals(sar.getValue(9), 9.001);
//        assertDecimalEquals(sar.getValue(10), 9.050);
//        assertDecimalEquals(sar.getValue(11), 9.137);
//        assertDecimalEquals(sar.getValue(12), 9.306);
//        assertDecimalEquals(sar.getValue(13), 9.533);
//        assertDecimalEquals(sar.getValue(14), 9.736);
//        assertDecimalEquals(sar.getValue(15), 9.984);
//        assertDecimalEquals(sar.getValue(16), 10.252);
//        assertDecimalEquals(sar.getValue(17), 10.522);
//        assertDecimalEquals(sar.getValue(18), 10.749);
//        assertDecimalEquals(sar.getValue(19), 10.940);
//        assertDecimalEquals(sar.getValue(20), 11.940);
//        assertDecimalEquals(sar.getValue(21), 11.907);
//        assertDecimalEquals(sar.getValue(22), 11.875);
//        assertDecimalEquals(sar.getValue(23), 11.820);
//        assertDecimalEquals(sar.getValue(24), 11.734);
//        assertDecimalEquals(sar.getValue(25), 11.614);
//        assertDecimalEquals(sar.getValue(26), 11.506);
//        assertDecimalEquals(sar.getValue(27), 11.331);
//        assertDecimalEquals(sar.getValue(28), 11.112);
//        assertDecimalEquals(sar.getValue(29), 10.924);
//        assertDecimalEquals(sar.getValue(30), 10.693);
//        assertDecimalEquals(sar.getValue(31), 10.499);
//        assertDecimalEquals(sar.getValue(32), 10.289);
//        assertDecimalEquals(sar.getValue(33), 10.057);
//        assertDecimalEquals(sar.getValue(34), 9.858);
//        assertDecimalEquals(sar.getValue(35), 9.680);
//        assertDecimalEquals(sar.getValue(36), 9.650);
//        assertDecimalEquals(sar.getValue(37), 8.970);
//    }
}