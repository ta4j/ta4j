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
package eu.verdelhan.ta4j.indicators.trackers;

import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
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
        
        assertThat(sar.getValue(0)).isEqualTo(10d);
        assertThat(sar.getValue(1)).isEqualTo(8d);
        assertThat(sar.getValue(2)).isEqualTo(11d);
        assertThat(sar.getValue(3)).isEqualTo(4d);
        assertThat(sar.getValue(4)).isEqualTo(4d);
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
        
        assertThat(sar.getValue(0)).isEqualTo(10d);
        assertThat(sar.getValue(1)).isEqualTo(10d);
        assertThat(sar.getValue(2)).isEqualTo(0.04 * (18d - 10) + 10d);
        assertThat(sar.getValue(3)).isEqualTo(18d);
        assertThat(sar.getValue(3)).isEqualTo(18d);
        assertThat(sar.getValue(4)).isEqualTo(18d);
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
        
        assertThat(sar.getValue(0)).isEqualTo(10d);
        assertThat(sar.getValue(1)).isEqualTo(17d);
        assertThat(sar.getValue(2)).isEqualTo(11.38d);
        assertThat(sar.getValue(3)).isEqualTo(11.38d);
        assertThat(sar.getValue(4)).isEqualTo(18d);
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
        
        assertThat(sar.getValue(0)).isEqualTo(20d);
        assertThat(sar.getValue(1)).isEqualTo(19d);
        assertThat(sar.getValue(2)).isEqualTo(0.04d * (14d - 19d) + 19d);
        double value = 0.06d * (11.38d - 18.8d) + 18.8d;
        assertThat(sar.getValue(3)).isEqualTo(value);
        assertThat(sar.getValue(4)).isEqualTo(0.08d * (11d - value) + value);
        assertThat(sar.getValue(5)).isEqualTo(11d);
    }

//    @Test
//    public void fullTest() {
//
//        ParabolicSarIndicator sar = new ParabolicSarIndicator(series, 1);
//
//        assertThat(sar.getValue(0)).isEqualTo(9.410);
//        assertThat(sar.getValue(1)).isEqualTo(9.410);
//        assertThat(sar.getValue(2)).isEqualTo(9.350);
//        assertThat(sar.getValue(3)).isEqualTo(9.850);
//        assertThat(sar.getValue(4)).isEqualTo(9.816);
//        assertThat(sar.getValue(5)).isEqualTo(9.783);
//        assertThat(sar.getValue(6)).isEqualTo(9.752);
//        assertThat(sar.getValue(7)).isEqualTo(9.704);
//        assertThat(sar.getValue(8)).isEqualTo(8.950);
//        assertThat(sar.getValue(9)).isEqualTo(9.001);
//        assertThat(sar.getValue(10)).isEqualTo(9.050);
//        assertThat(sar.getValue(11)).isEqualTo(9.137);
//        assertThat(sar.getValue(12)).isEqualTo(9.306);
//        assertThat(sar.getValue(13)).isEqualTo(9.533);
//        assertThat(sar.getValue(14)).isEqualTo(9.736);
//        assertThat(sar.getValue(15)).isEqualTo(9.984);
//        assertThat(sar.getValue(16)).isEqualTo(10.252);
//        assertThat(sar.getValue(17)).isEqualTo(10.522);
//        assertThat(sar.getValue(18)).isEqualTo(10.749);
//        assertThat(sar.getValue(19)).isEqualTo(10.940);
//        assertThat(sar.getValue(20)).isEqualTo(11.940);
//        assertThat(sar.getValue(21)).isEqualTo(11.907);
//        assertThat(sar.getValue(22)).isEqualTo(11.875);
//        assertThat(sar.getValue(23)).isEqualTo(11.820);
//        assertThat(sar.getValue(24)).isEqualTo(11.734);
//        assertThat(sar.getValue(25)).isEqualTo(11.614);
//        assertThat(sar.getValue(26)).isEqualTo(11.506);
//        assertThat(sar.getValue(27)).isEqualTo(11.331);
//        assertThat(sar.getValue(28)).isEqualTo(11.112);
//        assertThat(sar.getValue(29)).isEqualTo(10.924);
//        assertThat(sar.getValue(30)).isEqualTo(10.693);
//        assertThat(sar.getValue(31)).isEqualTo(10.499);
//        assertThat(sar.getValue(32)).isEqualTo(10.289);
//        assertThat(sar.getValue(33)).isEqualTo(10.057);
//        assertThat(sar.getValue(34)).isEqualTo(9.858);
//        assertThat(sar.getValue(35)).isEqualTo(9.680);
//        assertThat(sar.getValue(36)).isEqualTo(9.650);
//        assertThat(sar.getValue(37)).isEqualTo(8.970);
//    }
}