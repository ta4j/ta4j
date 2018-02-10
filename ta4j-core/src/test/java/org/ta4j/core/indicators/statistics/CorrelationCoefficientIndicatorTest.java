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
package org.ta4j.core.indicators.statistics;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.TestUtils;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.num.Num;

import java.time.ZonedDateTime;
import java.util.function.Function;

import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

public class CorrelationCoefficientIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private Indicator<Num> close, volume;

    public CorrelationCoefficientIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        TimeSeries data = new BaseTimeSeries.SeriesBuilder().withNumTypeOf(numFunction).build();
        int i = 20;
        // close, volume
        data.addBar(new MockBar(ZonedDateTime.now().minusSeconds(i--),6,100,numFunction));
        data.addBar(new MockBar(ZonedDateTime.now().minusSeconds(i--),7,105,numFunction));
        data.addBar(new MockBar(ZonedDateTime.now().minusSeconds(i--),9,130,numFunction));
        data.addBar(new MockBar(ZonedDateTime.now().minusSeconds(i--),12,160,numFunction));
        data.addBar(new MockBar(ZonedDateTime.now().minusSeconds(i--),11,150,numFunction));
        data.addBar(new MockBar(ZonedDateTime.now().minusSeconds(i--),10, 130,numFunction));
        data.addBar(new MockBar(ZonedDateTime.now().minusSeconds(i--),11, 95,numFunction));
        data.addBar(new MockBar(ZonedDateTime.now().minusSeconds(i--),13,120,numFunction));
        data.addBar(new MockBar(ZonedDateTime.now().minusSeconds(i--),15,180,numFunction));
        data.addBar(new MockBar(ZonedDateTime.now().minusSeconds(i--),12,160,numFunction));
        data.addBar(new MockBar(ZonedDateTime.now().minusSeconds(i--),8, 150,numFunction));
        data.addBar(new MockBar(ZonedDateTime.now().minusSeconds(i--),4, 200,numFunction));
        data.addBar(new MockBar(ZonedDateTime.now().minusSeconds(i--),3, 150,numFunction));
        data.addBar(new MockBar(ZonedDateTime.now().minusSeconds(i--),4, 85,numFunction));
        data.addBar(new MockBar(ZonedDateTime.now().minusSeconds(i--),3, 70,numFunction));
        data.addBar(new MockBar(ZonedDateTime.now().minusSeconds(i--),5, 90,numFunction));
        data.addBar(new MockBar(ZonedDateTime.now().minusSeconds(i--),8, 100,numFunction));
        data.addBar(new MockBar(ZonedDateTime.now().minusSeconds(i--),9, 95,numFunction));
        data.addBar(new MockBar(ZonedDateTime.now().minusSeconds(i--),11, 110,numFunction));
        data.addBar(new MockBar(ZonedDateTime.now().minusSeconds(i),10, 95,numFunction));

        close = new ClosePriceIndicator(data);
        volume = new VolumeIndicator(data, 2);
    }

    @Test
    public void usingTimeFrame5UsingClosePriceAndVolume() {
        CorrelationCoefficientIndicator coef = new CorrelationCoefficientIndicator(close, volume, 5);

        assertTrue(coef.getValue(0).isNaN());

		TestUtils.assertNumEquals(coef.getValue(1), 1);
		assertNumEquals(coef.getValue(2), 0.8773);
		assertNumEquals(coef.getValue(3), 0.9073);
		assertNumEquals(coef.getValue(4), 0.9219);
		assertNumEquals(coef.getValue(5), 0.9205);
		assertNumEquals(coef.getValue(6), 0.4565);
		assertNumEquals(coef.getValue(7), -0.4622);
		assertNumEquals(coef.getValue(8), 0.05747);
		assertNumEquals(coef.getValue(9), 0.1442);
		assertNumEquals(coef.getValue(10), -0.1263);
		assertNumEquals(coef.getValue(11), -0.5345);
		assertNumEquals(coef.getValue(12), -0.7275);
		assertNumEquals(coef.getValue(13), 0.1676);
		assertNumEquals(coef.getValue(14), 0.2506);
		assertNumEquals(coef.getValue(15), -0.2938);
		assertNumEquals(coef.getValue(16), -0.3586);
		assertNumEquals(coef.getValue(17), 0.1713);
		assertNumEquals(coef.getValue(18), 0.9841);
		assertNumEquals(coef.getValue(19), 0.9799);
    }
}
