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


import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.Decimal;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockTimeSeries;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.ta4j.core.TATestsUtils.assertDecimalEquals;


public class IIIIndicatorTest {
    @Test
    public void intradayIntensityIndex() {
        ZonedDateTime now = ZonedDateTime.now();
        List<Bar> bars = new ArrayList<>();
        bars.add(new MockBar(now, 0d, 10d, 12d, 8d, 0d, 200d, 0));//2-2 * 200 / 4
        bars.add(new MockBar(now, 0d, 8d, 10d, 7d, 0d, 100d, 0));//1-2 *100 / 3
        bars.add(new MockBar(now, 0d, 9d, 15d, 6d, 0d, 300d, 0));//3-6 *300 /9
        bars.add(new MockBar(now, 0d, 20d, 40d, 5d, 0d, 50d, 0));//15-20 *50 / 35
        bars.add(new MockBar(now, 0d, 30d, 30d, 3d, 0d, 600d, 0));//27-0 *600 /27

        TimeSeries series = new MockTimeSeries(bars);
        IIIIndicator iiiIndicator = new IIIIndicator(series);
        assertDecimalEquals(iiiIndicator.getValue(0), 0);
        assertDecimalEquals(iiiIndicator.getValue(1), (2*8d-10d-7d)/((10d-7d)*100d));
        assertDecimalEquals(iiiIndicator.getValue(2), (2*9d-15d-6d)/((15d-6d)*300d));
        assertDecimalEquals(iiiIndicator.getValue(3), (2*20d-40d-5d)/((40d-5d)*50d));
        assertDecimalEquals(iiiIndicator.getValue(4), (2*30d-30d-3d)/((30d-3d)*600d));
    }
}
