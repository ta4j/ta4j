/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.donchian;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class DonchianChannelUpperIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    private BarSeries series;

    public DonchianChannelUpperIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        this.series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withName("DonchianChannelUpperIndicatorTestSeries")
                .build();

        series.barBuilder().openPrice(100d).highPrice(105d).lowPrice(95d).closePrice(100d).add();
        series.barBuilder().openPrice(105).highPrice(110).lowPrice(100).closePrice(105).add();
        series.barBuilder().openPrice(110).highPrice(115).lowPrice(105).closePrice(110).add();
        series.barBuilder().openPrice(115).highPrice(120).lowPrice(110).closePrice(115).add();
        series.barBuilder().openPrice(120).highPrice(125).lowPrice(115).closePrice(120).add();
        series.barBuilder().openPrice(115).highPrice(120).lowPrice(110).closePrice(115).add();
        series.barBuilder().openPrice(110).highPrice(115).lowPrice(105).closePrice(110).add();
        series.barBuilder().openPrice(105).highPrice(110).lowPrice(100).closePrice(105).add();
        series.barBuilder().openPrice(100).highPrice(105).lowPrice(95).closePrice(100).add();
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testGetValue() {
        var subject = new DonchianChannelUpperIndicator(series, 3);

        assertEquals(numOf(105), subject.getValue(0));
        assertEquals(numOf(110), subject.getValue(1));
        assertEquals(numOf(115), subject.getValue(2));
        assertEquals(numOf(120), subject.getValue(3));
        assertEquals(numOf(125), subject.getValue(4));
        assertEquals(numOf(125), subject.getValue(5));
        assertEquals(numOf(125), subject.getValue(6));
        assertEquals(numOf(120), subject.getValue(7));
        assertEquals(numOf(115), subject.getValue(8));
    }

    @Test
    public void testGetValueWhenBarCountIs1() {
        var subject = new DonchianChannelUpperIndicator(series, 1);

        assertEquals(numOf(105), subject.getValue(0));
        assertEquals(numOf(110), subject.getValue(1));
        assertEquals(numOf(115), subject.getValue(2));
        assertEquals(numOf(120), subject.getValue(3));
        assertEquals(numOf(125), subject.getValue(4));
        assertEquals(numOf(120), subject.getValue(5));
        assertEquals(numOf(115), subject.getValue(6));
        assertEquals(numOf(110), subject.getValue(7));
        assertEquals(numOf(105), subject.getValue(8));
    }
}
