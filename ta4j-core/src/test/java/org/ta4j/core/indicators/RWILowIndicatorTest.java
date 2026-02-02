/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.ta4j.core.TestUtils.assertIndicatorEquals;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Testing the RWILowIndicator
 */
public class RWILowIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    /**
     * TODO: Just graphically Excel-Sheet validation with hard coded results. Excel
     * formula needed
     */
    private final ExternalIndicatorTest xls;

    public RWILowIndicatorTest(NumFactory numFactory) {
        super((data, params) -> new RWILowIndicator(data, (int) params[0]), numFactory);
        xls = new XLSIndicatorTest(this.getClass(), "RWIHL.xls", 9, numFactory);
    }

    @Test
    public void randomWalkIndexHigh() throws Exception {
        BarSeries series = xls.getSeries();
        RWILowIndicator rwih = (RWILowIndicator) getIndicator(series, 20);
        assertIndicatorEquals(getIndicator(series, 20), rwih);
    }
}
