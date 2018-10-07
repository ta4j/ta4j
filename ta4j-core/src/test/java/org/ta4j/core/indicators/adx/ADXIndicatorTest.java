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

public class ADXIndicatorTest extends AbstractIndicatorTest<TimeSeries,Num> {

    private ExternalIndicatorTest xls;

    public ADXIndicatorTest(Function<Number, Num> nf) throws Exception {
        super((data, params) -> new ADXIndicator((TimeSeries) data, (int) params[0], (int) params[1]),nf);
        xls = new XLSIndicatorTest(this.getClass(), "ADX.xls", 15, numFunction);
    }

    @Test
    public void externalData() throws Exception {
        TimeSeries series = xls.getSeries();
        Indicator<Num> actualIndicator;

        actualIndicator = getIndicator(series, 1, 1);
        assertIndicatorEquals(xls.getIndicator(1, 1), actualIndicator);
        assertEquals(100.0, actualIndicator.getValue(actualIndicator.getTimeSeries().getEndIndex()).doubleValue(), TestUtils.GENERAL_OFFSET);

        actualIndicator = getIndicator(series, 3, 2);
        assertIndicatorEquals(xls.getIndicator(3, 2), actualIndicator);
        assertEquals(12.1330, actualIndicator.getValue(actualIndicator.getTimeSeries().getEndIndex()).doubleValue(), TestUtils.GENERAL_OFFSET);

        actualIndicator = getIndicator(series, 13, 8);
        assertIndicatorEquals(xls.getIndicator(13, 8), actualIndicator);
        assertEquals(7.3884, actualIndicator.getValue(actualIndicator.getTimeSeries().getEndIndex()).doubleValue(), TestUtils.GENERAL_OFFSET);
    }

}
