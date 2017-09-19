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
package org.ta4j.core.indicators;

import static org.ta4j.core.TATestsUtils.assertDecimalEquals;
import static org.junit.Assert.assertEquals;

import org.ta4j.core.Tick;
import org.ta4j.core.mocks.MockTick;
import org.ta4j.core.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class ParabolicSarIndicatorTest {

    @Test
    public void startUpAndDownTrendTest() {
        List<Tick> ticks = new ArrayList<Tick>();
        ticks.add(new MockTick(0, 75.1, 74.06, 75.11));
        ticks.add(new MockTick(0, 75.9, 76.030000, 74.640000));
        ticks.add(new MockTick(0, 75.24, 76.269900, 75.060000));
        ticks.add(new MockTick(0, 75.17, 75.280000, 74.500000));
        ticks.add(new MockTick(0, 74.6, 75.310000, 74.540000));
        ticks.add(new MockTick(0, 74.1, 75.467000, 74.010000));
        ticks.add(new MockTick(0, 73.740000,74.700000, 73.546000));
        ticks.add(new MockTick(0, 73.390000, 73.830000, 72.720000));
        ticks.add(new MockTick(0, 73.25, 73.890000, 72.86));
        ticks.add(new MockTick(0, 74.36, 74.410000, 73,26));

        ticks.add(new MockTick(0, 76.510000, 76.830000, 74.820000));
        ticks.add(new MockTick(0, 75.590000, 76.850000, 74.540000));
        ticks.add(new MockTick(0, 75.910000, 76.960000, 75.510000));
        ticks.add(new MockTick(0, 74.610000, 77.070000, 74.560000));
        ticks.add(new MockTick(0, 75.330000, 75.530000, 74.010000));
        ticks.add(new MockTick(0, 75.010000, 75.500000, 74.510000));
        ticks.add(new MockTick(0, 75.620000, 76.210000, 75.250000));
        ticks.add(new MockTick(0, 76.040000, 76.460000, 75.092800));
        ticks.add(new MockTick(0, 76.450000, 76.450000, 75.435000));
        ticks.add(new MockTick(0, 76.260000, 76.470000, 75.840000));
        ticks.add(new MockTick(0, 76.850000, 77.000000, 76.190000));


        ParabolicSarIndicator sar = new ParabolicSarIndicator(new MockTimeSeries(ticks));

        assertEquals(sar.getValue(0).toString(), "NaN");
        assertDecimalEquals(sar.getValue(1), 74.640000000000000568434188608080);
        assertDecimalEquals(sar.getValue(2), 74.640000000000000568434188608080); // start with up trend
        assertDecimalEquals(sar.getValue(3), 76.269900000000006912159733474255); // switch to downtrend
        assertDecimalEquals(sar.getValue(4), 76.234502000000006773916538804770); // hold trend...
        assertDecimalEquals(sar.getValue(5), 76.200611960000006763493729522452);
        assertDecimalEquals(sar.getValue(6), 76.112987481600006697590288240463);
        assertDecimalEquals(sar.getValue(7), 75.958968232704006684543855953962);
        assertDecimalEquals(sar.getValue(8), 75.699850774087686058830877300352);
        assertDecimalEquals(sar.getValue(9), 75.461462712160671083174936939031); // switch to up trend
        assertDecimalEquals(sar.getValue(10), 72.719999999999998863131622783840);// hold trend
        assertDecimalEquals(sar.getValue(11), 72.802199999999998851762939011678);
        assertDecimalEquals(sar.getValue(12), 72.964111999999998670318746007979);
        assertDecimalEquals(sar.getValue(13), 73.203865279999998374933056766167);
        assertDecimalEquals(sar.getValue(14), 73.513156057599997959241591161117);
        assertDecimalEquals(sar.getValue(15), 73.797703572991997576805442804471);
        assertDecimalEquals(sar.getValue(16), 74.059487287152637224964186316356);
        assertDecimalEquals(sar.getValue(17), 74.300328304180425701270230347291);
        assertDecimalEquals(sar.getValue(18), 74.521902039845991099471790855751);
        assertDecimalEquals(sar.getValue(19), 74.725749876658311265817226523534);
        assertDecimalEquals(sar.getValue(20), 74.913289886525645818855027337894);
    }

}