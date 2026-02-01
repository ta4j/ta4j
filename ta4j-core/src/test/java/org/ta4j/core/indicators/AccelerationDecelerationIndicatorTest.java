/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class AccelerationDecelerationIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries series;

    public AccelerationDecelerationIndicatorTest(NumFactory numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().build();

        series.barBuilder().openPrice(0).closePrice(0).highPrice(16).lowPrice(8).add();
        series.barBuilder().openPrice(0).closePrice(0).highPrice(12).lowPrice(6).add();
        series.barBuilder().openPrice(0).closePrice(0).highPrice(18).lowPrice(14).add();
        series.barBuilder().openPrice(0).closePrice(0).highPrice(10).lowPrice(6).add();
        series.barBuilder().openPrice(0).closePrice(0).highPrice(8).lowPrice(4).add();
    }

    @Test
    public void calculateWithSma2AndSma3() {
        var acceleration = new AccelerationDecelerationIndicator(series, 2, 3);

        assertNumEquals(0, acceleration.getValue(0));
        assertNumEquals(0, acceleration.getValue(1));
        assertNumEquals(0.08333333333, acceleration.getValue(2));
        assertNumEquals(0.41666666666, acceleration.getValue(3));
        assertNumEquals(-2, acceleration.getValue(4));
    }

    @Test
    public void withSma1AndSma2() {
        var acceleration = new AccelerationDecelerationIndicator(series, 1, 2);

        assertNumEquals(0, acceleration.getValue(0));
        assertNumEquals(0, acceleration.getValue(1));
        assertNumEquals(0, acceleration.getValue(2));
        assertNumEquals(0, acceleration.getValue(3));
        assertNumEquals(0, acceleration.getValue(4));
    }

    @Test
    public void withSmaDefault() {
        var acceleration = new AccelerationDecelerationIndicator(series);

        assertNumEquals(0, acceleration.getValue(0));
        assertNumEquals(0, acceleration.getValue(1));
        assertNumEquals(0, acceleration.getValue(2));
        assertNumEquals(0, acceleration.getValue(3));
        assertNumEquals(0, acceleration.getValue(4));
    }
}
