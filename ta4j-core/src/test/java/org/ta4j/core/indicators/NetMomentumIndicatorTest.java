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
package org.ta4j.core.indicators;

import org.junit.Before;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import static org.junit.jupiter.api.Assertions.*;

public class NetMomentumIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries series;
    private ClosePriceIndicator closePrice;

    public NetMomentumIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        this.series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        // Create a series with values that will produce known RSI values
        series.barBuilder().closePrice(44.34).add();
        series.barBuilder().closePrice(44.09).add();
        series.barBuilder().closePrice(44.15).add();
        series.barBuilder().closePrice(43.61).add();
        series.barBuilder().closePrice(44.33).add();
        series.barBuilder().closePrice(44.83).add();
        series.barBuilder().closePrice(45.10).add();
        series.barBuilder().closePrice(45.42).add();
        series.barBuilder().closePrice(45.84).add();
        series.barBuilder().closePrice(46.08).add();
        series.barBuilder().closePrice(45.89).add();
        series.barBuilder().closePrice(46.03).add();
        series.barBuilder().closePrice(45.61).add();
        series.barBuilder().closePrice(46.28).add();
        series.barBuilder().closePrice(46.28).add();
        series.barBuilder().closePrice(46.00).add();
        series.barBuilder().closePrice(46.03).add();
        series.barBuilder().closePrice(46.41).add();
        series.barBuilder().closePrice(46.22).add();
        series.barBuilder().closePrice(45.64).add();

        closePrice = new ClosePriceIndicator(series);
    }

    @Test
    public void testWithRSIIndicator() {
        RSIIndicator rsi = new RSIIndicator(closePrice, 14);
        NetMomentumIndicator boe = new NetMomentumIndicator(rsi, 5);

        assertNotNull(boe.getValue(19));

        // Verify that the indicator produces different values over time
        Num value15 = boe.getValue(15);
        Num value19 = boe.getValue(19);
        assertFalse(value15.equals(value19));
    }

    @Test
    public void testGeneralConstructor() {
        // Create a simple oscillating indicator for testing
        CachedIndicator<Num> oscillator = new CachedIndicator<Num>(closePrice) {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            protected Num calculate(int index) {
                // Oscillates between 0 and 100
                return numOf(50 + 30 * Math.sin(index * 0.5));
            }
        };

        NetMomentumIndicator boe = new NetMomentumIndicator(oscillator, 10, 50);

        assertNotNull(boe.getValue(10));
    }

    @Test
    public void testPositiveAndNegativeBalance() {
        // Create an indicator that alternates above and below neutral
        CachedIndicator<Num> alternatingIndicator = new CachedIndicator<Num>(closePrice) {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            protected Num calculate(int index) {
                return numOf(index % 2 == 0 ? 60 : 40); // Alternates above/below 50
            }
        };

        NetMomentumIndicator boe = new NetMomentumIndicator(alternatingIndicator, 3, 50);

        // At index 2: [60, 40, 60] - after smoothing and differencing, should have
        // mixed balance
        Num value = boe.getValue(2);
        assertNotNull(value);
    }

    @Test
    public void testGetCountOfUnstableBars() {
        RSIIndicator rsi = new RSIIndicator(closePrice, 14);
        NetMomentumIndicator boe = new NetMomentumIndicator(rsi, 5);

        int unstableBars = boe.getCountOfUnstableBars();
        assertTrue(unstableBars >= 5); // At least the timeframe
        assertTrue(unstableBars >= rsi.getCountOfUnstableBars()); // At least the RSI unstable bars
    }

    @Test
    public void testCachedValues() {
        RSIIndicator rsi = new RSIIndicator(closePrice, 14);
        NetMomentumIndicator boe = new NetMomentumIndicator(rsi, 5);

        // Get value twice - should be cached
        Num firstCall = boe.getValue(15);
        Num secondCall = boe.getValue(15);

        assertSame(firstCall, secondCall); // Should be the same object due to caching
    }

    @Test
    public void testTrendDetection() {
        // Create a trending indicator (consistently above neutral)
        CachedIndicator<Num> trendingUp = new CachedIndicator<Num>(closePrice) {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            protected Num calculate(int index) {
                return numOf(70); // Consistently above 50
            }
        };

        NetMomentumIndicator boe = new NetMomentumIndicator(trendingUp, 5, 50);

        // After several bars, balance should be positive
        Num balance = boe.getValue(10);
        assertTrue(balance.isPositive());
    }

    @Test
    public void testWithDifferentNeutralPivots() {
        RSIIndicator rsi = new RSIIndicator(closePrice, 14);

        NetMomentumIndicator boe30 = new NetMomentumIndicator(rsi, 5, 30);
        NetMomentumIndicator boe50 = new NetMomentumIndicator(rsi, 5, 50);
        NetMomentumIndicator boe70 = new NetMomentumIndicator(rsi, 5, 70);

        // Different pivot values should produce different results
        Num value30 = boe30.getValue(19);
        Num value50 = boe50.getValue(19);
        Num value70 = boe70.getValue(19);

        assertNotEquals(value30, value50);
        assertNotEquals(value50, value70);
    }
}