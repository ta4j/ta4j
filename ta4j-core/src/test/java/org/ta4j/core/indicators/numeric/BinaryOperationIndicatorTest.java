/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.numeric;

import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class BinaryOperationIndicatorTest extends AbstractIndicatorTest<BinaryOperationIndicator, Num> {

    public BinaryOperationIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void testSumIndicatorToIndicator() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        var left = new ClosePriceIndicator(series);
        var right = new ClosePriceIndicator(series);

        var result = BinaryOperationIndicator.sum(left, right);

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

        var result = BinaryOperationIndicator.sum(indicator, 10);

        assertNumEquals(11, result.getValue(0));
        assertNumEquals(12, result.getValue(1));
        assertNumEquals(13, result.getValue(2));
        assertNumEquals(14, result.getValue(3));
        assertNumEquals(15, result.getValue(4));
    }

    @Test
    public void testDifferenceIndicatorToIndicator() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        List<Num> leftValues = Arrays.asList(5, 6, 7, 8, 9)
                .stream()
                .map(numFactory::numOf)
                .collect(Collectors.toList());
        List<Num> rightValues = Arrays.asList(1, 2, 3, 4, 5)
                .stream()
                .map(numFactory::numOf)
                .collect(Collectors.toList());
        var left = new MockIndicator(series, leftValues);
        var right = new MockIndicator(series, rightValues);

        var result = BinaryOperationIndicator.difference(left, right);

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

        var result = BinaryOperationIndicator.difference(indicator, 5);

        assertNumEquals(5, result.getValue(0));
        assertNumEquals(10, result.getValue(1));
        assertNumEquals(15, result.getValue(2));
        assertNumEquals(20, result.getValue(3));
        assertNumEquals(25, result.getValue(4));
    }

    @Test
    public void testProductIndicatorToIndicator() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        List<Num> leftValues = Arrays.asList(2, 3, 4, 5, 6)
                .stream()
                .map(numFactory::numOf)
                .collect(Collectors.toList());
        List<Num> rightValues = Arrays.asList(3, 2, 1, 2, 3)
                .stream()
                .map(numFactory::numOf)
                .collect(Collectors.toList());
        var left = new MockIndicator(series, leftValues);
        var right = new MockIndicator(series, rightValues);

        var result = BinaryOperationIndicator.product(left, right);

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

        var result = BinaryOperationIndicator.product(indicator, 2.5);

        assertNumEquals(2.5, result.getValue(0));
        assertNumEquals(5.0, result.getValue(1));
        assertNumEquals(7.5, result.getValue(2));
        assertNumEquals(10.0, result.getValue(3));
        assertNumEquals(12.5, result.getValue(4));
    }

    @Test
    public void testQuotientIndicatorToIndicator() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        List<Num> leftValues = Arrays.asList(10, 15, 20, 25, 30)
                .stream()
                .map(numFactory::numOf)
                .collect(Collectors.toList());
        List<Num> rightValues = Arrays.asList(2, 3, 4, 5, 6)
                .stream()
                .map(numFactory::numOf)
                .collect(Collectors.toList());
        var left = new MockIndicator(series, leftValues);
        var right = new MockIndicator(series, rightValues);

        var result = BinaryOperationIndicator.quotient(left, right);

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

        var result = BinaryOperationIndicator.quotient(indicator, 5);

        assertNumEquals(2, result.getValue(0));
        assertNumEquals(3, result.getValue(1));
        assertNumEquals(4, result.getValue(2));
        assertNumEquals(5, result.getValue(3));
        assertNumEquals(6, result.getValue(4));
    }

    @Test
    public void testQuotientDivisionByZeroIndicator() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 20, 30, 40, 50).build();
        List<Num> leftValues = Arrays.asList(10, 20, 30, 40, 50)
                .stream()
                .map(numFactory::numOf)
                .collect(Collectors.toList());
        List<Num> rightValues = Arrays.asList(2, 0, 5, 0, 10)
                .stream()
                .map(numFactory::numOf)
                .collect(Collectors.toList());
        var left = new MockIndicator(series, leftValues);
        var right = new MockIndicator(series, rightValues);

        var result = BinaryOperationIndicator.quotient(left, right);

        // Index 0: 10 / 2 = 5 (normal division)
        assertNumEquals(5, result.getValue(0));
        // Index 1: 20 / 0 = NaN (division by zero)
        assertTrue("Expected NaN at index 1 (division by zero)", result.getValue(1).isNaN());
        // Index 2: 30 / 5 = 6 (normal division)
        assertNumEquals(6, result.getValue(2));
        // Index 3: 40 / 0 = NaN (division by zero)
        assertTrue("Expected NaN at index 3 (division by zero)", result.getValue(3).isNaN());
        // Index 4: 50 / 10 = 5 (normal division)
        assertNumEquals(5, result.getValue(4));
    }

    @Test
    public void testQuotientDivisionByZeroNumber() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 20, 30, 40, 50).build();
        var indicator = new ClosePriceIndicator(series);

        var result = BinaryOperationIndicator.quotient(indicator, 0);

        // All indexes should be NaN when dividing by zero
        assertTrue("Expected NaN at index 0 (division by zero)", result.getValue(0).isNaN());
        assertTrue("Expected NaN at index 1 (division by zero)", result.getValue(1).isNaN());
        assertTrue("Expected NaN at index 2 (division by zero)", result.getValue(2).isNaN());
        assertTrue("Expected NaN at index 3 (division by zero)", result.getValue(3).isNaN());
        assertTrue("Expected NaN at index 4 (division by zero)", result.getValue(4).isNaN());
    }

    @Test
    public void testMaxIndicatorToIndicator() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        List<Num> leftValues = Arrays.asList(1, 5, 3, 8, 2)
                .stream()
                .map(numFactory::numOf)
                .collect(Collectors.toList());
        List<Num> rightValues = Arrays.asList(3, 2, 6, 4, 7)
                .stream()
                .map(numFactory::numOf)
                .collect(Collectors.toList());
        var left = new MockIndicator(series, leftValues);
        var right = new MockIndicator(series, rightValues);

        var result = BinaryOperationIndicator.max(left, right);

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

        var result = BinaryOperationIndicator.max(indicator, 3);

        assertNumEquals(3, result.getValue(0));
        assertNumEquals(3, result.getValue(1));
        assertNumEquals(3, result.getValue(2));
        assertNumEquals(4, result.getValue(3));
        assertNumEquals(5, result.getValue(4));
    }

    @Test
    public void testSumWithDifferingUnstableBars() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        List<Num> leftValues = Arrays.asList(1, 2, 3, 4, 5)
                .stream()
                .map(numFactory::numOf)
                .collect(Collectors.toList());
        List<Num> rightValues = Arrays.asList(2, 3, 4, 5, 6)
                .stream()
                .map(numFactory::numOf)
                .collect(Collectors.toList());
        var left = new MockIndicator(series, 2, leftValues);
        var right = new MockIndicator(series, 5, rightValues);

        var result = BinaryOperationIndicator.sum(left, right);

        assertEquals(Math.max(left.getCountOfUnstableBars(), right.getCountOfUnstableBars()),
                result.getCountOfUnstableBars());
    }

    @Test
    public void testMinIndicatorToIndicator() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        List<Num> leftValues = Arrays.asList(1, 5, 3, 8, 2)
                .stream()
                .map(numFactory::numOf)
                .collect(Collectors.toList());
        List<Num> rightValues = Arrays.asList(3, 2, 6, 4, 7)
                .stream()
                .map(numFactory::numOf)
                .collect(Collectors.toList());
        var left = new MockIndicator(series, leftValues);
        var right = new MockIndicator(series, rightValues);

        var result = BinaryOperationIndicator.min(left, right);

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

        var result = BinaryOperationIndicator.min(indicator, 3);

        assertNumEquals(1, result.getValue(0));
        assertNumEquals(2, result.getValue(1));
        assertNumEquals(3, result.getValue(2));
        assertNumEquals(3, result.getValue(3));
        assertNumEquals(3, result.getValue(4));
    }

    @Test
    public void testGetCountOfUnstableBars() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        var left = new ClosePriceIndicator(series);
        var right = new ClosePriceIndicator(series);

        var result = BinaryOperationIndicator.sum(left, right);

        assertEquals(0, result.getCountOfUnstableBars());
    }

    @Test
    public void testGetBarSeries() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        var left = new ClosePriceIndicator(series);
        var right = new ClosePriceIndicator(series);

        var result = BinaryOperationIndicator.sum(left, right);

        assertEquals(series, result.getBarSeries());
    }

    @Test
    public void testNullLeftIndicatorThrowsException() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        var right = new ClosePriceIndicator(series);

        assertThrows(IllegalArgumentException.class, () -> {
            BinaryOperationIndicator.sum((Indicator<Num>) null, right);
        });
    }

    @Test
    public void testNullRightIndicatorThrowsException() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        var left = new ClosePriceIndicator(series);

        assertThrows(IllegalArgumentException.class, () -> {
            BinaryOperationIndicator.sum(left, (Indicator<Num>) null);
        });
    }

    @Test
    public void testDifferentBarSeriesThrowsException() {
        var series1 = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        var series2 = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(4, 5, 6).build();
        var left = new ClosePriceIndicator(series1);
        var right = new ClosePriceIndicator(series2);

        assertThrows(IllegalArgumentException.class, () -> {
            BinaryOperationIndicator.sum(left, right);
        });
    }
}