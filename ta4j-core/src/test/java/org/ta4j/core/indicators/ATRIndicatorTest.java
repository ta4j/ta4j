package org.ta4j.core.indicators;

import org.junit.Test;
import org.ta4j.core.*;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.num.Num;

import java.time.ZonedDateTime;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertIndicatorEquals;

public class ATRIndicatorTest extends AbstractIndicatorTest<TimeSeries, Num> {

    private ExternalIndicatorTest xls;

    public ATRIndicatorTest(Function<Number,Num> numFunction) throws Exception {
        super((data, params) -> new ATRIndicator( data, (int) params[0]),numFunction);
        xls = new XLSIndicatorTest(this.getClass(), "ATR.xls", 7, numFunction);
    }

    @Test
    public void testDummy() throws Exception {
        TimeSeries series = new BaseTimeSeries.SeriesBuilder().withNumTypeOf(numFunction).build();
        Bar a = (new MockBar(0, 12, 15, 8,numFunction));
        series.addBar(new MockBar(ZonedDateTime.now().minusSeconds(5), 0, 12, 15, 8, 0, 0, 0,numFunction));
        series.addBar(new MockBar(ZonedDateTime.now().minusSeconds(4), 0, 8, 11, 6, 0, 0, 0,numFunction));
        series.addBar(new MockBar(ZonedDateTime.now().minusSeconds(3), 0, 15, 17, 14, 0, 0, 0,numFunction));
        series.addBar(new MockBar(ZonedDateTime.now().minusSeconds(2), 0, 15, 17, 14, 0, 0, 0,numFunction));
        series.addBar(new MockBar(ZonedDateTime.now().minusSeconds(1), 0, 0, 0, 2, 0, 0, 0,numFunction));
        Indicator<Num> indicator = getIndicator(series, 3);

        assertEquals(7d, indicator.getValue(0).doubleValue(), TestUtils.GENERAL_OFFSET);
        assertEquals(6d / 3 + (1 - 1d / 3) * indicator.getValue(0).doubleValue(),
                indicator.getValue(1).doubleValue(), TestUtils.GENERAL_OFFSET);
        assertEquals(9d / 3 + (1 - 1d / 3) * indicator.getValue(1).doubleValue(),
                indicator.getValue(2).doubleValue(), TestUtils.GENERAL_OFFSET);
        assertEquals(3d / 3 + (1 - 1d / 3) * indicator.getValue(2).doubleValue(),
                indicator.getValue(3).doubleValue(), TestUtils.GENERAL_OFFSET);
        assertEquals(15d / 3 + (1 - 1d / 3) * indicator.getValue(3).doubleValue(),
                indicator.getValue(4).doubleValue(), TestUtils.GENERAL_OFFSET);
    }

    @Test
    public void testXls() throws Exception {
        TimeSeries xlsSeries = xls.getSeries();
        Indicator<Num> indicator;

        indicator = getIndicator(xlsSeries, 1);
        assertIndicatorEquals(xls.getIndicator(1), indicator);
        assertEquals(4.8, indicator.getValue(indicator.getTimeSeries().getEndIndex()).doubleValue(), TestUtils.GENERAL_OFFSET);

        indicator = getIndicator(xlsSeries, 3);
        assertIndicatorEquals(xls.getIndicator(3), indicator);
        assertEquals(7.4225, indicator.getValue(indicator.getTimeSeries().getEndIndex()).doubleValue(), TestUtils.GENERAL_OFFSET);

        indicator = getIndicator(xlsSeries, 13);
        assertIndicatorEquals(xls.getIndicator(13), indicator);
        assertEquals(8.8082, indicator.getValue(indicator.getTimeSeries().getEndIndex()).doubleValue(), TestUtils.GENERAL_OFFSET);
    }

}
