/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.adx;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.Indicator;
import org.ta4j.core.TestUtils;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.XLSIndicatorTest;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class PlusDIIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    private final ExternalIndicatorTest xls;

    public PlusDIIndicatorTest(NumFactory numFactory) {
        super((data, params) -> new PlusDIIndicator(data, (int) params[0]), numFactory);
        xls = new XLSIndicatorTest(this.getClass(), "ADX.xls", 12, numFactory);
    }

    @Test
    public void testAgainstExternalData() throws Exception {
        BarSeries xlsSeries = xls.getSeries();
        Indicator<Num> actualIndicator;

        actualIndicator = getIndicator(xlsSeries, 1);
        assertEquals(2, actualIndicator.getCountOfUnstableBars());
        assertEquals(12.5, actualIndicator.getValue(actualIndicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);

        actualIndicator = getIndicator(xlsSeries, 3);
        assertEquals(4, actualIndicator.getCountOfUnstableBars());
        assertEquals(22.8407, actualIndicator.getValue(actualIndicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);

        actualIndicator = getIndicator(xlsSeries, 13);
        assertEquals(14, actualIndicator.getCountOfUnstableBars());
        assertEquals(22.1399, actualIndicator.getValue(actualIndicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);
    }

}
