package org.ta4j.core.indicators.adx;

import org.junit.Test;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.Indicator;
import org.ta4j.core.TestUtils;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.XLSIndicatorTest;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertIndicatorEquals;

public class MinusDIIndicatorTest extends AbstractIndicatorTest<TimeSeries, Num> {

    private ExternalIndicatorTest xls;

    public MinusDIIndicatorTest(Function<Number, Num> nf) {
        super((data, params) -> new MinusDIIndicator(data, (int) params[0]),nf);
        xls = new XLSIndicatorTest(this.getClass(), "ADX.xls", 13, numFunction);
    }

    @Test
    public void xlsTest() throws Exception {
        TimeSeries xlsSeries = xls.getSeries();
        Indicator<Num> indicator;

        indicator = getIndicator(xlsSeries, 1);
        assertIndicatorEquals(xls.getIndicator(1), indicator);
        assertEquals(0.0, indicator.getValue(indicator.getTimeSeries().getEndIndex()).doubleValue(), TestUtils.GENERAL_OFFSET);

        indicator = getIndicator(xlsSeries, 3);
        assertIndicatorEquals(xls.getIndicator(3), indicator);
        assertEquals(21.0711, indicator.getValue(indicator.getTimeSeries().getEndIndex()).doubleValue(), TestUtils.GENERAL_OFFSET);

        indicator = getIndicator(xlsSeries, 13);
        assertIndicatorEquals(xls.getIndicator(13), indicator);
        assertEquals(20.9020, indicator.getValue(indicator.getTimeSeries().getEndIndex()).doubleValue(), TestUtils.GENERAL_OFFSET);
    }

}
