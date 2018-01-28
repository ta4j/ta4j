package org.ta4j.core;

import org.junit.Test;
import org.ta4j.core.Num.BigDecimalNum;
import org.ta4j.core.Num.DoubleNum;

import java.time.ZonedDateTime;

import static junit.framework.TestCase.assertEquals;
import static org.ta4j.core.TATestsUtils.assertNumEquals;

public class SeriesBuilderTest {

    private final BaseTimeSeries.SeriesBuilder seriesBuilder = new BaseTimeSeries.SeriesBuilder();

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
