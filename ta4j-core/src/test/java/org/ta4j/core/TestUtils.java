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

import org.ta4j.core.num.BigDecimalNum;
import org.ta4j.core.num.Num;

import static org.junit.Assert.assertEquals;

/**
 * Utility class for {@code Num} tests.
 */
public class TestUtils {

    /** Offset for double equality checking */
    // deprecated so that JUnit Assert calls direct from unit tests will show warnings
    // TODO: modify unit tests to not call JUnit Assert but to call TestUtils Assert
    @Deprecated
    public static final double GENERAL_OFFSET = 0.0001;
    // for 32 digit BigDecimalNum:
    //    private static final Num BIGDECIMALNUM_OFFSET = BigDecimalNum.valueOf("0.00000000000000000000000000001");
    // for DoubleNum:
    //    private static final Num BIGDECIMALNUM_OFFSET = BigDecimalNum.valueOf("0.000000000001");
    // for old unit test expected values with 4 decimal precision:
    private static final Num BIGDECIMALNUM_OFFSET = BigDecimalNum.valueOf("0.0001");

    /**
     * Verifies that the actual {@code Num} value is exactly equal to the expected {@code String} representation.
     *
     * @param expected the given {@code String} representation to compare the actual value to
     * @param actual the actual {@code Num} value
     * @throws AssertionError if the actual {@code Num} value is not exactly equal to the given {@code String} representation
     */
    public static void assertNumExactlyEquals(String expected, Num actual) {
        assertBigDecimalNumEquals(null, BigDecimalNum.valueOf(expected), BigDecimalNum.valueOf(actual.toString()));
    }

    public static void assertNumEquals(String expected, Num actual) {
        assertBigDecimalNumEquals(null, BigDecimalNum.valueOf(expected), BigDecimalNum.valueOf(actual.toString()), BIGDECIMALNUM_OFFSET);
    }

    /**
     * Verifies that the actual {@code Num} value is not exactly equal to the expected {@code String} representation.
     *
     * @param expected the given {@code String} representation to compare the actual value to
     * @param actual the actual {@code Num} value
     * @throws AssertionError if the actual {@code Num} value is exactly equal to the given {@code String} representation
     */
    public static void assertNumNotEquals(String expected, Num actual) {
        assertBigDecimalNumNotEquals(null, BigDecimalNum.valueOf(expected), BigDecimalNum.valueOf(actual.toString()));
    }

    /**
     * Verifies that the actual {@code Num} value is exactly equal to the expected {@code Num}.
     *
     * @param expected the given {@code Num} to compare the actual value to
     * @param actual the actual {@code Num} value
     * @throws AssertionError if the actual {@code Num} value is not exactly equal to the given {@code Num}
     */
    public static void assertNumEquals(Num expected, Num actual){
        assertBigDecimalNumEquals(null, BigDecimalNum.valueOf(expected.toString()), BigDecimalNum.valueOf(actual.toString()));
    }

    /**
     * Verifies that the actual {@code Num} value is not exactly equal to the expected {@code Num}.
     *
     * @param expected the given {@code Num} to compare the actual value to
     * @param actual the actual {@code Num} value
     * @throws AssertionError if the actual {@code Num} value is exactly equal to the given {@code Num}
     */
    public static void assertNumNotEquals(Num expected, Num actual) {
        assertBigDecimalNumNotEquals(null, BigDecimalNum.valueOf(expected.toString()), BigDecimalNum.valueOf(actual.toString()));
    }

    /**
     * Verifies that two {@code Num} values are equal to within a positive delta.
     *
     * @param expected the expected {@code Num} to compare the actual value to
     * @param actual the actual {@code Num} value
     * @throws AssertionError if the actual value is not equal to the given {@code Num} representation within the delta
     */
    public static void assertNumEquals(double expected, Num actual) {
        assertBigDecimalNumEquals(null, BigDecimalNum.valueOf(expected), BigDecimalNum.valueOf(actual.toString()), BIGDECIMALNUM_OFFSET);
    }

    public static void assertNumNotEquals(double expected, Num actual) {
        assertBigDecimalNumNotEquals(null, BigDecimalNum.valueOf(expected), BigDecimalNum.valueOf(actual.toString()));
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
        assertIndicatorEquals(expected, actual, BIGDECIMALNUM_OFFSET);
    }

    public static void assertIndicatorEquals(Indicator<Num> expected, Indicator<Num> actual, Num offset) {
        assertEquals("Size does not match,", expected.getTimeSeries().getBarCount(), actual.getTimeSeries().getBarCount());
        for (int i = 0; i < expected.getTimeSeries().getBarCount(); i++) {
            //            System.out.println(expected.getValue(i) + ", " + actual.getValue(i));
            Num exp = BigDecimalNum.valueOf(expected.getValue(i).toString());
            Num act = BigDecimalNum.valueOf((actual.getValue(i).toString()));
            assertBigDecimalNumEquals(
                    String.format("Failed at index %s: value %s does not match expected %s", i, act.toString(), exp.toString()),
                    exp, act, offset);
        }
    }

    /**
     * Verifies that two indicators have different size or at least one pair of values are different to within a positive delta.
     * 
     * @param expected indicator of expected values
     * @param actual indicator of actual values
     * @throws AssertionError if the size of the indicators is identical
     *             and if all of the actual values are equal to the corresponding expected values within the delta
     */
    public static void assertIndicatorNotEquals(Indicator<Num> expected, Indicator<Num> actual) {
        assertIndicatorNotEquals(expected, actual, BIGDECIMALNUM_OFFSET);
    }

    public static void assertIndicatorNotEquals(Indicator<Num> expected, Indicator<Num> actual, Num offset) {
        if (expected.getTimeSeries().getBarCount() == actual.getTimeSeries().getBarCount()) {
            for (int i = 0; i < expected.getTimeSeries().getBarCount(); i++) {
                Num exp = BigDecimalNum.valueOf(expected.getValue(i).toString());
                Num act = BigDecimalNum.valueOf(actual.getValue(i).toString());
                if (exp.minus(act).abs().isGreaterThan(offset)) {
                    return;
                }
            }
        }
        throw new AssertionError("indicators are the same within delta");
    }

    private static void assertBigDecimalNumEquals(String message, Num expected, Num actual, Num delta) {
        //        System.out.println("assertBDE " + expected.toString() + ", " + actual.toString());
        if (expected.minus(actual).abs().isGreaterThan(delta)) {
            if (message == null) message = "value " + actual.toString() + " does not match expected " + expected.toString();
            throw new AssertionError(message);
        }
    }

    private static void assertBigDecimalNumEquals(String message, Num expected, Num actual) {
        //        System.out.println("assertBDE " + expected.toString() + ", " + actual.toString());
        if (expected.compareTo(actual) != 0) {
            if (message == null) message = "value " + actual.toString() + " does not match expected " + expected.toString();
            throw new AssertionError(message);
        }
    }

    private static void assertBigDecimalNumNotEquals(String message, Num expected, Num actual) {
        //        System.out.println("assertBDNE " + expected.toString() + ", " + actual.toString());
        if (expected.compareTo(actual) == 0) {
            if (message == null) message = "value " + actual.toString() + " matches expected " + expected.toString();
            throw new AssertionError(message);
        }
    }

}
