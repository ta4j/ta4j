/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ROCVIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    BarSeries series;

    public ROCVIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().closePrice(1355.69).volume(1000).add();
        series.barBuilder().closePrice(1325.51).volume(3000).add();
        series.barBuilder().closePrice(1335.02).volume(3500).add();
        series.barBuilder().closePrice(1313.72).volume(2200).add();
        series.barBuilder().closePrice(1319.99).volume(2300).add();
        series.barBuilder().closePrice(1331.85).volume(200).add();
        series.barBuilder().closePrice(1329.04).volume(2700).add();
        series.barBuilder().closePrice(1362.16).volume(5000).add();
        series.barBuilder().closePrice(1365.51).volume(1000).add();
        series.barBuilder().closePrice(1374.02).volume(2500).add();
    }

    @Test
    public void test() {
        ROCVIndicator roc = new ROCVIndicator(series, 3);
        assertEquals(3, roc.getCountOfUnstableBars());

        assertNumEquals(0, roc.getValue(0));
        assertNumEquals(200, roc.getValue(1));
        assertNumEquals(250, roc.getValue(2));
        assertNumEquals(120, roc.getValue(3));
        assertNumEquals(-23.333333333333332, roc.getValue(4));
        assertNumEquals(-94.28571428571429, roc.getValue(5));
        assertNumEquals(22.727272727272727, roc.getValue(6));
        assertNumEquals(117.3913043478261, roc.getValue(7));
        assertNumEquals(400, roc.getValue(8));
        assertNumEquals(-7.407407407407407, roc.getValue(9));
    }
}
