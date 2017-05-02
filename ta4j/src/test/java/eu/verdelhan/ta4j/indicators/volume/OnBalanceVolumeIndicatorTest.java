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
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import org.joda.time.DateTime;
import org.junit.Test;

public class OnBalanceVolumeIndicatorTest {

    @Test
    public void getValue() {
        DateTime now = DateTime.now();
        List<Tick> ticks = new ArrayList<Tick>();
        ticks.add(new MockTick(now, 0, 10, 0, 0, 0, 4, 0));
        ticks.add(new MockTick(now, 0, 5, 0, 0, 0, 2, 0));
        ticks.add(new MockTick(now, 0, 6, 0, 0, 0, 3, 0));
        ticks.add(new MockTick(now, 0, 7, 0, 0, 0, 8, 0));
        ticks.add(new MockTick(now, 0, 7, 0, 0, 0, 6, 0));
        ticks.add(new MockTick(now, 0, 6, 0, 0, 0, 10, 0));

        OnBalanceVolumeIndicator obv = new OnBalanceVolumeIndicator(new MockTimeSeries(ticks));
        assertDecimalEquals(obv.getValue(0), 0);
        assertDecimalEquals(obv.getValue(1), -2);
        assertDecimalEquals(obv.getValue(2), 1);
        assertDecimalEquals(obv.getValue(3), 9);
        assertDecimalEquals(obv.getValue(4), 9);
        assertDecimalEquals(obv.getValue(5), -1);
    }
    
    @Test
    public void stackOverflowError() {
        List<Tick> bigListOfTicks = new ArrayList<Tick>();
        for (int i = 0; i < 10000; i++) {
            bigListOfTicks.add(new MockTick(i));
        }
        MockTimeSeries bigSeries = new MockTimeSeries(bigListOfTicks);
        OnBalanceVolumeIndicator obv = new OnBalanceVolumeIndicator(bigSeries);
        // If a StackOverflowError is thrown here, then the RecursiveCachedIndicator
        // does not work as intended.
        assertDecimalEquals(obv.getValue(9999), 0);
    }
}
