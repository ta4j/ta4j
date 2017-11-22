/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.volume;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockTimeSeries;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.ta4j.core.TATestsUtils.assertDecimalEquals;

public class OnBalanceVolumeIndicatorTest {

    @Test
    public void getValue() {
        ZonedDateTime now = ZonedDateTime.now();
        List<Bar> bars = new ArrayList<>();
        bars.add(new MockBar(now, 0, 10, 0, 0, 0, 4, 0));
        bars.add(new MockBar(now, 0, 5, 0, 0, 0, 2, 0));
        bars.add(new MockBar(now, 0, 6, 0, 0, 0, 3, 0));
        bars.add(new MockBar(now, 0, 7, 0, 0, 0, 8, 0));
        bars.add(new MockBar(now, 0, 7, 0, 0, 0, 6, 0));
        bars.add(new MockBar(now, 0, 6, 0, 0, 0, 10, 0));

        OnBalanceVolumeIndicator obv = new OnBalanceVolumeIndicator(new MockTimeSeries(bars));
        assertDecimalEquals(obv.getValue(0), 0);
        assertDecimalEquals(obv.getValue(1), -2);
        assertDecimalEquals(obv.getValue(2), 1);
        assertDecimalEquals(obv.getValue(3), 9);
        assertDecimalEquals(obv.getValue(4), 9);
        assertDecimalEquals(obv.getValue(5), -1);
    }

    @Test
    public void stackOverflowError() {
        List<Bar> bigListOfBars = new ArrayList<Bar>();
        for (int i = 0; i < 10000; i++) {
            bigListOfBars.add(new MockBar(i));
        }
        MockTimeSeries bigSeries = new MockTimeSeries(bigListOfBars);
        OnBalanceVolumeIndicator obv = new OnBalanceVolumeIndicator(bigSeries);
        // If a StackOverflowError is thrown here, then the RecursiveCachedIndicator
        // does not work as intended.
        assertDecimalEquals(obv.getValue(9999), 0);
    }
}
