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
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class BinaryOperationTest extends AbstractIndicatorTest<BinaryOperation, Num> {

    public BinaryOperationTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void testSumIndicatorToIndicator() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        var left = new ClosePriceIndicator(series);
        var right = new ClosePriceIndicator(series);

        var result = BinaryOperation.sum(left, right);

        assertNumEquals(2, result.getValue(0));
        assertNumEquals(4, result.getValue(1));
        assertNumEquals(6, result.getValue(2));
        assertNumEquals(8, result.getValue(3));
        assertNumEquals(10, result.getValue(4));
    }

    @Test
    public void testSumIndicatorToNumber() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        var indicator = new ClosePriceIndicator(series);

        var result = BinaryOperation.sum(indicator, 10);

        assertNumEquals(11, result.getValue(0));
        assertNumEquals(12, result.getValue(1));
        assertNumEquals(13, result.getValue(2));
        assertNumEquals(14, result.getValue(3));
        assertNumEquals(15, result.getValue(4));
    }

    @Test
    public void testDifferenceIndicatorToIndicator() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(5, 6, 7, 8, 9).build();
        var left = new ClosePriceIndicator(series);
        var series2 = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        var right = new ClosePriceIndicator(series2);

        var result = BinaryOperation.difference(left, right);

        assertNumEquals(4, result.getValue(0));
        assertNumEquals(4, result.getValue(1));
        assertNumEquals(4, result.getValue(2));
        assertNumEquals(4, result.getValue(3));
        assertNumEquals(4, result.getValue(4));
    }

    @Test
    public void testDifferenceIndicatorToNumber() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 15, 20, 25, 30).build();
        var indicator = new ClosePriceIndicator(series);

        var result = BinaryOperation.difference(indicator, 5);

        assertNumEquals(5, result.getValue(0));
        assertNumEquals(10, result.getValue(1));
        assertNumEquals(15, result.getValue(2));
        assertNumEquals(20, result.getValue(3));
        assertNumEquals(25, result.getValue(4));
    }

    @Test
    public void testProductIndicatorToIndicator() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(2, 3, 4, 5, 6).build();
        var left = new ClosePriceIndicator(series);
        var series2 = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(3, 2, 1, 2, 3).build();
        var right = new ClosePriceIndicator(series2);

        var result = BinaryOperation.product(left, right);

        assertNumEquals(6, result.getValue(0));
        assertNumEquals(6, result.getValue(1));
        assertNumEquals(4, result.getValue(2));
        assertNumEquals(10, result.getValue(3));
        assertNumEquals(18, result.getValue(4));
    }

    @Test
    public void testProductIndicatorToNumber() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        var indicator = new ClosePriceIndicator(series);

        var result = BinaryOperation.product(indicator, 2.5);

        assertNumEquals(2.5, result.getValue(0));
        assertNumEquals(5.0, result.getValue(1));
        assertNumEquals(7.5, result.getValue(2));
        assertNumEquals(10.0, result.getValue(3));
        assertNumEquals(12.5, result.getValue(4));
    }

    @Test
    public void testQuotientIndicatorToIndicator() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 15, 20, 25, 30).build();
        var left = new ClosePriceIndicator(series);
        var series2 = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(2, 3, 4, 5, 6).build();
        var right = new ClosePriceIndicator(series2);

        var result = BinaryOperation.quotient(left, right);

        assertNumEquals(5, result.getValue(0));
        assertNumEquals(5, result.getValue(1));
        assertNumEquals(5, result.getValue(2));
        assertNumEquals(5, result.getValue(3));
        assertNumEquals(5, result.getValue(4));
    }

    @Test
    public void testQuotientIndicatorToNumber() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 15, 20, 25, 30).build();
        var indicator = new ClosePriceIndicator(series);

        var result = BinaryOperation.quotient(indicator, 5);

        assertNumEquals(2, result.getValue(0));
        assertNumEquals(3, result.getValue(1));
        assertNumEquals(4, result.getValue(2));
        assertNumEquals(5, result.getValue(3));
        assertNumEquals(6, result.getValue(4));
    }

    @Test
    public void testMaxIndicatorToIndicator() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 5, 3, 8, 2).build();
        var left = new ClosePriceIndicator(series);
        var series2 = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(3, 2, 6, 4, 7).build();
        var right = new ClosePriceIndicator(series2);

        var result = BinaryOperation.max(left, right);

        assertNumEquals(3, result.getValue(0));
        assertNumEquals(5, result.getValue(1));
        assertNumEquals(6, result.getValue(2));
        assertNumEquals(8, result.getValue(3));
        assertNumEquals(7, result.getValue(4));
    }

    @Test
    public void testMaxIndicatorToNumber() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        var indicator = new ClosePriceIndicator(series);

        var result = BinaryOperation.max(indicator, 3);

        assertNumEquals(3, result.getValue(0));
        assertNumEquals(3, result.getValue(1));
        assertNumEquals(3, result.getValue(2));
        assertNumEquals(4, result.getValue(3));
        assertNumEquals(5, result.getValue(4));
    }

    @Test
    public void testMinIndicatorToIndicator() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 5, 3, 8, 2).build();
        var left = new ClosePriceIndicator(series);
        var series2 = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(3, 2, 6, 4, 7).build();
        var right = new ClosePriceIndicator(series2);

        var result = BinaryOperation.min(left, right);

        assertNumEquals(1, result.getValue(0));
        assertNumEquals(2, result.getValue(1));
        assertNumEquals(3, result.getValue(2));
        assertNumEquals(4, result.getValue(3));
        assertNumEquals(2, result.getValue(4));
    }

    @Test
    public void testMinIndicatorToNumber() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        var indicator = new ClosePriceIndicator(series);

        var result = BinaryOperation.min(indicator, 3);

        assertNumEquals(1, result.getValue(0));
        assertNumEquals(2, result.getValue(1));
        assertNumEquals(3, result.getValue(2));
        assertNumEquals(3, result.getValue(3));
        assertNumEquals(3, result.getValue(4));
    }

    @Test
    public void testGetCountOfUnstableBars() {
        var series1 = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        var series2 = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        var left = new ClosePriceIndicator(series1);
        var right = new ClosePriceIndicator(series2);

        var result = BinaryOperation.sum(left, right);

        assertEquals(0, result.getCountOfUnstableBars());
    }

    @Test
    public void testGetBarSeries() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        var left = new ClosePriceIndicator(series);
        var right = new ClosePriceIndicator(series);

        var result = BinaryOperation.sum(left, right);

        assertEquals(series, result.getBarSeries());
    }
}