/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan, Ta4j Organization & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core;

import java.math.BigDecimal;

import org.ta4j.core.num.Num;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Utility class for {@code Num} tests.
 */
public class TestUtils {

    /** Offset for double equality checking */
    public static final double GENERAL_OFFSET = 0.0001;

    /**
     * Verifies that the actual {@code Num} value is exactly equal to the expected {@code String} representation.
     *
     * @param expected the given {@code String} representation to compare the actual value to
     * @param actual the actual {@code Num} value
     * @throws AssertionError if the actual {@code Num} value is not exactly equal to the given {@code String} representation
     */
    public static void assertNumEquals(String expected, Num actual) {
        assertEquals(actual.numOf(new BigDecimal(expected)), actual);
    }

    /**
     * Verifies that the actual {@code Num} value is not exactly equal to the expected {@code String} representation.
     *
     * @param expected the given {@code String} representation to compare the actual value to
     * @param actual the actual {@code Num} value
     * @throws AssertionError if the actual {@code Num} value is exactly equal to the given {@code String} representation
     */
    public static void assertNumNotEquals(String expected, Num actual) {
        assertNotEquals(actual.numOf(new BigDecimal(expected)), actual);
    }

    /**
     * Verifies that the actual {@code Num} value is exactly equal to the expected {@code Num}.
     *
     * @param expected the given {@code Num} to compare the actual value to
     * @param actual the actual {@code Num} value
     * @throws AssertionError if the actual {@code Num} value is not exactly equal to the given {@code Num}
     */
    public static void assertNumEquals(Num expected, Num actual){
        assertEquals(expected, actual);
    }

    /**
     * Verifies that the actual {@code Num} value is not exactly equal to the expected {@code Num}.
     *
     * @param expected the given {@code Num} to compare the actual value to
     * @param actual the actual {@code Num} value
     * @throws AssertionError if the actual {@code Num} value is exactly equal to the given {@code Num}
     */
    public static void assertNumNotEquals(Num expected, Num actual) {
        assertNotEquals(expected, actual);
    }

    /**
     * Verifies that two {@code Num} values are equal to within a positive delta.
     *
     * @param expected the expected {@code Num} to compare the actual value to
     * @param actual the actual {@code Num} value
     * @throws AssertionError if the actual value is not equal to the given {@code Num} representation within the delta
     */
    public static void assertNumEquals(double expected, Num actual) {
        assertEquals(expected, actual.doubleValue(), GENERAL_OFFSET);
    }

    /**
     * Verifies that two {@code Num} values are not equal to within a positive delta.
     *
     * @param expected the expected {@code Num} to compare the actual value to
     * @param actual the actual {@code Num} value
     * @throws AssertionError if the actual value is equal to the given {@code Num} representation within the delta
     */
    public static void assertNumNotEquals(double expected, Num actual) {
        assertNotEquals(expected, actual.doubleValue(), GENERAL_OFFSET);
    }

    /**
     * Verifies that two {@code Num} values are equal to within a positive delta.
     *
     * @param message the {@code String} message to print if the values are not equal
     * @param expected the expected {@code Num} to compare the actual value to
     * @param actual the actual {@code Num} value
     * @throws AssertionError if the actual value is not equal to the given {@code Num} representation within the delta
     */
    public static void assertNumEquals(String message, double expected, Num actual) {
        assertEquals(message, expected, actual.doubleValue(), GENERAL_OFFSET);
    }

    /**
     * Verifies that two indicators have the same size and the same values to within a positive delta.
     * 
     * @param expected indicator of expected values
     * @param actual indicator of actual values
     * @throws AssertionError if the size of the indicators is not identical
     *             or if any of the actual values are not equal to the corresponding expected values within the delta
     */
    public static void assertIndicatorEquals(Indicator<Num> expected, Indicator<Num> actual) {
        assertEquals("Size does not match,", expected.getTimeSeries().getBarCount(), actual.getTimeSeries().getBarCount());
        for (int i = 0; i < expected.getTimeSeries().getBarCount(); i++) {
            assertNumEquals(String.format("Failed at index %s: %s", i, actual.toString()),
                    expected.getValue(i).doubleValue(),
                    actual.getValue(i));
        }
    }

}
