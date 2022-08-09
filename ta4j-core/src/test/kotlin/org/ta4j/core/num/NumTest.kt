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
package org.ta4j.core.num

import junit.framework.TestCase
import org.junit.Assert
import org.junit.Test
import org.ta4j.core.TestUtils
import org.ta4j.core.indicators.AbstractIndicatorTest
import org.ta4j.core.num.DecimalNum.Companion.valueOf
import java.io.IOException
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.*
import java.util.function.Function

class NumTest(numFunction: Function<Number?, Num>) : AbstractIndicatorTest<Any?, Num>(numFunction) {
    @Test
    fun testZero() {
        val anyNaNNum = NaN.NaN
        val anyDecimalNum: Num = valueOf(3)
        val anyDoubleNum: Num = DoubleNum.valueOf(3)
        TestUtils.assertNumEquals(NaN.NaN, anyNaNNum.zero())
        TestUtils.assertNumEquals(0, numOf(3).zero())
        TestUtils.assertNumEquals(0, anyDecimalNum.zero())
        TestUtils.assertNumEquals(0, anyDoubleNum.zero())
    }

    @Test
    fun testOne() {
        val anyNaNNum = NaN.NaN
        val anyDecimalNum: Num = valueOf(3)
        val anyDoubleNum: Num = DoubleNum.valueOf(3)
        TestUtils.assertNumEquals(NaN.NaN, anyNaNNum.one())
        TestUtils.assertNumEquals(1, numOf(3).one())
        TestUtils.assertNumEquals(1, anyDecimalNum.one())
        TestUtils.assertNumEquals(1, anyDoubleNum.one())
    }

    @Test
    fun testHundred() {
        val anyNaNNum = NaN.NaN
        val anyDecimalNum: Num = valueOf(3)
        val anyDoubleNum: Num = DoubleNum.valueOf(3)
        TestUtils.assertNumEquals(NaN.NaN, anyNaNNum.hundred())
        TestUtils.assertNumEquals(100, numOf(3).hundred())
        TestUtils.assertNumEquals(100, anyDecimalNum.hundred())
        TestUtils.assertNumEquals(100, anyDoubleNum.hundred())
    }

    @Test(expected = AssertionError::class)
    fun testStringNumFail() {
        TestUtils.assertNumEquals("1.234", numOf(4.321))
    }

    @Test
    fun testStringNumPass() {
        TestUtils.assertNumEquals("1.234", numOf(1.234))
    }

    @Test
    fun testDecimalNumPrecision() {
        val highPrecisionString =
            "1.928749238479283749238472398472936872364823749823749238749238749283749238472983749238749832749274"
        val num = numOf(highPrecisionString, HIGH_PRECISION)
        val highPrecisionNum: Num = valueOf(highPrecisionString, HIGH_PRECISION)
        Assert.assertTrue((highPrecisionNum as DecimalNum).matches(num, 17))
        val fromNum = BigDecimal(num.toString())
        if (num.javaClass == DoubleNum::class.java) {
            TestCase.assertEquals(17, fromNum.precision())
            Assert.assertTrue(highPrecisionNum.matches(num, 17))
            TestCase.assertFalse(highPrecisionNum.matches(num, 18))
        }
        if (num.javaClass == DecimalNum::class.java) {
            TestCase.assertEquals(97, fromNum.precision())
            // since precisions are the same, will match to any precision
            Assert.assertTrue(highPrecisionNum.matches(num, 10000))
        }
    }

    @Test
    fun testDecimalNumOffset() {
        val highPrecisionString =
            "1.928749238479283749238472398472936872364823749823749238749238749283749238472983749238749832749274"
        val num = numOf(highPrecisionString, HIGH_PRECISION)
        // upconvert num to PrecisionNum so that we don't throw ClassCastException in
        // minus() from
        // PrecisionNum.matches()
        val lowerPrecisionNum: Num = valueOf(num.toString(), 128)
        val highPrecisionNum: Num = valueOf(highPrecisionString, 128)
        // use HIGH_PRECISION PrecisionNums for delta because they are so small
        Assert.assertTrue(
            (highPrecisionNum as DecimalNum).matches(
                lowerPrecisionNum,
                highPrecisionNum.numOf("0.0000000000000001", HIGH_PRECISION)!!
            )
        )
        if (num.javaClass == DoubleNum::class.java) {
            Assert.assertTrue(
                highPrecisionNum.matches(
                    lowerPrecisionNum,
                    highPrecisionNum.numOf("0.0000000000000001", HIGH_PRECISION)!!
                )
            )
            TestCase.assertFalse(
                highPrecisionNum.matches(
                    lowerPrecisionNum,
                    highPrecisionNum.numOf("0.00000000000000001", HIGH_PRECISION)!!
                )
            )
        }
        if (num.javaClass == DecimalNum::class.java) {
            // since precisions are the same, will match to any precision
            Assert.assertTrue(
                highPrecisionNum.matches(
                    lowerPrecisionNum,
                    highPrecisionNum.numOf(
                        "0.0000000000000000000000000000000000000000000000000000000000000000000000000000000000001",
                        HIGH_PRECISION
                    )!!
                )
            )
        }
    }

    @Test
    fun testValueOf() {
        TestUtils.assertNumEquals(0.33333333333333333332, numOf(0.33333333333333333332))
        TestUtils.assertNumEquals(1, numOf(1.0))
        TestUtils.assertNumEquals(2.54, numOf(BigDecimal("2.54")))
        TestUtils.assertNumEquals(0.33, numOf(0.33))
        TestUtils.assertNumEquals(1, numOf(1))
        TestUtils.assertNumEquals(2.54, numOf(BigDecimal(2.54)))
    }

    @Test
    fun testMultiplicationSymmetrically() {
        val decimalFromString = numOf(BigDecimal("0.33"))
        val decimalFromDouble = numOf(45.33)
        TestCase.assertEquals(
            decimalFromString.times(decimalFromDouble),
            decimalFromDouble.times(decimalFromString)
        )
        val doubleNumFromString = numOf(BigDecimal("0.33"))
        val doubleNumFromDouble = numOf(10.33)
        TestUtils.assertNumEquals(
            doubleNumFromString.times(doubleNumFromDouble),
            doubleNumFromDouble.times(doubleNumFromString)
        )
    }

    @Test(expected = ClassCastException::class)
    fun testFailDifferentNumsAdd() {
        val a: Num = valueOf(12)
        val b: Num = DoubleNum.valueOf(12)
        a.plus(b)
    }

    @Test(expected = ClassCastException::class)
    fun testFailDifferentNumsCompare() {
        val a: Num = valueOf(12)
        val b: Num = DoubleNum.valueOf(13)
        a.isEqual(b)
    }

    @Test(expected = UnsupportedOperationException::class)
    fun testFailNaNtoInt() {
        NaN.NaN.intValue()
    }

    @Test(expected = UnsupportedOperationException::class)
    fun testFailNaNtoLong() {
        NaN.NaN.longValue()
    }

    @Test
    fun testNaN() {
        val a = NaN.NaN
        val eleven: Num = valueOf(11)
        var mustBeNaN = a.plus(eleven)
        TestUtils.assertNumEquals(mustBeNaN, NaN.NaN)
        mustBeNaN = a.minus(eleven)
        TestUtils.assertNumEquals(mustBeNaN, NaN.NaN)
        mustBeNaN = a.div(a)
        TestUtils.assertNumEquals(mustBeNaN, NaN.NaN)
        mustBeNaN = a.times(NaN.NaN)
        TestUtils.assertNumEquals(mustBeNaN, NaN.NaN)
        mustBeNaN = a.max(eleven)
        TestUtils.assertNumEquals(mustBeNaN, NaN.NaN)
        mustBeNaN = eleven.min(a)
        TestUtils.assertNumEquals(mustBeNaN, NaN.NaN)
        mustBeNaN = a.pow(12)
        TestUtils.assertNumEquals(mustBeNaN, NaN.NaN)
        mustBeNaN = a.pow(a)
        TestUtils.assertNumEquals(mustBeNaN, NaN.NaN)
        val nanDouble = a.doubleValue()
        TestCase.assertEquals(Double.NaN, nanDouble)
        val nanFloat = a.floatValue()
        TestCase.assertEquals(Float.NaN, nanFloat)
        Assert.assertTrue(NaN.NaN == a) // NaN == NaN -> true
    }

    @Test
    fun testArithmetic() {
        val ten = numOf(10)
        val million = numOf(1000000)
        TestUtils.assertNumEquals(10, ten)
        TestUtils.assertNumEquals("1000000.0", million)
        val zero = ten.minus(ten)
        TestUtils.assertNumEquals(0, zero)
        val hundred = ten.times(ten)
        TestUtils.assertNumEquals(100, hundred)
        val hundredMillion = hundred.times(million)
        TestUtils.assertNumEquals(100000000, hundredMillion)
        TestUtils.assertNumEquals(hundredMillion.div(hundred), million)
        TestUtils.assertNumEquals(0, hundredMillion.remainder(hundred))
        val five = ten.numOf(5) // generate new value with NumFunction
        val zeroDotTwo = ten.numOf(0.2) // generate new value with NumFunction
        val fiveHundred54 = ten.numOf(554) // generate new value with NumFunction
        TestUtils.assertNumEquals(0, hundredMillion.remainder(five))
        TestUtils.assertNumEquals(0.00032, zeroDotTwo.pow(5))
        TestUtils.assertNumEquals(0.7247796636776955, zeroDotTwo.pow(zeroDotTwo))
        TestUtils.assertNumEquals(1.37972966146, zeroDotTwo.pow(numOf(-0.2)))
        TestUtils.assertNumEquals(554, fiveHundred54.max(five))
        TestUtils.assertNumEquals(5, fiveHundred54.min(five))
        Assert.assertTrue(fiveHundred54.isGreaterThan(five))
        TestCase.assertFalse(five.isGreaterThan(five.function().apply(5)))
        TestCase.assertFalse(five.isGreaterThanOrEqual(fiveHundred54))
        TestCase.assertFalse(five.isGreaterThanOrEqual(five.function().apply(6)))
        Assert.assertTrue(five.isGreaterThanOrEqual(five.function().apply(5)))
        Assert.assertTrue(five == five.function().apply(5))
        Assert.assertTrue(five == five.function().apply(5.0))
        Assert.assertTrue(five == five.function().apply(5f))
        Assert.assertTrue(five == five.function().apply(5.toShort()))
        TestCase.assertFalse(five == five.function().apply(4.9))
        TestCase.assertFalse(five == five.function().apply(6))
        TestCase.assertFalse(five == five.function().apply(15f))
        TestCase.assertFalse(five == five.function().apply(45.toShort()))
    }

    @Test
    fun sqrtOfBigInteger() {
        val sqrtOfTwo = ("1.4142135623730950488016887242096980785696718753769480731"
                + "766797379907324784621070388503875343276415727350138462309122970249248360"
                + "558507372126441214970999358314132226659275055927557999505011527820605715")
        val precision = 200
        TestUtils.assertNumEquals(sqrtOfTwo, numOf(2).sqrt(precision))
    }

    @Test
    fun sqrtOfBigDouble() {
        val sqrtOfOnePointTwo =
            "1.095445115010332226913939565601604267905489389995966508453788899464986554245445467601716872327741252"
        val precision = 100
        TestUtils.assertNumEquals(sqrtOfOnePointTwo, numOf(1.2).sqrt(precision))
    }

    @Test
    fun sqrtOfNegativeDouble() {
        Assert.assertTrue(numOf(-1.2).sqrt(12).isNaN)
        Assert.assertTrue(numOf(-1.2).sqrt().isNaN)
    }

    @Test
    fun sqrtOfZero() {
        TestUtils.assertNumEquals(0, numOf(0).sqrt(12))
        TestUtils.assertNumEquals(0, numOf(0).sqrt())
    }

    @Test
    fun sqrtLudicrousPrecision() {
        val numBD = BigDecimal.valueOf(Double.MAX_VALUE)
            .multiply(BigDecimal.valueOf(Double.MAX_VALUE).add(BigDecimal.ONE))
        val sqrt = numOf(numBD).sqrt(100000)
        if (numOf(0).javaClass == DoubleNum::class.java) {
            TestCase.assertEquals("Infinity", sqrt.toString())
            /*
             * BigDecimalNum has been replaced by PrecisionNum
             * 
             * } else if (numOf(0).getClass().equals(BigDecimalNum.class)) {
             * assertNumEquals("1.7976931348623157000000000000000E+308", sqrt);
             * assertNumNotEquals("1.7976931348623157000000000000001E+308", sqrt);
             * assertNumEquals(Double.MAX_VALUE, sqrt);
             * assertNumEquals(numOf(Double.MAX_VALUE), sqrt); BigDecimal sqrtBD = new
             * BigDecimal(sqrt.toString()); assertNumEquals(numOf(numBD),
             * numOf(sqrtBD.multiply(sqrtBD, new MathContext(99999,
             * RoundingMode.HALF_UP)))); assertNumEquals(numOf(numBD),
             * sqrt.times(sqrt));
             */
        } else if (numOf(0).javaClass == DecimalNum::class.java) {
            val props = Properties()
            try {
                javaClass.getResourceAsStream("numTest.properties").use { `is` ->
                    props.load(`is`)
                    TestUtils.assertNumEquals(props.getProperty("sqrtCorrect100000"), sqrt)
                    TestUtils.assertNumNotEquals(props.getProperty("sqrtCorrect99999"), sqrt)
                    TestUtils.assertNumEquals(Double.MAX_VALUE, sqrt)
                    TestUtils.assertNumNotEquals(numOf(Double.MAX_VALUE), sqrt)
                    val sqrtBD = BigDecimal(sqrt.toString())
                    TestUtils.assertNumEquals(
                        numOf(numBD),
                        numOf(sqrtBD.multiply(sqrtBD, MathContext(99999, RoundingMode.HALF_UP)))
                    )
                    TestUtils.assertNumNotEquals(numOf(numBD), sqrt.times(sqrt))
                }
            } catch (ioe: IOException) {
                ioe.printStackTrace()
            }
        }
    }

    @Test
    fun sqrtOddExponent() {
        val numBD = BigDecimal.valueOf(java.lang.Double.valueOf("3E11"))
        val sqrt = numOf(numBD).sqrt()
        TestUtils.assertNumEquals("547722.55750516611345696978280080", sqrt)
    }

    companion object {
        const val HIGH_PRECISION = 128
    }
}