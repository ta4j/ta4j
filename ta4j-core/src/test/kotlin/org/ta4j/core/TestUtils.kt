/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2022 Ta4j Organization & respective
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
package org.ta4j.core

import org.junit.Assert
import org.slf4j.LoggerFactory
import org.ta4j.core.num.DecimalNum
import org.ta4j.core.num.Num
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.min

/**
 * Utility class for `Num` tests.
 */
object TestUtils {
    /** Offset for double equality checking  */
    const val GENERAL_OFFSET = 0.0001
    private val log = LoggerFactory.getLogger(TestUtils::class.java)

    /**
     * Verifies that the actual `Num` value is equal to the given
     * `String` representation.
     *
     * @param expected the given `String` representation to compare the actual
     * value to
     * @param actual   the actual `Num` value
     * @throws AssertionError if the actual value is not equal to the given
     * `String` representation
     */
    @JvmStatic
    fun assertNumEquals(expected: String?, actual: Num?) {
        Assert.assertEquals(actual!!.numOf(BigDecimal(expected)), actual)
    }

    /**
     * Verifies that the actual `Num` value is equal to the given `Num`.
     *
     * @param expected the given `Num` representation to compare the actual
     * value to
     * @param actual   the actual `Num` value
     * @throws AssertionError if the actual value is not equal to the given
     * `Num` representation
     */
    @JvmStatic
    fun assertNumEquals(expected: Num?, actual: Num?) {
        Assert.assertEquals(expected, actual)
    }

    /**
     * Verifies that the actual `Num` value is equal to the given `int`
     * representation.
     *
     *
     * @param expected the given `int` representation to compare the actual
     * value to
     * @param actual   the actual `Num` value
     * @throws AssertionError if the actual value is not equal to the given
     * `int` representation
     */
    @JvmStatic
    fun assertNumEquals(expected: Int, actual: Num?) {
        if (actual!!.isNaN) {
            throw AssertionError("Expected: $expected Actual: $actual")
        }
        Assert.assertEquals(actual.numOf(expected), actual)
    }

    /**
     * Verifies that the actual `Num` value is equal (within a positive
     * offset) to the given `double` representation.
     *
     * @param expected the given `double` representation to compare the actual
     * value to
     * @param actual   the actual `Num` value
     * @throws AssertionError if the actual value is not equal to the given
     * `double` representation
     */
    @JvmStatic
    fun assertNumEquals(expected: Double, actual: Num?) {
        Assert.assertEquals(expected, actual!!.doubleValue(), GENERAL_OFFSET)
    }

    /**
     * Verifies that the actual `Num` value is not equal to the given
     * `int` representation.
     *
     * @param actual     the actual `Num` value
     * @param unexpected the given `int` representation to compare the actual
     * value to
     * @throws AssertionError if the actual value is equal to the given `int`
     * representation
     */
    @JvmStatic
    fun assertNumNotEquals(unexpected: Int, actual: Num?) {
        Assert.assertNotEquals(actual!!.numOf(unexpected), actual)
    }

    /**
     * Verifies that two indicators have the same size and values to an offset
     *
     * @param expected indicator of expected values
     * @param actual   indicator of actual values
     */
    @JvmStatic
    fun assertIndicatorEquals(expected: Indicator<Num>?, actual: Indicator<Num>?) {
        Assert.assertEquals(
            "Size does not match,", expected!!.barSeries!!.barCount.toLong(),
            actual!!.barSeries!!.barCount.toLong()
        )
        for (i in 0 until expected.barSeries!!.barCount) {
            Assert.assertEquals(
                String.format("Failed at index %s: %s", i, actual.toString()),
                expected[i].doubleValue(), actual[i].doubleValue(), GENERAL_OFFSET
            )
        }
    }

    /**
     * Verifies that two indicators have either different size or different values
     * to an offset
     *
     * @param expected indicator of expected values
     * @param actual   indicator of actual values
     */
    @JvmStatic
    fun assertIndicatorNotEquals(expected: Indicator<Num>, actual: Indicator<Num>) {
        if (expected.barSeries!!.barCount != actual.barSeries!!.barCount) return
        for (i in 0 until expected.barSeries!!.barCount) {
            if (abs(
                    expected[i].doubleValue() - actual[i].doubleValue()
                ) > GENERAL_OFFSET
            ) return
        }
        throw AssertionError("Indicators match to $GENERAL_OFFSET")
    }

    /**
     * Verifies that the actual `Num` value is not equal to the given
     * `String` representation.
     *
     * @param actual   the actual `Num` value
     * @param expected the given `String` representation to compare the actual
     * value to
     * @throws AssertionError if the actual value is equal to the given
     * `String` representation
     */
    @JvmStatic
    fun assertNumNotEquals(expected: String?, actual: Num?) {
        Assert.assertNotEquals(actual!!.numOf(BigDecimal(expected)), actual)
    }

    /**
     * Verifies that the actual `Num` value is not equal to the given
     * `Num`.
     *
     * @param actual   the actual `Num` value
     * @param expected the given `Num` representation to compare the actual
     * value to
     * @throws AssertionError if the actual value is equal to the given `Num`
     * representation
     */
    @JvmStatic
    fun assertNumNotEquals(expected: Num?, actual: Num?) {
        Assert.assertNotEquals(expected, actual)
    }

    /**
     * Verifies that the actual `Num` value is not equal (within a positive
     * offset) to the given `double` representation.
     *
     * @param actual   the actual `Num` value
     * @param expected the given `double` representation to compare the actual
     * value to
     * @throws AssertionError if the actual value is equal to the given
     * `double` representation
     */
    @JvmStatic
    fun assertNumNotEquals(expected: Double, actual: Num?) {
        Assert.assertNotEquals(expected, actual!!.doubleValue(), GENERAL_OFFSET)
    }

    /**
     * Verifies that two indicators have the same size and values
     *
     * @param expected indicator of expected values
     * @param actual   indicator of actual values
     */
    @JvmStatic
    fun assertIndicatorEquals(expected: Indicator<Num>, actual: Indicator<Num>, delta: Num?) {
        Assert.assertEquals(
            "Size does not match,", expected.barSeries!!.barCount.toLong(),
            actual.barSeries!!.barCount.toLong()
        )
        for (i in expected.barSeries!!.beginIndex until expected.barSeries!!.endIndex) {
            // convert to DecimalNum via String (auto-precision) avoids Cast Class
            // Exception
            val exp: Num = DecimalNum.valueOf(expected[i].toString())
            val act: Num = DecimalNum.valueOf(actual[i].toString())
            val result = exp.minus(act).abs()
            if (result.isGreaterThan(delta)) {
                log.debug("{} expected does not match", exp)
                log.debug("{} actual", act)
                log.debug("{} offset", delta)
                var expString = exp.toString()
                var actString = act.toString()
                val minLen = min(expString.length, actString.length)
                if (expString.length > minLen) expString = expString.substring(0, minLen) + ".."
                if (actString.length > minLen) actString = actString.substring(0, minLen) + ".."
                throw AssertionError(
                    String.format(
                        "Failed at index %s: expected %s but actual was %s",
                        i,
                        expString,
                        actString
                    )
                )
            }
        }
    }

    /**
     * Verifies that two indicators have either different size or different values
     * to an offset
     *
     * @param expected indicator of expected values
     * @param actual   indicator of actual values
     * @param delta    num offset to which the indicators must be different
     */
    @JvmStatic
    fun assertIndicatorNotEquals(expected: Indicator<Num>, actual: Indicator<Num>, delta: Num) {
        if (expected.barSeries!!.barCount != actual.barSeries!!.barCount) {
            return
        }
        for (i in 0 until expected.barSeries!!.barCount) {
            val exp: Num = DecimalNum.valueOf(expected[i].toString())
            val act: Num = DecimalNum.valueOf(actual[i].toString())
            val result = exp.minus(act).abs()
            if (result.isGreaterThan(delta)) {
                return
            }
        }
        throw AssertionError("Indicators match to $delta")
    }
}