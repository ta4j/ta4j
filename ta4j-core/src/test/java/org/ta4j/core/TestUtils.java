/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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
package org.ta4j.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;

/**
 * Utility class for {@code Num} tests.
 * * {@code Num} 测试的实用程序类。
 */
public class TestUtils {

    /** Offset for double equality checking
     * 双重相等检查的偏移量 */
    public static final double GENERAL_OFFSET = 0.0001;

    private static Logger log = LoggerFactory.getLogger(TestUtils.class);

    /**
     * Verifies that the actual {@code Num} value is equal to the given {@code String} representation.
     * * 验证实际的 {@code Num} 值是否等于给定的 {@code String} 表示。
     *
     * @param actual   the actual {@code Num} value
     *                 实际的 {@code Num} 值
     *
     * @param expected the given {@code String} representation to compare the actual  value to
     *                 * @param 期望给定的 {@code String} 表示将实际值与
     *
     * @throws AssertionError if the actual value is not equal to the given    {@code String} representation
     * * @throws AssertionError 如果实际值不等于给定的 {@code String} 表示
     */
    public static void assertNumEquals(String expected, Num actual) {
        assertEquals(actual.numOf(new BigDecimal(expected)), actual);
    }

    /**
     * Verifies that the actual {@code Num} value is equal to the given {@code Num}.
     * * 验证实际的 {@code Num} 值是否等于给定的 {@code Num}。
     *
     * @param actual   the actual {@code Num} value
     *                 实际的 {@code Num} 值
     *
     * @param expected the given {@code Num} representation to compare the actual    value to
     *                 * @param 期望给定的 {@code Num} 表示将实际值与
     *
     * @throws AssertionError if the actual value is not equal to the given  {@code Num} representation
     * * @throws AssertionError 如果实际值不等于给定的 {@code Num} 表示
     */
    public static void assertNumEquals(Num expected, Num actual) {
        assertEquals(expected, actual);
    }

    /**
     * Verifies that the actual {@code Num} value is equal to the given {@code int} representation.
     * * 验证实际的 {@code Num} 值是否等于给定的 {@code int} 表示。
     *
     * @param actual   the actual {@code Num} value
     *                 实际的 {@code Num} 值
     *
     * @param expected the given {@code int} representation to compare the actual   value to
     *                 * @param 期望给定的 {@code int} 表示将实际值与
     *
     * @throws AssertionError if the actual value is not equal to the given   {@code int} representation
     * * @throws AssertionError 如果实际值不等于给定的 {@code int} 表示
     */
    public static void assertNumEquals(int expected, Num actual) {
        assertEquals(actual.numOf(expected), actual);
    }

    /**
     * Verifies that the actual {@code Num} value is equal (within a positive offset) to the given {@code double} representation.
     * * 验证实际的 {@code Num} 值是否等于（在正偏移量内）给定的 {@code double} 表示。
     *
     * @param actual   the actual {@code Num} value
     *                 实际的 {@code Num} 值
     *
     * @param expected the given {@code double} representation to compare the actual   value to
     *                 * @param 期望给定的 {@code double} 表示将实际值与
     *
     * @throws AssertionError if the actual value is not equal to the given    {@code double} representation
     * * @throws AssertionError 如果实际值不等于给定的 {@code double} 表示
     */
    public static void assertNumEquals(double expected, Num actual) {
        assertEquals(expected, actual.doubleValue(), GENERAL_OFFSET);
    }

    /**
     * Verifies that the actual {@code Num} value is not equal to the given {@code int} representation.
     * * 验证实际的 {@code Num} 值不等于给定的 {@code int} 表示。
     *
     * @param actual     the actual {@code Num} value
     *                   实际的 {@code Num} 值
     *
     * @param unexpected the given {@code int} representation to compare the actual    value to
     *                   给定的 {@code int} 表示来比较实际值
     *
     * @throws AssertionError if the actual value is equal to the given {@code int}  representation
     * * @throws AssertionError 如果实际值等于给定的 {@code int} 表示
     */
    public static void assertNumNotEquals(int unexpected, Num actual) {
        assertNotEquals(actual.numOf(unexpected), actual);
    }

    /**
     * Verifies that two indicators have the same size and values to an offset
     * 验证两个指标的大小和偏移值是否相同
     * 
     * @param expected indicator of expected values
     *                 预期值指标
     *
     * @param actual   indicator of actual values
     *                 实际值指标
     */
    public static void assertIndicatorEquals(Indicator<Num> expected, Indicator<Num> actual) {
        org.junit.Assert.assertEquals("Size does not match 尺寸不匹配,", expected.getBarSeries().getBarCount(),
                actual.getBarSeries().getBarCount());
        for (int i = 0; i < expected.getBarSeries().getBarCount(); i++) {
            assertEquals(String.format("Failed at index 索引失败 %s: %s", i, actual.toString()),
                    expected.getValue(i).doubleValue(), actual.getValue(i).doubleValue(), GENERAL_OFFSET);
        }
    }

    /**
     * Verifies that two indicators have either different size or different values to an offset
     * * 验证两个指标是否具有不同的大小或不同的偏移值
     * 
     * @param expected indicator of expected values
     *                 预期值指标
     * @param actual   indicator of actual values
     *                 实际值指标
     */
    public static void assertIndicatorNotEquals(Indicator<Num> expected, Indicator<Num> actual) {
        if (expected.getBarSeries().getBarCount() != actual.getBarSeries().getBarCount())
            return;
        for (int i = 0; i < expected.getBarSeries().getBarCount(); i++) {
            if (Math.abs(expected.getValue(i).doubleValue() - actual.getValue(i).doubleValue()) > GENERAL_OFFSET)
                return;
        }
        throw new AssertionError("Indicators match to 指标匹配 " + GENERAL_OFFSET);
    }

    /**
     * Verifies that the actual {@code Num} value is not equal to the given {@code String} representation.
     * * 验证实际的 {@code Num} 值不等于给定的 {@code String} 表示。
     *
     * @param actual   the actual {@code Num} value
     *                 实际的 {@code Num} 值
     *
     * @param expected the given {@code String} representation to compare the actual  value to
     *                 * @param 期望给定的 {@code String} 表示将实际值与
     *
     * @throws AssertionError if the actual value is equal to the given   {@code String} representation
     * * @throws AssertionError 如果实际值等于给定的 {@code String} 表示
     */
    public static void assertNumNotEquals(String expected, Num actual) {
        assertNotEquals(actual.numOf(new BigDecimal(expected)), actual);
    }

    /**
     * Verifies that the actual {@code Num} value is not equal to the given {@code Num}.
     * * 验证实际的 {@code Num} 值不等于给定的 {@code Num}。
     *
     * @param actual   the actual {@code Num} value
     *                 实际的 {@code Num} 值
     *
     * @param expected the given {@code Num} representation to compare the actual  value to
     *                 给定的 {@code Num} 表示来比较实际值
     *
     * @throws AssertionError if the actual value is equal to the given {@code Num}  representation
     * * @throws AssertionError 如果实际值等于给定的 {@code Num} 表示
     */
    public static void assertNumNotEquals(Num expected, Num actual) {
        assertNotEquals(expected, actual);
    }

    /**
     * Verifies that the actual {@code Num} value is not equal (within a positive offset) to the given {@code double} representation.
     * * 验证实际的 {@code Num} 值是否不等于（在正偏移量内）给定的 {@code double} 表示。
     *
     * @param actual   the actual {@code Num} value
     *                 实际的 {@code Num} 值
     * @param expected the given {@code double} representation to compare the actual  value to
     *                 给定的 {@code double} 表示来比较实际值
     *
     * @throws AssertionError if the actual value is equal to the given    {@code double} representation
     * * @throws AssertionError 如果实际值等于给定的 {@code double} 表示
     */
    public static void assertNumNotEquals(double expected, Num actual) {
        assertNotEquals(expected, actual.doubleValue(), GENERAL_OFFSET);
    }

    /**
     * Verifies that two indicators have the same size and values
     * * 验证两个指标的大小和值是否相同
     * 
     * @param expected indicator of expected values
     *                 预期值指标
     *
     * @param actual   indicator of actual values
     *                 实际值指标
     */
    public static void assertIndicatorEquals(Indicator<Num> expected, Indicator<Num> actual, Num delta) {
        org.junit.Assert.assertEquals("Size does not match 尺寸不匹配,", expected.getBarSeries().getBarCount(),
                actual.getBarSeries().getBarCount());
        for (int i = expected.getBarSeries().getBeginIndex(); i < expected.getBarSeries().getEndIndex(); i++) {
            // convert to DecimalNum via String (auto-precision) avoids Cast Class Exception
            // 通过字符串（自动精度）转换为 DecimalNum 避免强制转换类异常
            Num exp = DecimalNum.valueOf(expected.getValue(i).toString());
            Num act = DecimalNum.valueOf(actual.getValue(i).toString());
            Num result = exp.minus(act).abs();
            if (result.isGreaterThan(delta)) {
                log.debug("{} expected does not match 预期不匹配", exp);
                log.debug("{} actual 实际的", act);
                log.debug("{} offset 抵消", delta);
                String expString = exp.toString();
                String actString = act.toString();
                int minLen = Math.min(expString.length(), actString.length());
                if (expString.length() > minLen)
                    expString = expString.substring(0, minLen) + "..";
                if (actString.length() > minLen)
                    actString = actString.substring(0, minLen) + "..";
                throw new AssertionError(
                        String.format("Failed at index 索引失败 %s: expected 预期的 %s but actual was 但实际是 %s", i, expString, actString));
            }
        }
    }

    /**
     * Verifies that two indicators have either different size or different values to an offset
     * * 验证两个指标是否具有不同的大小或不同的偏移值
     * 
     * @param expected indicator of expected values
     *                 预期值指标
     * @param actual   indicator of actual values
     *                 实际值指标
     * @param delta    num offset to which the indicators must be different
     *                 指标必须不同的 num 偏移量
     */
    public static void assertIndicatorNotEquals(Indicator<Num> expected, Indicator<Num> actual, Num delta) {
        if (expected.getBarSeries().getBarCount() != actual.getBarSeries().getBarCount()) {
            return;
        }
        for (int i = 0; i < expected.getBarSeries().getBarCount(); i++) {
            Num exp = DecimalNum.valueOf(expected.getValue(i).toString());
            Num act = DecimalNum.valueOf(actual.getValue(i).toString());
            Num result = exp.minus(act).abs();
            if (result.isGreaterThan(delta)) {
                return;
            }
        }
        throw new AssertionError("Indicators match to 指标匹配 " + delta);
    }

}
