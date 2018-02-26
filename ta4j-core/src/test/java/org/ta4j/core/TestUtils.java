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

import java.util.function.Function;

import org.ta4j.core.num.BigDecimalNum;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

import static org.junit.Assert.assertEquals;

/**
 * Utility class for {@code Num} tests.
 */
public class TestUtils {

    // digits of precision for high precision operations
    public static final int HIGH_PRECISION = 64;
    // override the default BigDecimalNum precision for high precision conversions
    private static final Function<String, Num> highPrecisionNumFunc = (val -> BigDecimalNum.valueOf(val, HIGH_PRECISION));
    /** Offset for double equality checking */
    // deprecated so that JUnit Assert calls direct from unit tests will show warnings
    // TODO: modify unit tests to not call JUnit Assert but to call TestUtils Assert
    @Deprecated
    public static final double GENERAL_OFFSET = DoubleNum.valueOf(DoubleNum.EPS).doubleValue();
    private static final Num HIGH_PRECISION_OFFSET = highPrecisionNumFunc.apply("0.0001");


    /**
     * Verifies that the {@code Num} value is equal to the {@code String} value within an offset.
     *
     * @param expected {@code String} to compare the actual value to
     * @param actual {@code Num} to inspect
     * @throws AssertionError if the {@code Num} is not equal to the {@code String} within an offset
     */
    public static void assertNumEquals(String expected, Num actual) {
        assertNumEquals(null,
                highPrecisionNumFunc.apply(expected),
                highPrecisionNumFunc.apply(actual.toString()),
                HIGH_PRECISION_OFFSET);
    }

    /**
     * Verifies that the {@code Num} value is not equal to the {@code String} value within an offset.
     *
     * @param expected {@code String} to compare the actual value to
     * @param actual {@code Num} to inspect
     * @throws AssertionError if the {@code Num} is equal to the {@code String} within an offset
     */
    public static void assertNumNotEquals(String expected, Num actual) {
        assertNumNotEquals(null,
                highPrecisionNumFunc.apply(expected),
                highPrecisionNumFunc.apply(actual.toString()),
                HIGH_PRECISION_OFFSET);
    }

    /**
     * Verifies that the {@code Num} values are equal within an offset.
     *
     * @param expected {@code Num} to compare the actual value to
     * @param actual {@code Num} to inspect
     * @throws AssertionError if the {@code Num} values are not equal within an offset
     */
    public static void assertNumEquals(Num expected, Num actual) {
        assertNumEquals(null,
                highPrecisionNumFunc.apply(expected.toString()),
                highPrecisionNumFunc.apply(actual.toString()),
                HIGH_PRECISION_OFFSET);
    }

    /**
     * Verifies that the {@code Num} values are not equal within an offset.
     *
     * @param expected {@code Num} to compare the actual value to
     * @param actual {@code Num} to inspect
     * @throws AssertionError if the {@code Num} values are equal within an offset
     */
    public static void assertNumNotEquals(Num expected, Num actual) {
        assertNumNotEquals(null,
                highPrecisionNumFunc.apply(expected.toString()),
                highPrecisionNumFunc.apply(actual.toString()),
                HIGH_PRECISION_OFFSET);
    }

    /**
     * Verifies that the {@code Num} value is equal to the {@code double} value within an offset.
     *
     * @param expected {@code double} to compare the actual value to
     * @param actual {@code Num} to inspect
     * @throws AssertionError if the {@code Num} is not equal to the {@code double} within an offset
     */
    public static void assertNumEquals(double expected, Num actual) {
        assertNumEquals(null,
                highPrecisionNumFunc.apply(Double.toString(expected)),
                highPrecisionNumFunc.apply(actual.toString()),
                HIGH_PRECISION_OFFSET);
    }

    /**
     * Verifies that the {@code Num} value is not equal to the {@code double} value within an offset.
     *
     * @param expected {@code double} to compare the actual value to
     * @param actual {@code Num} to inspect
     * @throws AssertionError if the {@code Num} is equal to the {@code double} within an offset
     */
    public static void assertNumNotEquals(double expected, Num actual) {
        assertNumNotEquals(null,
                highPrecisionNumFunc.apply(Double.toString(expected)),
                highPrecisionNumFunc.apply(actual.toString()),
                HIGH_PRECISION_OFFSET);
    }

    /**
     * Verifies that two {@code Indicator} objects have the same size and the same values to within an offset.
     * 
     * @param expected {@code Indicator} to compare the actual {@code Indicator} to
     * @param actual {@code Indicator} of actual values to inspect
     * @throws AssertionError if the size of the {@code Indicator}s is not identical
     *             or if any of the corresponding values are not equal within the delta
     */
    public static void assertIndicatorEquals(Indicator<Num> expected, Indicator<Num> actual) {
        assertIndicatorEquals(expected, actual, HIGH_PRECISION_OFFSET);
    }

    /**
     * Verifies that two {@code Indicator} objects have the same size and the same values to within an offset.
     * 
     * @param expected {@code Indicator} to compare the actual {@code Indicator} to
     * @param actual {@code Indicator} of actual values to inspect
     * @param delta {@code Num} of the allowable offset
     * @throws AssertionError if the size of the {@code Indicator}s is not identical
     *             or if any of the corresponding values are not equal within the delta
     */
    public static void assertIndicatorEquals(Indicator<Num> expected, Indicator<Num> actual, Num delta) {
        assertEquals("Size does not match,", expected.getTimeSeries().getBarCount(), actual.getTimeSeries().getBarCount());
        for (int i = 0; i < expected.getTimeSeries().getBarCount(); i++) {
            Num exp = highPrecisionNumFunc.apply(expected.getValue(i).toString());
            Num act = highPrecisionNumFunc.apply((actual.getValue(i).toString()));
            assertNumEquals(
                    String.format("Failed at index %s: value %s does not match expected %s",
                            i, act.toString(), exp.toString()), exp, act, delta);
        }
    }

    /**
     * Verifies that two {@code Indicator} objects have different sizes or different values to within an offset.
     * 
     * @param expected {@code Indicator} to compare the actual {@code Indicator} to
     * @param actual {@code Indicator} of actual values to inspect
     * @throws AssertionError if the size of the {@code Indicator}s are not equal
     *             or if any of the corresponding values are not equal within the delta
     */
    public static void assertIndicatorNotEquals(Indicator<Num> expected, Indicator<Num> actual) {
        assertIndicatorNotEquals(expected, actual, HIGH_PRECISION_OFFSET);
    }

    /**
     * Verifies that two {@code Indicator} objects have different sizes or different values to within an offset.
     * 
     * @param expected {@code Indicator} to compare the actual {@code Indicator} to
     * @param actual {@code Indicator} of actual values to inspect
     * @param delta {@code Num} of the allowable offset
     * @throws AssertionError if the size of the {@code Indicator}s are not equal
     *             or if any of the corresponding values are not equal within the delta
     */
    public static void assertIndicatorNotEquals(Indicator<Num> expected, Indicator<Num> actual, Num delta) {
        if (expected.getTimeSeries().getBarCount() == actual.getTimeSeries().getBarCount()) {
            for (int i = 0; i < expected.getTimeSeries().getBarCount(); i++) {
                Num exp = highPrecisionNumFunc.apply(expected.getValue(i).toString());
                Num act = highPrecisionNumFunc.apply(actual.getValue(i).toString());
                if (exp.minus(act).abs().isGreaterThan(delta)) {
                    return;
                }
            }
        }
        throw new AssertionError("indicators are the same within delta");
    }

    /**
     * Verifies that the {@code Num} values are equal within an offset.
     *
     * @param message {@code String} to print in the AssertionError (or null)
     * @param expected {@code Num} to compare the actual value to
     * @param actual {@code Num} to inspect
     * @param delta {@code Num} of the allowable offset
     * @throws AssertionError if the {@code Num} values are not equal within an offset
     */
    private static void assertNumEquals(String message, Num expected, Num actual, Num delta) {
        if (expected.minus(actual).abs().isGreaterThan(delta)) {
            if (message == null) message = "value " + actual.toString() + " does not match expected " + expected.toString() + " within delta";
            throw new AssertionError(message);
        }
    }

    /**
     * Verifies that the {@code Num} values are different within an offset.
     *
     * @param message {@code String} to print in the AssertionError (or null)
     * @param expected {@code Num} to compare the actual value to
     * @param actual {@code Num} to inspect
     * @param delta {@code Num} of the allowable offset
     * @throws AssertionError if the {@code Num} values are equal within an offset
     */
    private static void assertNumNotEquals(String message, Num expected, Num actual, Num delta) {
        if (expected.minus(actual).abs().isLessThan(delta)) {
            if (message == null) message = "value " + actual.toString() + " matches expected " + expected.toString() + " within delta";
            throw new AssertionError(message);
        }
    }

}
