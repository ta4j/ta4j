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
package org.ta4j.core.indicators;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.Num.Num;
import org.ta4j.core.TestUtils;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockTimeSeries;

import java.util.function.Function;

import static org.ta4j.core.TestUtils.assertNumEquals;

public class RAVIIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private TimeSeries data;

    public RAVIIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {

        data = new MockTimeSeries(numFunction,
                110.00, 109.27, 104.69, 107.07, 107.92,
                107.95, 108.70, 107.97, 106.09, 106.03,
                108.65, 109.54, 112.26, 114.38, 117.94
            
        );
    }
    
    @Test
    public void ravi() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(data);
        RAVIIndicator ravi = new RAVIIndicator(closePrice, 3, 8);
        
        TestUtils.assertNumEquals(ravi.getValue(0), 0);
        TestUtils.assertNumEquals(ravi.getValue(1), 0);
        TestUtils.assertNumEquals(ravi.getValue(2), 0);
        assertNumEquals(ravi.getValue(3), -0.6937);
        assertNumEquals(ravi.getValue(4), -1.1411);
        assertNumEquals(ravi.getValue(5), -0.1577);
        assertNumEquals(ravi.getValue(6), 0.229);
        assertNumEquals(ravi.getValue(7), 0.2412);
        assertNumEquals(ravi.getValue(8), 0.1202);
        assertNumEquals(ravi.getValue(9), -0.3324);
        assertNumEquals(ravi.getValue(10), -0.5804);
        assertNumEquals(ravi.getValue(11), 0.2013);
        assertNumEquals(ravi.getValue(12), 1.6156);
        assertNumEquals(ravi.getValue(13), 2.6167);
        assertNumEquals(ravi.getValue(14), 4.0799);
    }
}
