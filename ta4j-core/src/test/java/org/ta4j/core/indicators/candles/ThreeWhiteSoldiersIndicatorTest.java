/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ThreeWhiteSoldiersIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    private BarSeries series;

    public ThreeWhiteSoldiersIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(19.0).closePrice(19.0).highPrice(22.0).lowPrice(15.0).add();
        series.barBuilder().openPrice(10.0).closePrice(18.0).highPrice(20.0).lowPrice(08.0).add();
        series.barBuilder().openPrice(17.0).closePrice(16.0).highPrice(21.0).lowPrice(15.0).add();
        series.barBuilder().openPrice(15.6).closePrice(18.0).highPrice(18.1).lowPrice(14.0).add();
        series.barBuilder().openPrice(16.0).closePrice(19.9).highPrice(20.0).lowPrice(15.0).add();
        series.barBuilder().openPrice(16.8).closePrice(23.0).highPrice(23.0).lowPrice(16.7).add();
        series.barBuilder().openPrice(17.0).closePrice(25.0).highPrice(25.0).lowPrice(17.0).add();
        series.barBuilder().openPrice(23.0).closePrice(16.8).highPrice(24.0).lowPrice(15.0).add();
    }

    @Test
    public void getValue() {
        var tws = new ThreeWhiteSoldiersIndicator(series, 3, series.numFactory().numOf(0.1));
        assertFalse(tws.getValue(0));
        assertFalse(tws.getValue(1));
        assertFalse(tws.getValue(2));
        assertFalse(tws.getValue(3));
        assertFalse(tws.getValue(4));
        assertTrue(tws.getValue(5));
        assertFalse(tws.getValue(6));
        assertFalse(tws.getValue(7));
    }
}
