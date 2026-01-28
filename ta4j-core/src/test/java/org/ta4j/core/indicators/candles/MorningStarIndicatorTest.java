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

public class MorningStarIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    private BarSeries series;

    public MorningStarIndicatorTest(NumFactory numFactory) {
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
        series.barBuilder().openPrice(22).closePrice(22.2).highPrice(23).lowPrice(20).add();
        series.barBuilder().openPrice(21).closePrice(27).highPrice(27).lowPrice(20).add();
        series.barBuilder().openPrice(27).closePrice(30).highPrice(31).lowPrice(27).add();

        var ms = new MorningStarIndicator(series);
        assertFalse(ms.getValue(17));
        assertFalse(ms.getValue(18));
        assertTrue(ms.getValue(19));
        assertFalse(ms.getValue(20));
    }

    @Test
    public void getValueWhenIndexBelowUnstableBars() {
        var ms = new MorningStarIndicator(series);
        assertFalse(ms.getValue(0));
        assertFalse(ms.getValue(1));
        assertFalse(ms.getValue(2));
    }

    @Test
    public void getValueWhenFirstBarBodyTooSmall() {
        series.barBuilder().openPrice(26).closePrice(25.5).highPrice(26).lowPrice(25).add();
        series.barBuilder().openPrice(24).closePrice(24.2).highPrice(25).lowPrice(23).add();
        series.barBuilder().openPrice(23).closePrice(27).highPrice(27).lowPrice(23).add();

        var ms = new MorningStarIndicator(series);
        assertFalse(ms.getValue(19));
    }

    @Test
    public void getValueWhenFirstBarIsBullish() {
        series.barBuilder().openPrice(23).closePrice(29).highPrice(29).lowPrice(23).add();
        series.barBuilder().openPrice(22).closePrice(22.2).highPrice(23).lowPrice(20).add();
        series.barBuilder().openPrice(21).closePrice(27).highPrice(27).lowPrice(20).add();

        var ms = new MorningStarIndicator(series);
        assertFalse(ms.getValue(19));
    }

    @Test
    public void getValueWhenSecondBarBodyTooLarge() {
        series.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(23).add();
        series.barBuilder().openPrice(22).closePrice(26).highPrice(27).lowPrice(20).add();
        series.barBuilder().openPrice(25).closePrice(28).highPrice(28).lowPrice(25).add();

        var ms = new MorningStarIndicator(series);
        assertFalse(ms.getValue(19));
    }

    @Test
    public void getValueWhenSecondBarDoesNotGapDown() {
        series.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(23).add();
        series.barBuilder().openPrice(24).closePrice(24.2).highPrice(25).lowPrice(23).add();
        series.barBuilder().openPrice(23).closePrice(27).highPrice(27).lowPrice(23).add();

        var ms = new MorningStarIndicator(series);
        assertFalse(ms.getValue(19));
    }

    @Test
    public void getValueWhenThirdBarDoesNotClosePastMidpoint() {
        series.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(23).add();
        series.barBuilder().openPrice(22).closePrice(22.2).highPrice(23).lowPrice(20).add();
        series.barBuilder().openPrice(21).closePrice(25).highPrice(25).lowPrice(20).add();

        var ms = new MorningStarIndicator(series);
        assertFalse(ms.getValue(19));
    }

    @Test
    public void getValueWhenThirdBarIsBearish() {
        series.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(23).add();
        series.barBuilder().openPrice(22).closePrice(22.2).highPrice(23).lowPrice(20).add();
        series.barBuilder().openPrice(27).closePrice(21).highPrice(27).lowPrice(20).add();

        var ms = new MorningStarIndicator(series);
        assertFalse(ms.getValue(19));
    }

    @Test
    public void getValueWhenThirdBarBodyTooSmall() {
        series.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(23).add();
        series.barBuilder().openPrice(22).closePrice(22.2).highPrice(23).lowPrice(20).add();
        series.barBuilder().openPrice(26).closePrice(26.5).highPrice(27).lowPrice(26).add();

        var ms = new MorningStarIndicator(series);
        assertFalse(ms.getValue(19));
    }

    @Test
    public void getValueWhenPatternAppearsInUptrend() {
        BarSeries uptrendSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        for (int i = 0; i < 17; ++i) {
            uptrendSeries.barBuilder().openPrice(i).closePrice(i + 6).highPrice(i + 8).lowPrice(i).add();
        }

        uptrendSeries.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(23).add();
        uptrendSeries.barBuilder().openPrice(22).closePrice(22.2).highPrice(23).lowPrice(20).add();
        uptrendSeries.barBuilder().openPrice(21).closePrice(27).highPrice(27).lowPrice(20).add();

        var ms = new MorningStarIndicator(uptrendSeries);
        assertFalse(ms.getValue(19));
    }

    @Test
    public void getValueWhenThirdBarClosesExactlyAtMidpoint() {
        series.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(23).add();
        series.barBuilder().openPrice(22).closePrice(22.2).highPrice(23).lowPrice(20).add();
        series.barBuilder().openPrice(21).closePrice(26).highPrice(26).lowPrice(20).add();

        var ms = new MorningStarIndicator(series);
        assertFalse(ms.getValue(19));
    }

    @Test
    public void getValueWhenThirdBarClosesBarelyAboveMidpoint() {
        series.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(23).add();
        series.barBuilder().openPrice(22).closePrice(22.2).highPrice(23).lowPrice(20).add();
        series.barBuilder().openPrice(21).closePrice(26.01).highPrice(27).lowPrice(20).add();

        var ms = new MorningStarIndicator(series);
        assertTrue(ms.getValue(19));
    }

}
