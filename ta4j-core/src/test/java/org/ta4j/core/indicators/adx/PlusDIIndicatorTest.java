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

public class PlusDIIndicatorTest extends AbstractIndicatorTest<TimeSeries,Num> {

    private ExternalIndicatorTest xls;

    public PlusDIIndicatorTest(Function<Number, Num> nf) throws Exception {
        super((data, params) -> new PlusDIIndicator((TimeSeries) data, (int) params[0]), nf);
        xls = new XLSIndicatorTest(this.getClass(), "ADX.xls", 12, numFunction);
    }

    @Test
    public void testAgainstExternalData() throws Exception {
        TimeSeries xlsSeries = xls.getSeries();
        Indicator<Num> actualIndicator;

        actualIndicator = getIndicator(xlsSeries, 1);
        assertIndicatorEquals(xls.getIndicator( 1), actualIndicator);
        assertEquals(12.5, actualIndicator.getValue(actualIndicator.getTimeSeries().getEndIndex()).doubleValue(), TestUtils.GENERAL_OFFSET);

        actualIndicator = getIndicator(xlsSeries, 3);
        assertIndicatorEquals(xls.getIndicator( 3), actualIndicator);
        assertEquals(22.8407, actualIndicator.getValue(actualIndicator.getTimeSeries().getEndIndex()).doubleValue(), TestUtils.GENERAL_OFFSET);

        actualIndicator = getIndicator(xlsSeries, 13);
        assertIndicatorEquals(xls.getIndicator( 13), actualIndicator);
        assertEquals(22.1399, actualIndicator.getValue(actualIndicator.getTimeSeries().getEndIndex()).doubleValue(), TestUtils.GENERAL_OFFSET);
    }

}
