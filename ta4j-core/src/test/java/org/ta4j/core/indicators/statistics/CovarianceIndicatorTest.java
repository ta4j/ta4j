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
import org.ta4j.core.Num.Num;
import org.ta4j.core.TestUtils;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.mocks.MockBar;

import java.time.ZonedDateTime;
import java.util.function.Function;

import static org.ta4j.core.TestUtils.assertNumEquals;

public class CovarianceIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private Indicator<Num> close, volume;

    public CovarianceIndicatorTest(Function<Number, Num> numFunction) {
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
        CovarianceIndicator covar = new CovarianceIndicator(close, volume, 5);

		TestUtils.assertNumEquals(covar.getValue(0), 0);
		assertNumEquals(covar.getValue(1), 26.25);
		assertNumEquals(covar.getValue(2), 63.3333);
		assertNumEquals(covar.getValue(3), 143.75);
		TestUtils.assertNumEquals(covar.getValue(4), 156);
		assertNumEquals(covar.getValue(5), 60.8);
		assertNumEquals(covar.getValue(6), 15.2);
		assertNumEquals(covar.getValue(7), -17.6);
		TestUtils.assertNumEquals(covar.getValue(8), 4);
		assertNumEquals(covar.getValue(9), 11.6);
		assertNumEquals(covar.getValue(10), -14.4);
		assertNumEquals(covar.getValue(11), -100.2);
		assertNumEquals(covar.getValue(12), -70.0);
		assertNumEquals(covar.getValue(13), 24.6);
		assertNumEquals(covar.getValue(14), 35.0);
		assertNumEquals(covar.getValue(15), -19.0);
		assertNumEquals(covar.getValue(16), -47.8);
		assertNumEquals(covar.getValue(17), 11.4);
		assertNumEquals(covar.getValue(18), 55.8);
		assertNumEquals(covar.getValue(19), 33.4);
    }

    @Test
    public void firstValueShouldBeZero() {
        CovarianceIndicator covar = new CovarianceIndicator(close, volume, 5);
        TestUtils.assertNumEquals(covar.getValue(0), 0);
    }

    @Test
    public void shouldBeZeroWhenTimeFrameIs1() {
        CovarianceIndicator covar = new CovarianceIndicator(close, volume, 1);
        TestUtils.assertNumEquals(covar.getValue(3), 0);
        TestUtils.assertNumEquals(covar.getValue(8), 0);
    }
}
