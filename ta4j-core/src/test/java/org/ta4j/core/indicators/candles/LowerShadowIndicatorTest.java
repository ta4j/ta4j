/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class LowerShadowIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries series;

    public LowerShadowIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(10).closePrice(18).highPrice(20).lowPrice(10).add();
        series.barBuilder().openPrice(17).closePrice(20).highPrice(21).lowPrice(17).add();
        series.barBuilder().openPrice(15).closePrice(15).highPrice(16).lowPrice(14).add();
        series.barBuilder().openPrice(15).closePrice(11).highPrice(15).lowPrice(8).add();
        series.barBuilder().openPrice(11).closePrice(12).highPrice(12).lowPrice(10).add();
    }

    @Test
    public void getValue() {
        LowerShadowIndicator lowerShadow = new LowerShadowIndicator(series);
        assertNumEquals(0, lowerShadow.getValue(0));
        assertNumEquals(0, lowerShadow.getValue(1));
        assertNumEquals(1, lowerShadow.getValue(2));
        assertNumEquals(3, lowerShadow.getValue(3));
        assertNumEquals(1, lowerShadow.getValue(4));
    }
}
