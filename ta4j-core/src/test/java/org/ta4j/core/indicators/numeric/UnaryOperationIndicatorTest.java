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
package org.ta4j.core.indicators.numeric;

import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class UnaryOperationIndicatorTest extends AbstractIndicatorTest<UnaryOperationIndicator, Num> {

    public UnaryOperationIndicatorTest(final NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void testSqrt() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 4, 9, 16, 25).build();
        final var indicator = new ClosePriceIndicator(series);

        final var result = UnaryOperationIndicator.sqrt(indicator);

        assertNumEquals(1, result.getValue(0));
        assertNumEquals(2, result.getValue(1));
        assertNumEquals(3, result.getValue(2));
        assertNumEquals(4, result.getValue(3));
        assertNumEquals(5, result.getValue(4));
    }

    @Test
    public void testAbs() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(-5, -2, 0, 3, -7).build();
        final var indicator = new ClosePriceIndicator(series);

        final var result = UnaryOperationIndicator.abs(indicator);

        assertNumEquals(5, result.getValue(0));
        assertNumEquals(2, result.getValue(1));
        assertNumEquals(0, result.getValue(2));
        assertNumEquals(3, result.getValue(3));
        assertNumEquals(7, result.getValue(4));
    }

    @Test
    public void testPow() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(2, 3, 4, 5, 6).build();
        final var indicator = new ClosePriceIndicator(series);

        final var result = UnaryOperationIndicator.pow(indicator, 2);

        assertNumEquals(4, result.getValue(0));
        assertNumEquals(9, result.getValue(1));
        assertNumEquals(16, result.getValue(2));
        assertNumEquals(25, result.getValue(3));
        assertNumEquals(36, result.getValue(4));
    }

    @Test
    public void testPowWithDecimal() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(4, 9, 16, 25, 36).build();
        final var indicator = new ClosePriceIndicator(series);

        final var result = UnaryOperationIndicator.pow(indicator, 0.5);

        assertNumEquals(2, result.getValue(0));
        assertNumEquals(3, result.getValue(1));
        assertNumEquals(4, result.getValue(2));
        assertNumEquals(5, result.getValue(3));
        assertNumEquals(6, result.getValue(4));
    }

    @Test
    public void testPowWithZero() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(2, 3, 4, 5, 6).build();
        final var indicator = new ClosePriceIndicator(series);

        final var result = UnaryOperationIndicator.pow(indicator, 0);

        assertNumEquals(1, result.getValue(0));
        assertNumEquals(1, result.getValue(1));
        assertNumEquals(1, result.getValue(2));
        assertNumEquals(1, result.getValue(3));
        assertNumEquals(1, result.getValue(4));
    }

    @Test
    public void testPowWithOne() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(2, 3, 4, 5, 6).build();
        final var indicator = new ClosePriceIndicator(series);

        final var result = UnaryOperationIndicator.pow(indicator, 1);

        assertNumEquals(2, result.getValue(0));
        assertNumEquals(3, result.getValue(1));
        assertNumEquals(4, result.getValue(2));
        assertNumEquals(5, result.getValue(3));
        assertNumEquals(6, result.getValue(4));
    }

    @Test
    public void testLog() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, Math.E, 10, 100, Math.E * Math.E)
                .build();
        final var indicator = new ClosePriceIndicator(series);

        final var result = UnaryOperationIndicator.log(indicator);

        assertNumEquals(0, result.getValue(0));
        assertNumEquals(1, result.getValue(1));
        assertNumEquals(Math.log(10), result.getValue(2));
        assertNumEquals(Math.log(100), result.getValue(3));
        assertNumEquals(2, result.getValue(4));
    }

    @Test
    public void testLogWithSmallValue() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(0.1, 0.01, 0.001).build();
        final var indicator = new ClosePriceIndicator(series);

        final var result = UnaryOperationIndicator.log(indicator);

        assertNumEquals(Math.log(0.1), result.getValue(0));
        assertNumEquals(Math.log(0.01), result.getValue(1));
        assertNumEquals(Math.log(0.001), result.getValue(2));
    }

    @Test
    public void testSubstitute_ReplacesNaNWithCorrectValue() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        Indicator<Num> indicator = new FixedIndicator<>(series, numOf(1), NaN.NaN, numOf(3), NaN.NaN, numOf(5));

        Num valueToReplace = NaN.NaN;
        Num replacementValue = numOf(0);

        UnaryOperationIndicator subject = UnaryOperationIndicator.substitute(indicator, valueToReplace, replacementValue);

        assertNumEquals(numFactory.one(), subject.getValue(0));
        assertNumEquals(numFactory.zero(), subject.getValue(1));
        assertNumEquals(numOf(3), subject.getValue(2));
        assertNumEquals(numOf(0), subject.getValue(3));
        assertNumEquals(numOf(5), subject.getValue(4));
    }

    @Test
    public void testGetCountOfUnstableBars() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        final var indicator = new ClosePriceIndicator(series);

        final var result = UnaryOperationIndicator.sqrt(indicator);

        assertEquals(0, result.getCountOfUnstableBars());
    }

    @Test
    public void testGetBarSeries() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        final var indicator = new ClosePriceIndicator(series);

        final var result = UnaryOperationIndicator.sqrt(indicator);

        assertEquals(series, result.getBarSeries());
    }
}
