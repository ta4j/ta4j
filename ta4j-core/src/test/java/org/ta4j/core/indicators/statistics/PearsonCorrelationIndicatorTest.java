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
package org.ta4j.core.indicators.statistics;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.*;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.mocks.MockBar;

import java.util.ArrayList;
import java.util.List;

import static org.ta4j.core.TATestsUtils.assertDecimalEquals;

public class PearsonCorrelationIndicatorTest {

    private Indicator<Decimal> close, volume;
    
    @Before
    public void setUp() {
        List<Bar> bars = new ArrayList<Bar>();
        // close, volume
        bars.add(new MockBar(6, 100));
        bars.add(new MockBar(7, 105));
        bars.add(new MockBar(9, 130));
        bars.add(new MockBar(12, 160));
        bars.add(new MockBar(11, 150));
        bars.add(new MockBar(10, 130));
        bars.add(new MockBar(11, 95));
        bars.add(new MockBar(13, 120));
        bars.add(new MockBar(15, 180));
        bars.add(new MockBar(12, 160));
        bars.add(new MockBar(8, 150));
        bars.add(new MockBar(4, 200));
        bars.add(new MockBar(3, 150));
        bars.add(new MockBar(4, 85));
        bars.add(new MockBar(3, 70));
        bars.add(new MockBar(5, 90));
        bars.add(new MockBar(8, 100));
        bars.add(new MockBar(9, 95));
        bars.add(new MockBar(11, 110));
        bars.add(new MockBar(10, 95));

        TimeSeries data = new BaseTimeSeries(bars);
        close = new ClosePriceIndicator(data);
        volume = new VolumeIndicator(data, 2);
    }

    @Test
    public void test() {
    PearsonCorrelationIndicator coef = new PearsonCorrelationIndicator(close, volume, 5);
       
		assertDecimalEquals(coef.getValue(1), 0.94947469058476818628408908843839);
		assertDecimalEquals(coef.getValue(2), 0.9640797490298872);
		assertDecimalEquals(coef.getValue(3), 0.9666189661412724);
		assertDecimalEquals(coef.getValue(4), 0.9219);
		assertDecimalEquals(coef.getValue(5), 0.9205);
		assertDecimalEquals(coef.getValue(6), 0.4565);
		assertDecimalEquals(coef.getValue(7), -0.4622);
		assertDecimalEquals(coef.getValue(8), 0.05747);
		assertDecimalEquals(coef.getValue(9), 0.1442);
		assertDecimalEquals(coef.getValue(10), -0.1263);
		assertDecimalEquals(coef.getValue(11), -0.5345);
		assertDecimalEquals(coef.getValue(12), -0.7275);
		assertDecimalEquals(coef.getValue(13), 0.1676);
		assertDecimalEquals(coef.getValue(14), 0.2506);
		assertDecimalEquals(coef.getValue(15), -0.2938);
		assertDecimalEquals(coef.getValue(16), -0.3586);
		assertDecimalEquals(coef.getValue(17), 0.1713);
		assertDecimalEquals(coef.getValue(18), 0.9841);
		assertDecimalEquals(coef.getValue(19), 0.9799);
    }
}
