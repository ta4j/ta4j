/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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
package org.ta4j.core.indicators.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Random;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.MockRule;
import org.ta4j.core.MockStrategy;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.indicators.candles.price.OpenPriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

public class PreviousValueIndicatorTest {

    private BacktestBarSeries series;

    @Before
    public void setUp() {
        final var r = new Random();
        this.series = new MockBarSeriesBuilder().withName("test").build();
        for (int i = 0; i < 1000; i++) {
            final double open = r.nextDouble();
            final double close = r.nextDouble();
            final double max = Math.max(close + r.nextDouble(), open + r.nextDouble());
            final double min = Math.min(0, Math.min(close - r.nextDouble(), open - r.nextDouble()));
            final Instant dateTime = ZonedDateTime.now().minusSeconds(1001 - i).toInstant();
            this.series.barBuilder()
                    .endTime(dateTime)
                    .openPrice(open)
                    .closePrice(close)
                    .highPrice(max)
                    .lowPrice(min)
                    .volume(i)
                    .add();
        }
    }

    @Test
    public void testIsStable1() {
        final var prevValueIndicator = new PreviousValueIndicator(new OpenPriceIndicator(this.series));
        this.series.replaceStrategy(new MockStrategy(new MockRule(List.of(prevValueIndicator))));

        assertFalse(prevValueIndicator.isStable());
        this.series.advance();
        assertTrue(prevValueIndicator.isStable());
        this.series.advance();
        assertTrue(prevValueIndicator.isStable());
    }

    @Test
    public void testIsStable2() {
        final var prevValueIndicator = new PreviousValueIndicator(new OpenPriceIndicator(this.series), 2);
        this.series.replaceStrategy(new MockStrategy(new MockRule(List.of(prevValueIndicator))));

        assertFalse(prevValueIndicator.isStable());
        this.series.advance();
        assertFalse(prevValueIndicator.isStable());
        this.series.advance();
        assertTrue(prevValueIndicator.isStable());
    }

    @Test
    public void testIsStable3() {
        final var prevValueIndicator = new PreviousValueIndicator(new OpenPriceIndicator(this.series), 3);
        this.series.replaceStrategy(new MockStrategy(new MockRule(List.of(prevValueIndicator))));

        assertFalse(prevValueIndicator.isStable());
        this.series.advance();
        assertFalse(prevValueIndicator.isStable());
        this.series.advance();
        assertFalse(prevValueIndicator.isStable());
        this.series.advance();
        assertTrue(prevValueIndicator.isStable());
    }

    @Test
    public void shouldBePreviousValueFromIndicator() {

        // test 1 with openPrice-indicator
        final var openPriceIndicator = new OpenPriceIndicator(this.series);
        final var prevValueIndicator = new PreviousValueIndicator(openPriceIndicator);
        this.series.replaceStrategy(new MockStrategy(new MockRule(List.of(prevValueIndicator))));

        this.series.advance();
        for (int i = 0; i < this.series.getBarCount() / 2 - 1; i++) {
            final var indicatorValue = openPriceIndicator.getValue();

            this.series.advance();
            assertEquals(indicatorValue, prevValueIndicator.getValue());
            final var indicatorValue2 = openPriceIndicator.getValue();

            this.series.advance();
            assertEquals(indicatorValue2, prevValueIndicator.getValue());
        }
    }

    @Test
    public void testToStringMethodWithN1() {
        final var openPriceIndicator = new OpenPriceIndicator(this.series);
        final var prevValueIndicator = new PreviousValueIndicator(openPriceIndicator);

        final String prevValueIndicatorAsString = prevValueIndicator.toString();

        assertTrue(prevValueIndicatorAsString.startsWith("PreviousValueIndicator["));
        assertTrue(prevValueIndicatorAsString.endsWith("]"));
    }

    @Test
    public void testToStringMethodWithN2() {
        final var openPriceIndicator = new OpenPriceIndicator(this.series);
        final var prevValueIndicator = new PreviousValueIndicator(openPriceIndicator);

        final String prevValueIndicatorAsString = prevValueIndicator.toString();

        assertTrue(prevValueIndicatorAsString.startsWith("PreviousValueIndicator["));
        assertTrue(prevValueIndicatorAsString.endsWith("]"));
    }

    @Test
    public void testToStringMethodWithNGreaterThen1() {
        final var openPriceIndicator = new OpenPriceIndicator(this.series);
        final var prevValueIndicator = new PreviousValueIndicator(openPriceIndicator, 2);

        final String prevValueIndicatorAsString = prevValueIndicator.toString();

        assertTrue(prevValueIndicatorAsString.startsWith("PreviousValueIndicator(2)["));
        assertTrue(prevValueIndicatorAsString.endsWith("]"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPreviousValueIndicatorWithNonPositiveN() {
        final var openPriceIndicator = new OpenPriceIndicator(this.series);
        new PreviousValueIndicator(openPriceIndicator, 0);
    }
}
