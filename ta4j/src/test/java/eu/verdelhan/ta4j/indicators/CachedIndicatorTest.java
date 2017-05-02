/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
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
package eu.verdelhan.ta4j.indicators;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.Strategy;
import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.indicators.simple.ConstantIndicator;
import eu.verdelhan.ta4j.indicators.trackers.SMAIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import eu.verdelhan.ta4j.trading.rules.OverIndicatorRule;
import eu.verdelhan.ta4j.trading.rules.UnderIndicatorRule;
import java.util.Arrays;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class CachedIndicatorTest {

    private TimeSeries series;

    @Before
    public void setUp() {
        series = new MockTimeSeries(1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2);
    }

    @Test
    public void ifCacheWorks() {
        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(series), 3);
        Decimal firstTime = sma.getValue(4);
        Decimal secondTime = sma.getValue(4);
        assertEquals(firstTime, secondTime);
    }

    @Test
    public void getValueWithNullTimeSeries() {
        
        ConstantIndicator<Decimal> constant = new ConstantIndicator<Decimal>(Decimal.TEN);
        assertEquals(Decimal.TEN, constant.getValue(0));
        assertEquals(Decimal.TEN, constant.getValue(100));
        assertNull(constant.getTimeSeries());

        SMAIndicator sma = new SMAIndicator(constant, 10);
        assertEquals(Decimal.TEN, sma.getValue(0));
        assertEquals(Decimal.TEN, sma.getValue(100));
        assertNull(sma.getTimeSeries());
    }

    @Test
    public void getValueWithCacheLengthIncrease() {
        double[] data = new double[200];
        Arrays.fill(data, 10);
        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(new MockTimeSeries(data)), 100);
        assertDecimalEquals(sma.getValue(105), 10);
    }

    @Test
    public void getValueWithOldResultsRemoval() {
        double[] data = new double[20];
        Arrays.fill(data, 1);
        TimeSeries timeSeries = new MockTimeSeries(data);
        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(timeSeries), 10);
        assertDecimalEquals(sma.getValue(5), 1);
        assertDecimalEquals(sma.getValue(10), 1);
        timeSeries.setMaximumTickCount(12);
        assertDecimalEquals(sma.getValue(19), 1);
    }

    @Test
    public void strategyExecutionOnCachedIndicatorAndLimitedTimeSeries() {
        TimeSeries timeSeries = new MockTimeSeries(0, 1, 2, 3, 4, 5, 6, 7);
        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(timeSeries), 2);
        // Theoretical values for SMA(2) cache: 0, 0.5, 1.5, 2.5, 3.5, 4.5, 5.5, 6.5
        timeSeries.setMaximumTickCount(6);
        // Theoretical values for SMA(2) cache: null, null, 2, 2.5, 3.5, 4.5, 5.5, 6.5
        
        Strategy strategy = new Strategy(
                new OverIndicatorRule(sma, Decimal.THREE),
                new UnderIndicatorRule(sma, Decimal.THREE)
        );
        // Theoretical shouldEnter results: false, false, false, false, true, true, true, true
        // Theoretical shouldExit results: false, false, true, true, false, false, false, false

        // As we return the first tick/result found for the removed ticks:
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
    public void getValueOnResultsCalculatedFromRemovedTicksShouldReturnFirstRemainingResult() {
        TimeSeries timeSeries = new MockTimeSeries(1, 1, 1, 1, 1);
        timeSeries.setMaximumTickCount(3);
        assertEquals(2, timeSeries.getRemovedTicksCount());
        
        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(timeSeries), 2);
        for (int i = 0; i < 5; i++) {
            assertDecimalEquals(sma.getValue(i), 1);
        }
    }
}
