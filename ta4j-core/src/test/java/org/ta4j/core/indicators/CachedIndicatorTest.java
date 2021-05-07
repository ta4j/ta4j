/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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
package org.ta4j.core.indicators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.Arrays;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Indicator;
import org.ta4j.core.Strategy;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;

public class CachedIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries series;

    public CachedIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        series = new MockBarSeries(numFunction, 1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2);
    }

    @Test
    public void ifCacheWorks() {
        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(series), 3);
        Num firstTime = sma.getValue(4);
        Num secondTime = sma.getValue(4);
        assertEquals(firstTime, secondTime);
    }

    @Test // should be not null
    public void getValueWithNullBarSeries() {

        ConstantIndicator<Num> constant = new ConstantIndicator<>(
                new BaseBarSeriesBuilder().withNumTypeOf(numFunction).build(), numFunction.apply(10));
        assertEquals(numFunction.apply(10), constant.getValue(0));
        assertEquals(numFunction.apply(10), constant.getValue(100));
        assertNotNull(constant.getBarSeries());

        SMAIndicator sma = new SMAIndicator(constant, 10);
        assertEquals(numFunction.apply(10), sma.getValue(0));
        assertEquals(numFunction.apply(10), sma.getValue(100));
        assertNotNull(sma.getBarSeries());
    }

    @Test
    public void getValueWithCacheLengthIncrease() {
        double[] data = new double[200];
        Arrays.fill(data, 10);
        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(new MockBarSeries(numFunction, data)), 100);
        assertNumEquals(10, sma.getValue(105));
    }

    @Test
    public void getValueWithOldResultsRemoval() {
        double[] data = new double[20];
        Arrays.fill(data, 1);
        BarSeries barSeries = new MockBarSeries(numFunction, data);
        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(barSeries), 10);
        assertNumEquals(1, sma.getValue(5));
        assertNumEquals(1, sma.getValue(10));
        barSeries.setMaximumBarCount(12);
        assertNumEquals(1, sma.getValue(19));
    }

    @Test
    public void strategyExecutionOnCachedIndicatorAndLimitedBarSeries() {
        BarSeries barSeries = new MockBarSeries(numFunction, 0, 1, 2, 3, 4, 5, 6, 7);
        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(barSeries), 2);
        // Theoretical values for SMA(2) cache: 0, 0.5, 1.5, 2.5, 3.5, 4.5, 5.5, 6.5
        barSeries.setMaximumBarCount(6);
        // Theoretical values for SMA(2) cache: null, null, 2, 2.5, 3.5, 4.5, 5.5, 6.5

        Strategy strategy = new BaseStrategy(new OverIndicatorRule(sma, sma.numOf(3)),
                new UnderIndicatorRule(sma, sma.numOf(3)));
        // Theoretical shouldEnter results: false, false, false, false, true, true,
        // true, true
        // Theoretical shouldExit results: false, false, true, true, false, false,
        // false, false

        // As we return the first bar/result found for the removed bars:
        // -> Approximated values for ClosePrice cache: 2, 2, 2, 3, 4, 5, 6, 7
        // -> Approximated values for SMA(2) cache: 2, 2, 2, 2.5, 3.5, 4.5, 5.5, 6.5

        // Then enters/exits are also approximated:
        // -> shouldEnter results: false, false, false, false, true, true, true, true
        // -> shouldExit results: true, true, true, true, false, false, false, false

        assertFalse(strategy.shouldEnter(0));
        assertTrue(strategy.shouldExit(0));
        assertFalse(strategy.shouldEnter(1));
        assertTrue(strategy.shouldExit(1));
        assertFalse(strategy.shouldEnter(2));
        assertTrue(strategy.shouldExit(2));
        assertFalse(strategy.shouldEnter(3));
        assertTrue(strategy.shouldExit(3));
        assertTrue(strategy.shouldEnter(4));
        assertFalse(strategy.shouldExit(4));
        assertTrue(strategy.shouldEnter(5));
        assertFalse(strategy.shouldExit(5));
        assertTrue(strategy.shouldEnter(6));
        assertFalse(strategy.shouldExit(6));
        assertTrue(strategy.shouldEnter(7));
        assertFalse(strategy.shouldExit(7));
    }

    @Test
    public void getValueOnResultsCalculatedFromRemovedBarsShouldReturnFirstRemainingResult() {
        BarSeries barSeries = new MockBarSeries(numFunction, 1, 1, 1, 1, 1);
        barSeries.setMaximumBarCount(3);
        assertEquals(2, barSeries.getRemovedBarsCount());

        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(barSeries), 2);
        for (int i = 0; i < 5; i++) {
            assertNumEquals(1, sma.getValue(i));
        }
    }

    @Test
    public void recursiveCachedIndicatorOnMovingBarSeriesShouldNotCauseStackOverflow() {
        // Added to check issue #120: https://github.com/mdeverdelhan/ta4j/issues/120
        // See also: CachedIndicator#getValue(int index)
        series = new MockBarSeries(numFunction);
        series.setMaximumBarCount(5);
        assertEquals(5, series.getBarCount());

        ZLEMAIndicator zlema = new ZLEMAIndicator(new ClosePriceIndicator(series), 1);
        try {
            assertNumEquals(4996, zlema.getValue(8));
        } catch (Throwable t) {
            fail(t.getMessage());
        }
    }

    @Test
    public void leaveLastBarUncached() {
        BarSeries barSeries = new MockBarSeries(numFunction);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        assertNumEquals(5000, closePrice.getValue(barSeries.getEndIndex()));
        barSeries.getLastBar().addTrade(numOf(10), numOf(5));
        assertNumEquals(5, closePrice.getValue(barSeries.getEndIndex()));
    }

}
