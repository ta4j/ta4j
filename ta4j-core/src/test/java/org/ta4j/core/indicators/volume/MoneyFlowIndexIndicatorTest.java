/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class MoneyFlowIndexIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public MoneyFlowIndexIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void givenBarCount_whenGetValueForIndexWithinBarCount_thenReturnNaN() {
        var series = new MockBarSeriesBuilder().build();
        series.barBuilder().openPrice(10).closePrice(10).highPrice(10).lowPrice(10).volume(0).add();
        series.barBuilder().openPrice(10).closePrice(10).highPrice(10).lowPrice(10).volume(10).add();
        series.barBuilder().openPrice(10).closePrice(10).highPrice(10).lowPrice(10).volume(10).add();
        series.barBuilder().openPrice(10).closePrice(10).highPrice(10).lowPrice(10).volume(10).add();
        series.barBuilder().openPrice(10).closePrice(10).highPrice(10).lowPrice(10).volume(10).add();
        series.barBuilder().openPrice(9).closePrice(9).highPrice(9).lowPrice(9).volume(10).add();
        series.barBuilder().openPrice(11).closePrice(11).highPrice(11).lowPrice(11).volume(10).add();

        var mfi = new MoneyFlowIndexIndicator(series, 5);

        assertTrue(mfi.getValue(0).isNaN());
        assertTrue(mfi.getValue(1).isNaN());
        assertTrue(mfi.getValue(2).isNaN());
        assertTrue(mfi.getValue(3).isNaN());
        assertTrue(mfi.getValue(4).isNaN());
        assertFalse(mfi.getValue(5).isNaN());
    }

    @Test
    public void givenBarCountOf1_whenGetValue_thenReturnEdgeCaseCorrectedValue() {
        var series = new MockBarSeriesBuilder().build();
        series.barBuilder().openPrice(10).closePrice(10).highPrice(10).lowPrice(10).volume(0).add();
        series.barBuilder().openPrice(9).closePrice(9).highPrice(9).lowPrice(9).volume(10).add();
        series.barBuilder().openPrice(10).closePrice(10).highPrice(10).lowPrice(10).volume(10).add();
        series.barBuilder().openPrice(11).closePrice(11).highPrice(11).lowPrice(11).volume(10).add();
        series.barBuilder().openPrice(12).closePrice(12).highPrice(12).lowPrice(12).volume(10).add();
        series.barBuilder().openPrice(11).closePrice(11).highPrice(11).lowPrice(11).volume(10).add();
        series.barBuilder().openPrice(11).closePrice(11).highPrice(11).lowPrice(11).volume(10).add();

        var mfi = new MoneyFlowIndexIndicator(series, 1);

        assertTrue(mfi.getValue(0).isNaN());
        assertNumEquals(1.098901098901095, mfi.getValue(1));
        assertNumEquals(99.00990099009901, mfi.getValue(2));
        assertNumEquals(99.09909909909909, mfi.getValue(3));
        assertNumEquals(99.17355371900827, mfi.getValue(4));
        assertNumEquals(0.9009009009008935, mfi.getValue(5));
    }

    @Test
    public void givenBarCountOf3_whenGetValue_thenReturnCorrectValue() {
        var series = new MockBarSeriesBuilder().build();
        series.barBuilder().openPrice(10).closePrice(10).highPrice(10).lowPrice(10).volume(0).add();
        series.barBuilder().openPrice(9).closePrice(9).highPrice(9).lowPrice(9).volume(10).add();
        series.barBuilder().openPrice(10).closePrice(10).highPrice(10).lowPrice(10).volume(10).add();
        series.barBuilder().openPrice(11).closePrice(11).highPrice(11).lowPrice(11).volume(10).add();
        series.barBuilder().openPrice(12).closePrice(12).highPrice(12).lowPrice(12).volume(10).add();
        series.barBuilder().openPrice(11).closePrice(11).highPrice(11).lowPrice(11).volume(10).add();
        series.barBuilder().openPrice(12).closePrice(12).highPrice(12).lowPrice(12).volume(10).add();
        series.barBuilder().openPrice(9).closePrice(9).highPrice(9).lowPrice(9).volume(10).add();

        var mfi = new MoneyFlowIndexIndicator(series, 3);

        assertTrue(mfi.getValue(0).isNaN());
        assertTrue(mfi.getValue(1).isNaN());
        assertTrue(mfi.getValue(2).isNaN());
        assertNumEquals(70, mfi.getValue(3));
        assertNumEquals(99.69788519637463, mfi.getValue(4));
        assertNumEquals(67.64705882352942, mfi.getValue(5));
        assertNumEquals(68.57142857142857, mfi.getValue(6));
        assertNumEquals(37.5, mfi.getValue(7));
    }
}
