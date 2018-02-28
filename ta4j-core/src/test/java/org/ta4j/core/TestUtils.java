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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.num.BigDecimalNum;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

import static org.junit.Assert.assertEquals;

/**
 * Utility class for {@code Num} tests.
 */
public class TestUtils {

    public static final int DEFAULT_PRECISION = 4;
    // override the default BigDecimalNum precision for high precision conversions
    // this allows for reading expected value Strings with higher precision than the default (32)
    public static final int HIGH_PRECISION = 64;
    private static final Function<String, Num> highPrecisionNumFunc = (val -> BigDecimalNum.valueOf(val, HIGH_PRECISION));
    /** Offset for double equality checking */
    // TODO: @Deprecated
    public static final double GENERAL_OFFSET = DoubleNum.valueOf(DoubleNum.EPS).doubleValue();
    private static final Num HIGH_PRECISION_OFFSET = highPrecisionNumFunc.apply("0.0001");
    private static final Logger log = LoggerFactory.getLogger(TestUtils.class);

    /**
     * Verifies that two {@code Indicator} objects have the same size and matching values, to a precision.
     * 
     * @param expected {@code Indicator} to compare the actual {@code Indicator} to
     * @param actual {@code Indicator} of actual values to inspect
     * @param precision optional {@code int} length 1 array for precision, may be null or length 0
     * @throws AssertionError if the size of the {@code Indicator}s is not identical
     *             or if any of the corresponding values do not match, to a precision
     */
    public static void assertIndicatorMatches(Indicator<Num> expected, Indicator<Num> actual, int... precision) {
        for (int i = 0; i < expected.getTimeSeries().getBarCount(); i++) {
            Num exp = highPrecisionNumFunc.apply(expected.getValue(i).toString());
            Num act = highPrecisionNumFunc.apply((actual.getValue(i).toString()));
            String message = String.format("Failed at index %s: ", i);
            assertNumMatches(message, exp, act, precision);
        }
    }

    /**
     * Verifies that two {@code Indicator} objects have different sizes or non-matching values, to a precision.
     * 
     * @param expected {@code Indicator} to compare the actual {@code Indicator} to
     * @param actual {@code Indicator} of actual values to inspect
     * @param precision optional {@code int} length 1 array for precision, may be null or length 0
     * @throws AssertionError if the size of the {@code Indicator}s are not equal
     *             or if all of the corresponding values match, to a precision
     */
    public static void assertIndicatorNotMatches(Indicator<Num> expected, Indicator<Num> actual, int... precision) {
        int precisionVal = precision == null | precision.length == 0 ? DEFAULT_PRECISION : precision[0];
        if (expected.getTimeSeries().getBarCount() == actual.getTimeSeries().getBarCount()) {
            for (int i = 0; i < expected.getTimeSeries().getBarCount(); i++) {
                Num exp = expected.getValue(i);
                Num act = actual.getValue(i);
                Num hpexp = highPrecisionNumFunc.apply(expected.getValue(i).toString());
                Num hpact = highPrecisionNumFunc.apply(actual.getValue(i).toString());
                if (!((BigDecimalNum) hpexp).matches(hpact, precisionVal)) {
                    log.debug("Passed at index {}: Value {} does not match expected {} to precision {}", i, act, exp, precision);
                    return;
                }
            }
        }
        throw new AssertionError("Indicators are the same within delta");
    }

    /**
     * Verifies that the {@code Num} value matches the {@code String} value, to a precision.
     *
     * @param expected {@code String} to compare the actual value to
     * @param actual {@code Num} to inspect
     * @param precision optional {@code int} length 1 array for precision, may be null or length 0
     * @throws AssertionError if the {@code Num} does not match the {@code String}, to a precision
     */
    public static void assertNumMatches(String expected, Num actual, int... precision) {
        assertNumMatches(null,
                highPrecisionNumFunc.apply(expected),
                highPrecisionNumFunc.apply(actual.toString()),
                precision);
    }

    /**
     * Verifies that the {@code Num} values match, to a precision.
     *
     * @param message {@code String} to print in the AssertionError (or null)
     * @param expected {@code Num} to compare the actual value to
     * @param actual {@code Num} to inspect
     * @param precision optional {@code int} length 1 array for precision, may be null or length 0
     * @throws AssertionError if the {@code Num} values do not match, to a precision
     */
    private static void assertNumMatches(String message, Num expected, Num actual, int... precision) {
        int precisionVal = precision == null || precision.length == 0 ? DEFAULT_PRECISION : precision[0];
        log.trace("expected {}, actual {}, precision {}", expected, actual, precision);
        if (!((BigDecimalNum) expected).matches(actual, precisionVal)) {
            message += "Value " + actual.toString() + " does not match expected " + expected.toString() + " within precision " + precision;
            log.debug(message);
            throw new AssertionError(message);
        }
    }

    /**
     * Verifies that the {@code Num} classes are the same.
     *
     * @param expected {@code Num} to compare the actual class to
     * @param actual {@code Num} to inspect
     * @throws AssertionError if the {@code Num} classes are not the same
     */
    public static void assertNumClassEquals(Num expected, Num actual) {
        assertEquals(expected.getClass(), actual.getClass());
    }

    /**
     * Verifies that the {@code Num} value is equal to the {@code String} value within an offset.
     *
     * @param expected {@code String} to compare the actual value to
     * @param actual {@code Num} to inspect
     * @param delta optional {@code Num} length 1 array for offset, may be null or length 0 or delta[0] may be null
     * @throws AssertionError if the {@code Num} is not equal to the {@code String} within an offset
     */
    // TODO: @Deprecated
    public static void assertNumEquals(String expected, Num actual, Num... delta) {
        assertNumEquals(null,
                highPrecisionNumFunc.apply(expected),
                highPrecisionNumFunc.apply(actual.toString()),
                delta == null || delta.length == 0 || delta[0] == null ? HIGH_PRECISION_OFFSET : delta[0]);
    }

    /**
     * Verifies that the {@code Num} value is not equal to the {@code String} value within an offset.
     *
     * @param expected {@code String} to compare the actual value to
     * @param actual {@code Num} to inspect
     * @param delta optional {@code Num} length 1 array for offset, may be null or length 0 or delta[0] may be null
     * @throws AssertionError if the {@code Num} is equal to the {@code String} within an offset
     */
    // TODO: @Deprecated
    public static void assertNumNotEquals(String expected, Num actual, Num... delta) {
        assertNumNotEquals(null,
                highPrecisionNumFunc.apply(expected),
                highPrecisionNumFunc.apply(actual.toString()),
                delta == null || delta.length == 0 || delta[0] == null ? HIGH_PRECISION_OFFSET : delta[0]);
    }

    /**
     * Verifies that the {@code Num} values are equal within an offset.
     *
     * @param expected {@code Num} to compare the actual value to
     * @param actual {@code Num} to inspect
     * @param delta optional {@code Num} length 1 array for offset, may be null or length 0 or delta[0] may be null
     * @throws AssertionError if the {@code Num} values are not equal within an offset
     */
    // TODO: @Deprecated
    public static void assertNumEquals(Num expected, Num actual, Num... delta) {
        assertNumEquals(null,
                highPrecisionNumFunc.apply(expected.toString()),
                highPrecisionNumFunc.apply(actual.toString()),
                delta == null || delta.length == 0 || delta[0] == null ? HIGH_PRECISION_OFFSET : delta[0]);
    }

    /**
     * Verifies that the {@code Num} values are not equal within an offset.
     *
     * @param expected {@code Num} to compare the actual value to
     * @param actual {@code Num} to inspect
     * @param delta optional {@code Num} length 1 array for offset, may be null or length 0 or delta[0] may be null
     * @throws AssertionError if the {@code Num} values are equal within an offset
     */
    // TODO: @Deprecated
    public static void assertNumNotEquals(Num expected, Num actual, Num... delta) {
        assertNumNotEquals(null,
                highPrecisionNumFunc.apply(expected.toString()),
                highPrecisionNumFunc.apply(actual.toString()),
                delta == null || delta.length == 0 || delta[0] == null ? HIGH_PRECISION_OFFSET : delta[0]);
    }

    /**
     * Verifies that the {@code Num} value is equal to the {@code double} value within an offset.
     *
     * @param expected {@code double} to compare the actual value to
     * @param actual {@code Num} to inspect
     * @param delta optional {@code Num} length 1 array for offset, may be null or length 0 or delta[0] may be null
     * @throws AssertionError if the {@code Num} is not equal to the {@code double} within an offset
     */
    // TODO: @Deprecated
    public static void assertNumEquals(double expected, Num actual, Num... delta) {
        assertNumEquals(null,
                highPrecisionNumFunc.apply(Double.toString(expected)),
                highPrecisionNumFunc.apply(actual.toString()),
                delta == null || delta.length == 0 || delta[0] == null ? HIGH_PRECISION_OFFSET : delta[0]);
    }

    /**
     * Verifies that the {@code Num} value is not equal to the {@code double} value within an offset.
     *
     * @param expected {@code double} to compare the actual value to
     * @param actual {@code Num} to inspect
     * @param delta optional {@code Num} length 1 array for offset, may be null or length 0 or delta[0] may be null
     * @throws AssertionError if the {@code Num} is equal to the {@code double} within an offset
     */
    // TODO: @Deprecated
    public static void assertNumNotEquals(double expected, Num actual, Num... delta) {
        assertNumNotEquals(null,
                highPrecisionNumFunc.apply(Double.toString(expected)),
                highPrecisionNumFunc.apply(actual.toString()),
                delta == null || delta.length == 0 || delta[0] == null ? HIGH_PRECISION_OFFSET : delta[0]);
    }

    /**
     * Verifies that two {@code Indicator} objects have the same size and the same values to within an offset.
     * 
     * @param expected {@code Indicator} to compare the actual {@code Indicator} to
     * @param actual {@code Indicator} of actual values to inspect
     * @param delta optional {@code Num} length 1 array for offset, may be null or length 0 or delta[0] may be null
     * @throws AssertionError if the size of the {@code Indicator}s is not identical
     *             or if any of the corresponding values are not equal within the delta
     */
    // TODO: @Deprecated
    public static void assertIndicatorEquals(Indicator<Num> expected, Indicator<Num> actual, Num... delta) {
        assertEquals("Size does not match,", expected.getTimeSeries().getBarCount(), actual.getTimeSeries().getBarCount());
        Num deltaVal = delta == null || delta.length == 0 || delta[0] == null ? HIGH_PRECISION_OFFSET : delta[0];
        for (int i = 0; i < expected.getTimeSeries().getBarCount(); i++) {
            Num exp = highPrecisionNumFunc.apply(expected.getValue(i).toString());
            Num act = highPrecisionNumFunc.apply((actual.getValue(i).toString()));
            String message = String.format("Failed at index %s: value %s does not match expected %s", i, act.toString(), exp.toString());
            assertNumEquals(message, exp, act, deltaVal);
        }
    }

    /**
     * Verifies that two {@code Indicator} objects have different sizes or different values to within an offset.
     * 
     * @param expected {@code Indicator} to compare the actual {@code Indicator} to
     * @param actual {@code Indicator} of actual values to inspect
     * @param delta optional {@code Num} length 1 array for offset, may be null or length 0 or delta[0] may be null
     * @throws AssertionError if the size of the {@code Indicator}s are not equal
     *             or if any of the corresponding values are not equal within the delta
     */
    // TODO: @Deprecated
    public static void assertIndicatorNotEquals(Indicator<Num> expected, Indicator<Num> actual, Num... delta) {
        if (expected.getTimeSeries().getBarCount() == actual.getTimeSeries().getBarCount()) {
            Num deltaVal = delta == null || delta.length == 0 || delta[0] == null ? HIGH_PRECISION_OFFSET : delta[0];
            for (int i = 0; i < expected.getTimeSeries().getBarCount(); i++) {
                Num exp = highPrecisionNumFunc.apply(expected.getValue(i).toString());
                Num act = highPrecisionNumFunc.apply(actual.getValue(i).toString());
                if (exp.minus(act).abs().isGreaterThan(deltaVal)) {
                    return;
                }
            }
        }
        throw new AssertionError("Indicators are the same within delta");
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
    // TODO: @Deprecated
    private static void assertNumEquals(String message, Num expected, Num actual, Num delta) {
        log.trace("expected {}, actual {}, delta {}", expected, actual, delta);
        if (expected.minus(actual).abs().isGreaterThan(delta)) {
            if (message == null) message = "Value " + actual.toString() + " does not match expected " + expected.toString() + " within delta";
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
    // TODO: @Deprecated
    private static void assertNumNotEquals(String message, Num expected, Num actual, Num delta) {
        if (expected.minus(actual).abs().isLessThan(delta)) {
            if (message == null) message = "Value " + actual.toString() + " matches expected " + expected.toString() + " within delta " + delta.toString();
            log.debug(message);
            throw new AssertionError(message);
        }
    }

}
