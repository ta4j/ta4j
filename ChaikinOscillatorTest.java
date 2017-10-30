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
package org.ta4j.core.indicators.volume;

import static org.ta4j.core.TATestsUtils.assertDecimalEquals;
import org.ta4j.core.Tick;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.ChaikinOscillatorIndicator;
import org.ta4j.core.mocks.MockTick;
import org.ta4j.core.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class ChaikinOscillatorTest {

    @Test
    public void getValue() {

        List<Tick> ticks = new ArrayList<Tick>();
        
        ticks.add(new MockTick(12.915, 13.600, 12.890, 13.550, 264266));
        ticks.add(new MockTick(13.550, 13.770, 13.310, 13.505, 305427));
        ticks.add(new MockTick(13.510, 13.590, 13.425, 13.490, 104077));
        ticks.add(new MockTick(13.515, 13.545, 13.400, 13.480, 136135));
        ticks.add(new MockTick(13.490, 13.495, 13.310, 13.345, 92090));
        ticks.add(new MockTick(13.350, 13.490, 13.325, 13.420, 80948));
        ticks.add(new MockTick(13.415, 13.460, 13.290, 13.300, 82983));
        ticks.add(new MockTick(13.320, 13.320, 13.090, 13.130, 126918));
        ticks.add(new MockTick(13.145, 13.225, 13.090, 13.150, 68560));
        ticks.add(new MockTick(13.150, 13.250, 13.110, 13.245, 41178));
        ticks.add(new MockTick(13.245, 13.250, 13.120, 13.210, 63606));
        ticks.add(new MockTick(13.210, 13.275, 13.185, 13.275, 34402));
        TimeSeries series = new MockTimeSeries(ticks);

        ChaikinOscillatorIndicator co = new ChaikinOscillatorIndicator(series);
        assertDecimalEquals(co.getValue(0), 0.0);
        assertDecimalEquals(co.getValue(1), 0.0);
        assertDecimalEquals(co.getValue(2), -165349.14743589723);
        assertDecimalEquals(co.getValue(3), -337362.31490384537);
        assertDecimalEquals(co.getValue(4), -662329.9816620838);
        assertDecimalEquals(co.getValue(5), -836710.5421757463);
        assertDecimalEquals(co.getValue(6), -1847749.1562169262);
        assertDecimalEquals(co.getValue(7), -2710068.6997245993);
        assertDecimalEquals(co.getValue(8), -3069157.9046621257);
        assertDecimalEquals(co.getValue(9), -2795286.881074371);
        assertDecimalEquals(co.getValue(9), -2795286.881074371);
        assertDecimalEquals(co.getValue(9), -2795286.881074371);
    }
}
