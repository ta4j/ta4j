/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertIndicatorEquals;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Testing the RWIHighIndicator
 */
public class RWIHighIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    /**
     * TODO: Just graphically Excel-Sheet validation with hard coded results. Excel
     * formula needed
     */
    private final ExternalIndicatorTest xls;

    public RWIHighIndicatorTest(NumFactory numFactory) {
        super((data, params) -> new RWIHighIndicator(data, (int) params[0]), numFactory);
        xls = new XLSIndicatorTest(this.getClass(), "RWIHL.xls", 8, numFactory);
    }

    @Test
    public void randomWalkIndexHigh() throws Exception {
        Indicator<Num> expected = xls.getIndicator(20);
        RWIHighIndicator rwih = (RWIHighIndicator) getIndicator(expected.getBarSeries(), 20);
        assertEquals(20, rwih.getCountOfUnstableBars());
        assertIndicatorEquals(expected, rwih);
    }
}
