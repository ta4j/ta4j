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

public class ThreeInsideUpIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    private BarSeries series;

    public ThreeInsideUpIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    private List<Bar> generateDowntrend() {
        List<Bar> bars = new ArrayList<Bar>(30);
        for (int i = 46; i > 29; --i) {
            bars.add(
                    new MockBarBuilder(numFactory).openPrice(i).closePrice(i - 6).highPrice(i).lowPrice(i - 8).build());
        }

        return bars;
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).withBars(generateDowntrend()).build();
    }

    @Test
    public void getValue() {
        series.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(23).add();
        series.barBuilder().openPrice(28).closePrice(22).highPrice(29).lowPrice(20).add();
        series.barBuilder().openPrice(24).closePrice(27).highPrice(27).lowPrice(24).add();
        series.barBuilder().openPrice(26).closePrice(29).highPrice(29).lowPrice(26).add();
        series.barBuilder().openPrice(27).closePrice(30).highPrice(31).lowPrice(27).add();

        var tiu = new ThreeInsideUpIndicator(series);
        assertFalse(tiu.getValue(17));
        assertFalse(tiu.getValue(18));
        assertFalse(tiu.getValue(19));
        assertTrue(tiu.getValue(20));
        assertFalse(tiu.getValue(21));
    }

    @Test
    public void getValueWhenIndexBelowUnstableBars() {
        var tiu = new ThreeInsideUpIndicator(series);
        assertFalse(tiu.getValue(0));
        assertFalse(tiu.getValue(1));
        assertFalse(tiu.getValue(2));
    }

    @Test
    public void getValueWhenHaramiExistsButThirdBarDoesNotConfirm() {
        series.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(23).add();
        series.barBuilder().openPrice(24).closePrice(27).highPrice(27).lowPrice(24).add();
        series.barBuilder().openPrice(26).closePrice(28).highPrice(28).lowPrice(26).add();

        var tiu = new ThreeInsideUpIndicator(series);
        assertFalse(tiu.getValue(19)); // Index where third bar is - should be false
    }

    @Test
    public void getValueWhenHaramiExistsButThirdBarIsBearish() {
        series.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(23).add();
        series.barBuilder().openPrice(24).closePrice(27).highPrice(27).lowPrice(24).add();
        series.barBuilder().openPrice(30).closePrice(28).highPrice(31).lowPrice(27).add();

        var tiu = new ThreeInsideUpIndicator(series);
        assertFalse(tiu.getValue(19));
    }

    @Test
    public void getValueWhenPatternAppearsInUptrend() {
        BarSeries uptrendSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        for (int i = 10; i < 27; ++i) {
            uptrendSeries.barBuilder().openPrice(i).closePrice(i + 6).highPrice(i + 8).lowPrice(i).add();
        }

        uptrendSeries.barBuilder().openPrice(35).closePrice(29).highPrice(35).lowPrice(29).add();
        uptrendSeries.barBuilder().openPrice(30).closePrice(33).highPrice(33).lowPrice(30).add();
        uptrendSeries.barBuilder().openPrice(32).closePrice(36).highPrice(37).lowPrice(32).add();

        var tiu = new ThreeInsideUpIndicator(uptrendSeries);
        assertFalse(tiu.getValue(19));
    }

    @Test
    public void getValueWhenThirdBarClosesExactlyAtFirstBarOpen() {
        series.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(23).add();
        series.barBuilder().openPrice(24).closePrice(27).highPrice(27).lowPrice(24).add();
        series.barBuilder().openPrice(26).closePrice(29).highPrice(29).lowPrice(26).add();

        var tiu = new ThreeInsideUpIndicator(series);
        assertFalse(tiu.getValue(19));
    }

    @Test
    public void getValueWhenThirdBarClosesBarelyAboveFirstBarOpen() {
        series.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(23).add();
        series.barBuilder().openPrice(24).closePrice(27).highPrice(27).lowPrice(24).add();
        series.barBuilder().openPrice(26).closePrice(29.01).highPrice(29.5).lowPrice(26).add();

        var tiu = new ThreeInsideUpIndicator(series);
        assertTrue(tiu.getValue(19));
    }
}
