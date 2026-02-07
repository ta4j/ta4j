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

public class MinusDIIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    private final ExternalIndicatorTest xls;

    public MinusDIIndicatorTest(NumFactory nf) {
        super((data, params) -> new MinusDIIndicator(data, (int) params[0]), nf);
        xls = new XLSIndicatorTest(this.getClass(), "ADX.xls", 13, numFactory);
    }

    @Test
    public void xlsTest() throws Exception {
        BarSeries xlsSeries = xls.getSeries();
        Indicator<Num> indicator;

        indicator = getIndicator(xlsSeries, 1);
        assertEquals(2, indicator.getCountOfUnstableBars());
        assertEquals(0.0, indicator.getValue(indicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);

        indicator = getIndicator(xlsSeries, 3);
        assertEquals(4, indicator.getCountOfUnstableBars());
        assertEquals(21.0711, indicator.getValue(indicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);

        indicator = getIndicator(xlsSeries, 13);
        assertEquals(14, indicator.getCountOfUnstableBars());
        assertEquals(20.9020, indicator.getValue(indicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);
    }

}
