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
package org.ta4j.core;

import org.junit.Test;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.BigDecimalNum;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

import java.time.ZonedDateTime;
import java.util.function.Function;

import static junit.framework.TestCase.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

public class SeriesBuilderTest extends AbstractIndicatorTest {

    public SeriesBuilderTest(Function<Number, Num> numFunction){
        super(numFunction);
    }

    private final BaseTimeSeries.SeriesBuilder seriesBuilder = new BaseTimeSeries.SeriesBuilder().withNumTypeOf(numFunction);

    @Test
    public void testBuilder(){

        TimeSeries defaultSeries = seriesBuilder.build(); // build a new empty unnamed time series using BigDecimal as delegate
        TimeSeries defaultSeriesName = seriesBuilder.withName("default").build(); // build a new empty unnamed time series using BigDecimal as delegate
        TimeSeries doubleSeries = seriesBuilder.withMaxBarCount(100).withNumTypeOf(DoubleNum.class).withName("useDouble").build();
        TimeSeries bigDecimalSeries = seriesBuilder.withMaxBarCount(100).withNumTypeOf(BigDecimalNum.class).withName("useBigDecimal").build();

        for(int i=1000; i>=0;i--){
            defaultSeries.addBar(ZonedDateTime.now().minusSeconds(i),i,i,i,i,i);
            defaultSeriesName.addBar(ZonedDateTime.now().minusSeconds(i),i,i,i,i,i);
            doubleSeries.addBar(ZonedDateTime.now().minusSeconds(i),i,i,i,i,i);
            bigDecimalSeries.addBar(ZonedDateTime.now().minusSeconds(i),i,i,i,i,i);
        }

        assertNumEquals(defaultSeries.getBar(1000).getClosePrice(),0);
        assertNumEquals(defaultSeries.getBar(0).getClosePrice(),1000);
        assertEquals(defaultSeriesName.getName(),"default");
        assertNumEquals(doubleSeries.getBar(0).getClosePrice(),99);
        assertNumEquals(bigDecimalSeries.getBar(0).getClosePrice(),99);
    }

    @Test
    public void testNumFunctions(){

        TimeSeries series = seriesBuilder.withNumTypeOf(DoubleNum.class).build();
        assertNumEquals(series.numOf(12), DoubleNum.valueOf(12));

        TimeSeries seriesB = seriesBuilder.withNumTypeOf(BigDecimalNum.class).build();
        assertNumEquals(seriesB.numOf(12), BigDecimalNum.valueOf(12));
    }

    @Test(expected = ClassCastException.class)
    public void testWrongNumType(){
        TimeSeries series = seriesBuilder.withNumTypeOf(BigDecimalNum.class).build();
        assertNumEquals(series.numOf(12), DoubleNum.valueOf(12));
    }
}
