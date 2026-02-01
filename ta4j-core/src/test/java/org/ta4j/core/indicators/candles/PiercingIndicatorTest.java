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

public class PiercingIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    private BarSeries series;

    public PiercingIndicatorTest(NumFactory numFactory) {
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
        series.barBuilder().openPrice(20).closePrice(27).highPrice(31).lowPrice(20).add();
        series.barBuilder().openPrice(27).closePrice(30).highPrice(31).lowPrice(27).add();

        var piercing = new PiercingIndicator(series);
        assertFalse(piercing.getValue(17));
        assertTrue(piercing.getValue(18));
        assertFalse(piercing.getValue(19));
    }

    @Test
    public void getValueWhenIndexBelowUnstableBars() {
        var piercing = new PiercingIndicator(series);
        assertFalse(piercing.getValue(0));
        assertFalse(piercing.getValue(1));
    }

    @Test
    public void getValueWhenFirstBarBodyTooSmall() {
        series.barBuilder().openPrice(26).closePrice(25.5).highPrice(26).lowPrice(25).add();
        series.barBuilder().openPrice(20).closePrice(27).highPrice(27).lowPrice(20).add();

        var piercing = new PiercingIndicator(series);
        assertFalse(piercing.getValue(18));
    }

    @Test
    public void getValueWhenFirstBarIsBullish() {
        series.barBuilder().openPrice(23).closePrice(29).highPrice(29).lowPrice(23).add();
        series.barBuilder().openPrice(20).closePrice(27).highPrice(27).lowPrice(20).add();

        var piercing = new PiercingIndicator(series);
        assertFalse(piercing.getValue(18));
    }

    @Test
    public void getValueWhenSecondBarIsBearish() {
        series.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(23).add();
        series.barBuilder().openPrice(27).closePrice(20).highPrice(27).lowPrice(20).add();

        var piercing = new PiercingIndicator(series);
        assertFalse(piercing.getValue(18));
    }

    @Test
    public void getValueWhenSecondBarBodyTooSmall() {
        series.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(23).add();
        series.barBuilder().openPrice(23).closePrice(23.5).highPrice(24).lowPrice(23).add();

        var piercing = new PiercingIndicator(series);
        assertFalse(piercing.getValue(18));
    }

    @Test
    public void getValueWhenSecondBarDoesNotOpenBelowFirstClose() {
        series.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(23).add();
        series.barBuilder().openPrice(24).closePrice(27).highPrice(27).lowPrice(24).add();

        var piercing = new PiercingIndicator(series);
        assertFalse(piercing.getValue(18));
    }

    @Test
    public void getValueWhenSecondBarDoesNotClosePastMidpoint() {
        series.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(23).add();
        series.barBuilder().openPrice(20).closePrice(25).highPrice(25).lowPrice(20).add();

        var piercing = new PiercingIndicator(series);
        assertFalse(piercing.getValue(18));
    }

    @Test
    public void getValueWhenPatternAppearsInUptrend() {
        BarSeries uptrendSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        for (int i = 0; i < 17; ++i) {
            uptrendSeries.barBuilder().openPrice(i).closePrice(i + 6).highPrice(i + 8).lowPrice(i).add();
        }

        uptrendSeries.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(23).add();
        uptrendSeries.barBuilder().openPrice(20).closePrice(27).highPrice(27).lowPrice(20).add();

        var piercing = new PiercingIndicator(uptrendSeries);
        assertFalse(piercing.getValue(18));
    }

    @Test
    public void getValueWhenSecondBarClosesExactlyAtMidpoint() {
        series.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(23).add();
        series.barBuilder().openPrice(20).closePrice(26).highPrice(26).lowPrice(20).add();

        var piercing = new PiercingIndicator(series);
        assertFalse(piercing.getValue(18));
    }

    @Test
    public void getValueWhenSecondBarClosesBarelyAboveMidpoint() {
        series.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(23).add();
        series.barBuilder().openPrice(20).closePrice(26.01).highPrice(27).lowPrice(20).add();

        var piercing = new PiercingIndicator(series);
        assertTrue(piercing.getValue(18));
    }

}
