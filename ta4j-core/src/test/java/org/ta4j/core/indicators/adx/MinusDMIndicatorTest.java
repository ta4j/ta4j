/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.adx;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class MinusDMIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public MinusDMIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void zeroDirectionalMovement() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        var yesterdayBar = series.barBuilder().openPrice(0).closePrice(0).highPrice(0).lowPrice(0).build();
        var todayBar = series.barBuilder().openPrice(0).closePrice(0).highPrice(0).lowPrice(0).build();
        series.addBar(yesterdayBar);
        series.addBar(todayBar);
        var down = new MinusDMIndicator(series);
        assertNumEquals(0, down.getValue(1));
    }

    @Test
    public void zeroDirectionalMovement2() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        var yesterdayBar = series.barBuilder().openPrice(0).closePrice(0).highPrice(6).lowPrice(12).build();
        var todayBar = series.barBuilder().openPrice(0).closePrice(0).highPrice(12).lowPrice(6).build();
        series.addBar(yesterdayBar);
        series.addBar(todayBar);
        MinusDMIndicator down = new MinusDMIndicator(series);
        assertNumEquals(0, down.getValue(1));
    }

    @Test
    public void zeroDirectionalMovement3() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        var yesterdayBar = series.barBuilder().openPrice(0).closePrice(0).highPrice(6).lowPrice(6).build();
        var todayBar = series.barBuilder().openPrice(0).closePrice(0).highPrice(12).lowPrice(4).build();
        series.addBar(yesterdayBar);
        series.addBar(todayBar);
        MinusDMIndicator down = new MinusDMIndicator(series);
        assertNumEquals(0, down.getValue(1));
    }

    @Test
    public void positiveDirectionalMovement() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        var yesterdayBar = series.barBuilder().openPrice(0).closePrice(0).highPrice(6).lowPrice(20).build();
        var todayBar = series.barBuilder().openPrice(0).closePrice(0).highPrice(12).lowPrice(4).build();
        series.addBar(yesterdayBar);
        series.addBar(todayBar);
        MinusDMIndicator down = new MinusDMIndicator(series);
        assertNumEquals(16, down.getValue(1));
    }
}
