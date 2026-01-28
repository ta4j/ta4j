/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
