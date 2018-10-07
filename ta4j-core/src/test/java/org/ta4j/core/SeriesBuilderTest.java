package org.ta4j.core;

import org.junit.Test;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.PrecisionNum;
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
        TimeSeries precisionSeries = seriesBuilder.withMaxBarCount(100).withNumTypeOf(PrecisionNum.class).withName("useBigDecimal").build();

        for(int i=1000; i>=0;i--){
            defaultSeries.addBar(ZonedDateTime.now().minusSeconds(i),i,i,i,i,i);
            defaultSeriesName.addBar(ZonedDateTime.now().minusSeconds(i),i,i,i,i,i);
            doubleSeries.addBar(ZonedDateTime.now().minusSeconds(i),i,i,i,i,i);
            precisionSeries.addBar(ZonedDateTime.now().minusSeconds(i), i, i, i, i, i);
        }

        assertNumEquals(0,defaultSeries.getBar(1000).getClosePrice());
        assertNumEquals(1000,defaultSeries.getBar(0).getClosePrice());
        assertEquals(defaultSeriesName.getName(),"default");
        assertNumEquals(99,doubleSeries.getBar(0).getClosePrice());
        assertNumEquals(99, precisionSeries.getBar(0).getClosePrice());
    }

    @Test
    public void testNumFunctions(){

        TimeSeries series = seriesBuilder.withNumTypeOf(DoubleNum.class).build();
        assertNumEquals(series.numOf(12), DoubleNum.valueOf(12));

        TimeSeries seriesB = seriesBuilder.withNumTypeOf(PrecisionNum.class).build();
        assertNumEquals(seriesB.numOf(12), PrecisionNum.valueOf(12));
    }

    @Test(expected = ClassCastException.class)
    public void testWrongNumType(){
        TimeSeries series = seriesBuilder.withNumTypeOf(PrecisionNum.class).build();
        assertNumEquals(series.numOf(12), DoubleNum.valueOf(12));
    }
}
