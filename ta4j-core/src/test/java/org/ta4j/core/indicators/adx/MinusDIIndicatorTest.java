/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.adx;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertIndicatorEquals;

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
        // With barCount=1, unstable period is 1, so index 0 returns NaN
        // Values after unstable period will differ initially but should converge. Only
        // check end value.
        assertEquals(0.0, indicator.getValue(indicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);

        indicator = getIndicator(xlsSeries, 3);
        // With barCount=3, unstable period is 3, so indices 0-2 return NaN
        // Values after unstable period will differ initially but should converge. Only
        // check end value.
        assertEquals(21.0711, indicator.getValue(indicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);

        indicator = getIndicator(xlsSeries, 13);
        // With barCount=13, unstable period is 13, so indices 0-12 return NaN
        // Values after unstable period will differ initially but should converge. Only
        // check end value.
        assertEquals(20.9020, indicator.getValue(indicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);
    }

}
