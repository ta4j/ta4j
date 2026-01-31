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

public class BullishKickerIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    private BarSeries series;

    public BullishKickerIndicatorTest(NumFactory numFactory) {
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
        series.barBuilder().openPrice(24).closePrice(30).highPrice(31).lowPrice(23).add();
        series.barBuilder().openPrice(27).closePrice(30).highPrice(31).lowPrice(27).add();

        var bullishKicker = new BullishKickerIndicator(series);
        assertFalse(bullishKicker.getValue(17));
        assertTrue(bullishKicker.getValue(18));
        assertFalse(bullishKicker.getValue(19));
    }

    @Test
    public void getValueWhenIndexBelowUnstableBars() {
        var bullishKicker = new BullishKickerIndicator(series);
        assertFalse(bullishKicker.getValue(0));
    }

    @Test
    public void getValueWhenFirstBarBodyTooSmall() {
        series.barBuilder().openPrice(26).closePrice(25.5).highPrice(26).lowPrice(25).add();
        series.barBuilder().openPrice(27).closePrice(31).highPrice(31).lowPrice(27).add();

        var bullishKicker = new BullishKickerIndicator(series);
        assertFalse(bullishKicker.getValue(18));
    }

    @Test
    public void getValueWhenFirstBarIsBullish() {
        series.barBuilder().openPrice(23).closePrice(29).highPrice(29).lowPrice(23).add();
        series.barBuilder().openPrice(30).closePrice(35).highPrice(35).lowPrice(30).add();

        var bullishKicker = new BullishKickerIndicator(series);
        assertFalse(bullishKicker.getValue(18));
    }

    @Test
    public void getValueWhenSecondBarBodyTooSmall() {
        series.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(23).add();
        series.barBuilder().openPrice(24).closePrice(24.5).highPrice(25).lowPrice(24).add();

        var bullishKicker = new BullishKickerIndicator(series);
        assertFalse(bullishKicker.getValue(18));
    }

    @Test
    public void getValueWhenSecondBarIsBearish() {
        series.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(23).add();
        series.barBuilder().openPrice(30).closePrice(24).highPrice(30).lowPrice(24).add();

        var bullishKicker = new BullishKickerIndicator(series);
        assertFalse(bullishKicker.getValue(18));
    }

    @Test
    public void getValueWhenSecondBarDoesNotGapUp() {
        series.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(23).add();
        series.barBuilder().openPrice(22).closePrice(27).highPrice(27).lowPrice(22).add();

        var bullishKicker = new BullishKickerIndicator(series);
        assertFalse(bullishKicker.getValue(18));
    }

    @Test
    public void getValueWhenSecondBarDoesNotCloseAboveFirstOpen() {
        series.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(23).add();
        series.barBuilder().openPrice(24).closePrice(28).highPrice(28).lowPrice(24).add();

        var bullishKicker = new BullishKickerIndicator(series);
        assertFalse(bullishKicker.getValue(18));
    }

    @Test
    public void getValueWhenPatternAppearsInUptrend() {
        BarSeries uptrendSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        for (int i = 0; i < 17; ++i) {
            uptrendSeries.barBuilder().openPrice(i).closePrice(i + 6).highPrice(i + 8).lowPrice(i).add();
        }

        uptrendSeries.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(23).add();
        uptrendSeries.barBuilder().openPrice(24).closePrice(30).highPrice(30).lowPrice(24).add();

        var bullishKicker = new BullishKickerIndicator(uptrendSeries);
        assertFalse(bullishKicker.getValue(18));
    }

    @Test
    public void getValueWhenSecondBarClosesExactlyAtFirstOpen() {
        series.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(23).add();
        series.barBuilder().openPrice(24).closePrice(29).highPrice(29).lowPrice(24).add();

        var bullishKicker = new BullishKickerIndicator(series);
        assertFalse(bullishKicker.getValue(18));
    }

    @Test
    public void getValueWhenSecondBarClosesBarelyAboveFirstOpen() {
        series.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(23).add();
        series.barBuilder().openPrice(24).closePrice(29.01).highPrice(30).lowPrice(24).add();

        var bullishKicker = new BullishKickerIndicator(series);
        assertTrue(bullishKicker.getValue(18));
    }

}
