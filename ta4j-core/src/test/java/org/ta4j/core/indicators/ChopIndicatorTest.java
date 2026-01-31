/*
 * SPDX-License-Identifier: MIT
 */
/**
 *
 */
package org.ta4j.core.indicators;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * @author jtomkinson
 *
 */
public class ChopIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public ChopIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    /**
     * this will assert that choppiness is high if market price is not moving
     */
    @Test
    public void testChoppy() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withName("low volatility series").build();
        for (int i = 0; i < 50; i++) {
            series.barBuilder().openPrice(21.5).highPrice(21.5 + 1).lowPrice(21.5 - 1).closePrice(21.5).add();
        }
        var ci1 = new ChopIndicator(series, 14, 100);
        int HIGH_CHOPPINESS_VALUE = 85;
        assertTrue(ci1.getValue(series.getEndIndex()).doubleValue() > HIGH_CHOPPINESS_VALUE);
    }

    /**
     * this will assert that choppiness is low if market price is trending
     * significantly
     */
    @Test
    public void testTradeableTrend() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withName("low volatility series").build();
        float value = 21.5f;
        for (int i = 0; i < 50; i++) {
            series.barBuilder().openPrice(value).highPrice(value + 1).lowPrice(value - 1).closePrice(value).add();
            value += 2.0f;
        }
        ChopIndicator ci1 = new ChopIndicator(series, 14, 100);
        int LOW_CHOPPINESS_VALUE = 30;
        assertTrue(ci1.getValue(series.getEndIndex()).doubleValue() < LOW_CHOPPINESS_VALUE);
    }

    // TODO: this test class needs better cases

}
