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
package eu.verdelhan.ta4j.indicators.volume;

import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;
import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import org.joda.time.DateTime;
import org.junit.Test;

public class AccumulationDistributionIndicatorTest {

    @Test
    public void accumulationDistribution() {
        DateTime now = DateTime.now();
        List<Tick> ticks = new ArrayList<Tick>();
        ticks.add(new MockTick(now, 0d, 10d, 12d, 8d, 0d, 200d, 0));//2-2 * 200 / 4
        ticks.add(new MockTick(now, 0d, 8d, 10d, 7d, 0d, 100d, 0));//1-2 *100 / 3
        ticks.add(new MockTick(now, 0d, 9d, 15d, 6d, 0d, 300d, 0));//3-6 *300 /9
        ticks.add(new MockTick(now, 0d, 20d, 40d, 5d, 0d, 50d, 0));//15-20 *50 / 35
        ticks.add(new MockTick(now, 0d, 30d, 30d, 3d, 0d, 600d, 0));//27-0 *600 /27
        
        TimeSeries series = new MockTimeSeries(ticks);
        AccumulationDistributionIndicator ac = new AccumulationDistributionIndicator(series);
        assertDecimalEquals(ac.getValue(0), 0);
        assertDecimalEquals(ac.getValue(1), -100d / 3);
        assertDecimalEquals(ac.getValue(2), -100d -(100d / 3));
        assertDecimalEquals(ac.getValue(3), (-250d/35) + (-100d -(100d / 3)));
        assertDecimalEquals(ac.getValue(4), 600d + ((-250d/35) + (-100d -(100d / 3))));
    }
}
