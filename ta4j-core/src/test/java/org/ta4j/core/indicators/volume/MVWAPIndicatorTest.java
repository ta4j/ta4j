/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan, Ta4j Organization & respective authors (see AUTHORS)

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
import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.ta4j.core.TestUtils.assertNumEquals;

public class MVWAPIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num>{
protected TimeSeries data;

    public MVWAPIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {

        List<Bar> bars = new ArrayList<Bar>();
        bars.add(new MockBar(44.98, 45.05, 45.17, 44.96, 1,numFunction));
        bars.add(new MockBar(45.05, 45.10, 45.15, 44.99, 2,numFunction));
        bars.add(new MockBar(45.11, 45.19, 45.32, 45.11, 1,numFunction));
        bars.add(new MockBar(45.19, 45.14, 45.25, 45.04, 3,numFunction));
        bars.add(new MockBar(45.12, 45.15, 45.20, 45.10, 1,numFunction));
        bars.add(new MockBar(45.15, 45.14, 45.20, 45.10, 2,numFunction));
        bars.add(new MockBar(45.13, 45.10, 45.16, 45.07, 1,numFunction));
        bars.add(new MockBar(45.12, 45.15, 45.22, 45.10, 5,numFunction));
        bars.add(new MockBar(45.15, 45.22, 45.27, 45.14, 1,numFunction));
        bars.add(new MockBar(45.24, 45.43, 45.45, 45.20, 1,numFunction));
        bars.add(new MockBar(45.43, 45.44, 45.50, 45.39, 1,numFunction));
        bars.add(new MockBar(45.43, 45.55, 45.60, 45.35, 5,numFunction));
        bars.add(new MockBar(45.58, 45.55, 45.61, 45.39, 7,numFunction));
        bars.add(new MockBar(45.45, 45.01, 45.55, 44.80, 6,numFunction));
        bars.add(new MockBar(45.03, 44.23, 45.04, 44.17, 1,numFunction));
        bars.add(new MockBar(44.23, 43.95, 44.29, 43.81, 2,numFunction));
        bars.add(new MockBar(43.91, 43.08, 43.99, 43.08, 1,numFunction));
        bars.add(new MockBar(43.07, 43.55, 43.65, 43.06, 7,numFunction));
        bars.add(new MockBar(43.56, 43.95, 43.99, 43.53, 6,numFunction));
        bars.add(new MockBar(43.93, 44.47, 44.58, 43.93, 1,numFunction));
        data = new MockTimeSeries(bars);
    }

    @Test
    public void mvwap() {
        VWAPIndicator vwap = new VWAPIndicator(data, 5);
        MVWAPIndicator mvwap = new MVWAPIndicator(vwap, 8);

        assertNumEquals(mvwap.getValue(8), 45.1271);
        assertNumEquals(mvwap.getValue(9), 45.1399);
        assertNumEquals(mvwap.getValue(10), 45.1530);
        assertNumEquals(mvwap.getValue(11), 45.1790);
        assertNumEquals(mvwap.getValue(12), 45.2227);
        assertNumEquals(mvwap.getValue(13), 45.2533);
        assertNumEquals(mvwap.getValue(14), 45.2769);
        assertNumEquals(mvwap.getValue(15), 45.2844);
        assertNumEquals(mvwap.getValue(16), 45.2668);
        assertNumEquals(mvwap.getValue(17), 45.1386);
        assertNumEquals(mvwap.getValue(18), 44.9487);
    }
}
