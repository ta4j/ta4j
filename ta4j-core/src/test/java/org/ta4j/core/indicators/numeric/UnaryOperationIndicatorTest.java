/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.numeric;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.indicators.helpers.DifferenceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.assertj.core.api.Assertions.assertThat;

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
    public void testSqrtOnNegativeInput() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(-1, -4, -9, -16, -25).build();
        final var indicator = new ClosePriceIndicator(series);

        final var result = UnaryOperationIndicator.sqrt(indicator);

        assertTrue("sqrt(-1) should be NaN", result.getValue(0).isNaN());
        assertTrue("sqrt(-4) should be NaN", result.getValue(1).isNaN());
        assertTrue("sqrt(-9) should be NaN", result.getValue(2).isNaN());
        assertTrue("sqrt(-16) should be NaN", result.getValue(3).isNaN());
        assertTrue("sqrt(-25) should be NaN", result.getValue(4).isNaN());
    }

    @Test
    public void testLogOnZeroAndNegativeInput() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(0, -1, -5, -10).build();
        final var indicator = new ClosePriceIndicator(series);

        final var result = UnaryOperationIndicator.log(indicator);

        // log(0) mathematically should be -Infinity, but implementation may return NaN
        final Num logZero = result.getValue(0);
        if (logZero.isNaN()) {
            // Implementation returns NaN for log(0)
            assertTrue("log(0) should be NaN or -Infinity", logZero.isNaN());
        } else {
            // If implementation returns -Infinity, verify it
            assertTrue("log(0) should be -Infinity",
                    Double.isInfinite(logZero.doubleValue()) && logZero.doubleValue() == Double.NEGATIVE_INFINITY);
        }

        // log(negative) should always be NaN
        assertTrue("log(-1) should be NaN", result.getValue(1).isNaN());
        assertTrue("log(-5) should be NaN", result.getValue(2).isNaN());
        assertTrue("log(-10) should be NaN", result.getValue(3).isNaN());
    }

    @Test
    public void testPowWithNegativeBaseAndFractionalExponent() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(-4, -9, -16, -25).build();
        final var indicator = new ClosePriceIndicator(series);

        // Test with fractional exponents (non-integer)
        // pow(negative, fractional) should be NaN for non-integer exponents
        // Note: DecimalNum implementation throws NumberFormatException when Math.pow
        // returns NaN/Infinity
        // DoubleNum should return NaN
        final var resultHalf = UnaryOperationIndicator.pow(indicator, 0.5);
        final var resultThird = UnaryOperationIndicator.pow(indicator, 1.0 / 3.0);
        final var resultTwoThirds = UnaryOperationIndicator.pow(indicator, 2.0 / 3.0);

        for (int i = 0; i < series.getBarCount(); i++) {
            try {
                final Num halfResult = resultHalf.getValue(i);
                final Num thirdResult = resultThird.getValue(i);
                final Num twoThirdsResult = resultTwoThirds.getValue(i);

                // For DoubleNum, Math.pow(-4, 0.5) returns Double.NaN, which is wrapped in
                // DoubleNum
                // DoubleNum doesn't override isNaN(), so we check the underlying double value
                // For DecimalNum, this should throw NumberFormatException (caught below)
                assertTrue("pow(" + series.getBar(i).getClosePrice() + ", 0.5) should be NaN, but got: " + halfResult,
                        Num.isNaNOrNull(halfResult));
                assertTrue("pow(" + series.getBar(i).getClosePrice() + ", 1/3) should be NaN, but got: " + thirdResult,
                        Num.isNaNOrNull(thirdResult));
                assertTrue(
                        "pow(" + series.getBar(i).getClosePrice() + ", 2/3) should be NaN, but got: " + twoThirdsResult,
                        Num.isNaNOrNull(twoThirdsResult));
            } catch (NumberFormatException e) {
                // DecimalNum throws NumberFormatException when Math.pow returns NaN/Infinity
                // This is also an edge case behavior that should be documented
                // The exception message may vary depending on the BigDecimal implementation,
                // so we only verify that an exception is thrown, not the specific message
                // Expected for DecimalNum when pow(negative, fractional) results in NaN
            }
        }
    }

    @Test
    public void testSubstitute_ReplacesNaNWithCorrectValue() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        Indicator<Num> indicator = new FixedIndicator<>(series, numOf(1), NaN.NaN, numOf(3), NaN.NaN, numOf(5));

        Num valueToReplace = NaN.NaN;
        Num replacementValue = numOf(0);

        UnaryOperationIndicator subject = UnaryOperationIndicator.substitute(indicator, valueToReplace,
                replacementValue);

        assertNumEquals(numFactory.one(), subject.getValue(0));
        assertNumEquals(numFactory.zero(), subject.getValue(1));
        assertNumEquals(numOf(3), subject.getValue(2));
        assertNumEquals(numOf(0), subject.getValue(3));
        assertNumEquals(numOf(5), subject.getValue(4));
    }

    @Test
    public void testGetCountOfUnstableBars() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        final var indicator = new MockIndicator(series, 1, numOf(1));

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

    @Test
    @SuppressWarnings("unchecked")
    public void serializationRoundTrip() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, -2, 3, -4, 5, -6).build();
        Indicator<Num> base = new ClosePriceIndicator(series);
        Indicator<Num> priceChange = new DifferenceIndicator(base);
        UnaryOperationIndicator original = UnaryOperationIndicator.abs(priceChange);

        String json = original.toJson();
        Indicator<Num> reconstructed = (Indicator<Num>) Indicator.fromJson(series, json);

        assertThat(reconstructed).isInstanceOf(UnaryOperationIndicator.class);
        assertEquals(original.toDescriptor(), reconstructed.toDescriptor());

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertNumEquals(original.getValue(i), reconstructed.getValue(i));
        }
    }
}
