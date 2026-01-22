/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class CloseLocationValueIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries series;

    public CloseLocationValueIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().build();
        series.barBuilder().openPrice(10).closePrice(18).highPrice(20).lowPrice(10).add();
        series.barBuilder().openPrice(17).closePrice(20).highPrice(21).lowPrice(17).add();
        series.barBuilder().openPrice(15).closePrice(15).highPrice(16).lowPrice(14).add();
        series.barBuilder().openPrice(15).closePrice(11).highPrice(15).lowPrice(8).add();
        series.barBuilder().openPrice(11).closePrice(12).highPrice(12).lowPrice(10).add();
        series.barBuilder().openPrice(10).closePrice(10).highPrice(10).lowPrice(10).add();
        series.barBuilder().openPrice(11).closePrice(12).highPrice(12).lowPrice(10).add();
        series.barBuilder().openPrice(11).closePrice(120).highPrice(140).lowPrice(100).add();
    }

    @Test
    public void getValue() {
        CloseLocationValueIndicator clv = new CloseLocationValueIndicator(series);
        assertNumEquals(0.6, clv.getValue(0));
        assertNumEquals(0.5, clv.getValue(1));
        assertNumEquals(0, clv.getValue(2));
        assertNumEquals(-1d / 7, clv.getValue(3));
        assertNumEquals(1, clv.getValue(4));
    }

    @Test
    public void returnZeroIfHighEqualsLow() {
        CloseLocationValueIndicator clv = new CloseLocationValueIndicator(series);
        assertNumEquals(NaN.NaN, clv.getValue(5));
        assertNumEquals(1, clv.getValue(6));
        assertNumEquals(0, clv.getValue(7));
    }
}
