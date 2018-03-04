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

import org.ta4j.core.num.Num;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Utility class for {@code Num} tests.
 */
public class TestUtils {

    /** Offset for double equality checking */
    public static final double GENERAL_OFFSET = 0.0001;

    /**
     * Verifies that the actual {@code Num} value is equal to the given {@code String} representation.
     *
     * @param actual the actual {@code Num} value
     * @param expected the given {@code String} representation to compare the actual value to
     * @throws AssertionError if the actual value is not equal to the given {@code String} representation
     */
    public static void assertNumEquals(String expected, Num actual) {
        assertEquals(actual.numOf(new BigDecimal(expected)), actual);
    }

    /**
     * Verifies that the actual {@code Num} value is equal to the given {@code Num}.
     *
     * @param actual the actual {@code Num} value
     * @param expected the given {@code Num} representation to compare the actual value to
     * @throws AssertionError if the actual value is not equal to the given {@code Num} representation
     */
    public static void assertNumEquals(Num expected, Num actual){
        assertEquals(expected, actual);
    }

    /**
     * Verifies that the actual {@code Num} value is equal to the given {@code int} representation.
     *
     * @param actual the actual {@code Num} value
     * @param expected the given {@code int} representation to compare the actual value to
     * @throws AssertionError if the actual value is not equal to the given {@code int} representation
     */
    public static void assertNumEquals(int expected, Num actual) {
        assertEquals(expected, actual.intValue());
    }

    /**
     * Verifies that the actual {@code Num} value is equal (within a positive offset) to the given {@code double} representation.
     *
     * @param actual the actual {@code Num} value
     * @param expected the given {@code double} representation to compare the actual value to
     * @throws AssertionError if the actual value is not equal to the given {@code double} representation
     */
    public static void assertNumEquals(double expected, Num actual) {
        assertEquals(expected, actual.doubleValue(), GENERAL_OFFSET);
    }

    /**
     * Verifies that the actual {@code Num} value is not equal to the given {@code int} representation.
     *
     * @param actual the actual {@code Num} value
     * @param unexpected the given {@code int} representation to compare the actual value to
     * @throws AssertionError if the actual value is equal to the given {@code int} representation
     */
    public static void assertNumNotEquals(int unexpected,Num actual) {
        assertNotEquals(unexpected, actual.intValue());
    }

    /**
     * Verifies that expected values match indicator values.
     *
     * @param expectedValues expected list of values
     * @param actualIndicator indicator to compare
     */
    public static void assertValuesEquals(Indicator<Num> actualIndicator, List<Num> expectedValues) {
        assertEquals("Size does not match,", expectedValues.size(), actualIndicator.getTimeSeries().getBarCount());
        for (int i = 0; i < expectedValues.size(); i++) {
            assertEquals(String.format("Values at index <%d> does not match,", i),
                    expectedValues.get(i).doubleValue(), actualIndicator.getValue(i).doubleValue(), GENERAL_OFFSET);
        }
    }

    /**
     * Verifies that two indicators have the same size and values
     * @param expected indicator of expected values
     * @param actual indicator of actual values
     */
    public static void assertIndicatorEquals(Indicator<Num> expected, Indicator<Num> actual) {
        org.junit.Assert.assertEquals("Size does not match,",
                expected.getTimeSeries().getBarCount(), actual.getTimeSeries().getBarCount());
        for (int i = 0; i < expected.getTimeSeries().getBarCount(); i++) {
            assertEquals(String.format("Failed at index %s: %s",i,actual.toString()),
                    expected.getValue(i).doubleValue(),
                    actual.getValue(i).doubleValue(), GENERAL_OFFSET);
        }
    }

}
