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

public class EveningStarIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    private BarSeries series;

    public EveningStarIndicatorTest(NumFactory numFactory) {
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
        series.barBuilder().openPrice(26).closePrice(25.8).highPrice(27).lowPrice(25).add();
        series.barBuilder().openPrice(27).closePrice(19).highPrice(27).lowPrice(17).add();
        series.barBuilder().openPrice(19).closePrice(16).highPrice(19).lowPrice(15).add();

        var es = new EveningStarIndicator(series);
        assertFalse(es.getValue(17));
        assertFalse(es.getValue(18));
        assertTrue(es.getValue(19));
        assertFalse(es.getValue(20));
    }

    @Test
    public void getValueWhenIndexBelowUnstableBars() {
        var es = new EveningStarIndicator(series);
        assertFalse(es.getValue(0));
        assertFalse(es.getValue(1));
        assertFalse(es.getValue(2));
    }

    @Test
    public void getValueWhenFirstBarBodyTooSmall() {
        series.barBuilder().openPrice(17.5).closePrice(18).highPrice(18).lowPrice(17).add();
        series.barBuilder().openPrice(19).closePrice(18.8).highPrice(19).lowPrice(18).add();
        series.barBuilder().openPrice(19).closePrice(15).highPrice(19).lowPrice(15).add();

        var es = new EveningStarIndicator(series);
        assertFalse(es.getValue(19));
    }

    @Test
    public void getValueWhenFirstBarIsBearish() {
        series.barBuilder().openPrice(25).closePrice(17).highPrice(25).lowPrice(17).add();
        series.barBuilder().openPrice(26).closePrice(25.8).highPrice(27).lowPrice(25).add();
        series.barBuilder().openPrice(27).closePrice(19).highPrice(27).lowPrice(17).add();

        var es = new EveningStarIndicator(series);
        assertFalse(es.getValue(19));
    }

    @Test
    public void getValueWhenSecondBarBodyTooLarge() {
        series.barBuilder().openPrice(17).closePrice(25).highPrice(25).lowPrice(17).add();
        series.barBuilder().openPrice(26).closePrice(22).highPrice(27).lowPrice(21).add();
        series.barBuilder().openPrice(23).closePrice(18).highPrice(23).lowPrice(18).add();

        var es = new EveningStarIndicator(series);
        assertFalse(es.getValue(19));
    }

    @Test
    public void getValueWhenSecondBarDoesNotGapUp() {
        series.barBuilder().openPrice(17).closePrice(25).highPrice(25).lowPrice(17).add();
        series.barBuilder().openPrice(24).closePrice(24.2).highPrice(25).lowPrice(23).add();
        series.barBuilder().openPrice(25).closePrice(19).highPrice(25).lowPrice(19).add();

        var es = new EveningStarIndicator(series);
        assertFalse(es.getValue(19));
    }

    @Test
    public void getValueWhenThirdBarDoesNotClosePastMidpoint() {
        series.barBuilder().openPrice(17).closePrice(25).highPrice(25).lowPrice(17).add();
        series.barBuilder().openPrice(26).closePrice(25.8).highPrice(27).lowPrice(25).add();
        series.barBuilder().openPrice(27).closePrice(22).highPrice(27).lowPrice(21).add();

        var es = new EveningStarIndicator(series);
        assertFalse(es.getValue(19));
    }

    @Test
    public void getValueWhenThirdBarIsBullish() {
        series.barBuilder().openPrice(17).closePrice(25).highPrice(25).lowPrice(17).add();
        series.barBuilder().openPrice(26).closePrice(25.8).highPrice(27).lowPrice(25).add();
        series.barBuilder().openPrice(19).closePrice(27).highPrice(27).lowPrice(19).add();

        var es = new EveningStarIndicator(series);
        assertFalse(es.getValue(19));
    }

    @Test
    public void getValueWhenThirdBarBodyTooSmall() {
        series.barBuilder().openPrice(17).closePrice(25).highPrice(25).lowPrice(17).add();
        series.barBuilder().openPrice(26).closePrice(25.8).highPrice(27).lowPrice(25).add();
        series.barBuilder().openPrice(21).closePrice(20.5).highPrice(21).lowPrice(20).add();

        var es = new EveningStarIndicator(series);
        assertFalse(es.getValue(19));
    }

    @Test
    public void getValueWhenPatternAppearsInDowntrend() {
        BarSeries downtrendSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        for (int i = 46; i > 29; --i) {
            downtrendSeries.barBuilder().openPrice(i).closePrice(i - 6).highPrice(i).lowPrice(i - 8).add();
        }

        downtrendSeries.barBuilder().openPrice(17).closePrice(25).highPrice(25).lowPrice(17).add();
        downtrendSeries.barBuilder().openPrice(26).closePrice(25.8).highPrice(27).lowPrice(25).add();
        downtrendSeries.barBuilder().openPrice(27).closePrice(19).highPrice(27).lowPrice(17).add();

        var es = new EveningStarIndicator(downtrendSeries);
        assertFalse(es.getValue(19));
    }

    @Test
    public void getValueWhenThirdBarClosesExactlyAtMidpoint() {
        series.barBuilder().openPrice(17).closePrice(25).highPrice(25).lowPrice(17).add();
        series.barBuilder().openPrice(26).closePrice(25.8).highPrice(27).lowPrice(25).add();
        series.barBuilder().openPrice(27).closePrice(21).highPrice(27).lowPrice(20).add();

        var es = new EveningStarIndicator(series);
        assertFalse(es.getValue(19));
    }

    @Test
    public void getValueWhenThirdBarClosesBarelyBelowMidpoint() {
        series.barBuilder().openPrice(17).closePrice(25).highPrice(25).lowPrice(17).add();
        series.barBuilder().openPrice(26).closePrice(25.8).highPrice(27).lowPrice(25).add();
        series.barBuilder().openPrice(27).closePrice(20.99).highPrice(27).lowPrice(19).add();

        var es = new EveningStarIndicator(series);
        assertTrue(es.getValue(19));
    }

}
