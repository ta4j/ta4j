/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarBuilder;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class DarkCloudIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    private BarSeries series;

    public DarkCloudIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    private List<Bar> generateUptrend() {
        List<Bar> bars = new ArrayList<Bar>(30);
        for (int i = 0; i < 17; ++i) {
            bars.add(
                    new MockBarBuilder(numFactory).openPrice(i).closePrice(i + 6).highPrice(i + 8).lowPrice(i).build());
        }

        return bars;
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).withBars(generateUptrend()).build();
    }

    @Test
    public void getValue() {
        series.barBuilder().openPrice(17).closePrice(25).highPrice(25).lowPrice(17).add();
        series.barBuilder().openPrice(28).closePrice(20).highPrice(28).lowPrice(15).add();
        series.barBuilder().openPrice(19).closePrice(16).highPrice(19).lowPrice(15).add();

        var dc = new DarkCloudIndicator(series);
        assertFalse(dc.getValue(17));
        assertTrue(dc.getValue(18));
        assertFalse(dc.getValue(19));
    }

    @Test
    public void getValueWhenIndexBelowUnstableBars() {
        var dc = new DarkCloudIndicator(series);
        assertFalse(dc.getValue(0));
        assertFalse(dc.getValue(1));
    }

    @Test
    public void getValueWhenFirstBarBodyTooSmall() {
        series.barBuilder().openPrice(17).closePrice(18).highPrice(18).lowPrice(17).add();
        series.barBuilder().openPrice(28).closePrice(20).highPrice(28).lowPrice(20).add();

        var dc = new DarkCloudIndicator(series);
        assertFalse(dc.getValue(18));
    }

    @Test
    public void getValueWhenFirstBarIsBearish() {
        series.barBuilder().openPrice(25).closePrice(17).highPrice(25).lowPrice(17).add();
        series.barBuilder().openPrice(28).closePrice(20).highPrice(28).lowPrice(20).add();

        var dc = new DarkCloudIndicator(series);
        assertFalse(dc.getValue(18));
    }

    @Test
    public void getValueWhenSecondBarIsBullish() {
        series.barBuilder().openPrice(17).closePrice(25).highPrice(25).lowPrice(17).add();
        series.barBuilder().openPrice(20).closePrice(28).highPrice(28).lowPrice(20).add();

        var dc = new DarkCloudIndicator(series);
        assertFalse(dc.getValue(18));
    }

    @Test
    public void getValueWhenSecondBarBodyTooSmall() {
        series.barBuilder().openPrice(17).closePrice(25).highPrice(25).lowPrice(17).add();
        series.barBuilder().openPrice(24).closePrice(23.5).highPrice(24).lowPrice(23).add();

        var dc = new DarkCloudIndicator(series);
        assertFalse(dc.getValue(18));
    }

    @Test
    public void getValueWhenSecondBarDoesNotOpenAboveFirstClose() {
        series.barBuilder().openPrice(17).closePrice(25).highPrice(25).lowPrice(17).add();
        series.barBuilder().openPrice(24).closePrice(20).highPrice(24).lowPrice(20).add();

        var dc = new DarkCloudIndicator(series);
        assertFalse(dc.getValue(18));
    }

    @Test
    public void getValueWhenSecondBarDoesNotClosePastMidpoint() {
        series.barBuilder().openPrice(17).closePrice(25).highPrice(25).lowPrice(17).add();
        series.barBuilder().openPrice(28).closePrice(22).highPrice(28).lowPrice(22).add();

        var dc = new DarkCloudIndicator(series);
        assertFalse(dc.getValue(18));
    }

    @Test
    public void getValueWhenPatternAppearsInDowntrend() {
        BarSeries downtrendSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        for (int i = 46; i > 29; --i) {
            downtrendSeries.barBuilder().openPrice(i).closePrice(i - 6).highPrice(i).lowPrice(i - 8).add();
        }

        downtrendSeries.barBuilder().openPrice(17).closePrice(25).highPrice(25).lowPrice(17).add();
        downtrendSeries.barBuilder().openPrice(28).closePrice(20).highPrice(28).lowPrice(20).add();

        var dc = new DarkCloudIndicator(downtrendSeries);
        assertFalse(dc.getValue(18));
    }

    @Test
    public void getValueWhenSecondBarClosesExactlyAtMidpoint() {
        series.barBuilder().openPrice(17).closePrice(25).highPrice(25).lowPrice(17).add();
        series.barBuilder().openPrice(28).closePrice(21).highPrice(28).lowPrice(20).add();

        var dc = new DarkCloudIndicator(series);
        assertFalse(dc.getValue(18));
    }

    @Test
    public void getValueWhenSecondBarClosesBarelyBelowMidpoint() {
        series.barBuilder().openPrice(17).closePrice(25).highPrice(25).lowPrice(17).add();
        series.barBuilder().openPrice(28).closePrice(20.99).highPrice(28).lowPrice(19).add();

        var dc = new DarkCloudIndicator(series);
        assertTrue(dc.getValue(18));
    }
}
