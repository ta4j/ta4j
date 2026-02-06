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

public class ADXIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    private final ExternalIndicatorTest xls;

    public ADXIndicatorTest(NumFactory numFactory) {
        super((data, params) -> new ADXIndicator(data, (int) params[0], (int) params[1]), numFactory);
        xls = new XLSIndicatorTest(this.getClass(), "ADX.xls", 15, this.numFactory);
    }

    @Test
    public void externalData() throws Exception {
        BarSeries series = xls.getSeries();
        Indicator<Num> actualIndicator;

        actualIndicator = getIndicator(series, 1, 1);
        assertEquals(3, actualIndicator.getCountOfUnstableBars());
        assertEquals(100.0, actualIndicator.getValue(actualIndicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);

        actualIndicator = getIndicator(series, 3, 2);
        assertEquals(6, actualIndicator.getCountOfUnstableBars());
        assertEquals(12.1330, actualIndicator.getValue(actualIndicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);

        actualIndicator = getIndicator(series, 13, 8);
        assertEquals(22, actualIndicator.getCountOfUnstableBars());
        assertEquals(7.3884, actualIndicator.getValue(actualIndicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);
    }

}
