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
package org.ta4j.core.indicators.numeric

import org.junit.Assert
import org.junit.Test
import org.ta4j.core.BarSeries
import org.ta4j.core.Indicator
import org.ta4j.core.TestUtils
import org.ta4j.core.indicators.AbstractIndicatorTest
import org.ta4j.core.indicators.EMAIndicator
import org.ta4j.core.indicators.SMAIndicator
import org.ta4j.core.indicators.adx.ADXIndicator
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.indicators.helpers.HighestValueIndicator
import org.ta4j.core.indicators.helpers.LowestValueIndicator
import org.ta4j.core.indicators.helpers.VolumeIndicator
import org.ta4j.core.indicators.numeric.NumericIndicator.Companion.closePrice
import org.ta4j.core.indicators.numeric.NumericIndicator.Companion.of
import org.ta4j.core.indicators.numeric.NumericIndicator.Companion.volume
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.NaN
import org.ta4j.core.num.Num
import org.ta4j.core.rules.CrossedDownIndicatorRule
import org.ta4j.core.rules.CrossedUpIndicatorRule
import org.ta4j.core.rules.OverIndicatorRule
import org.ta4j.core.rules.UnderIndicatorRule
import java.util.function.Function

class NumericIndicatorTest(numFunction: Function<Number?, Num>) :
    AbstractIndicatorTest<NumericIndicator?, Num>(numFunction) {
    private val series: BarSeries = MockBarSeries(
        numFunction, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 8.0, 7.0, 6.0, 5.0, 4.0, 3.0, 2.0, 1.0,
        0.0, -1.0, -2.0
    )
    private val cp1 = ClosePriceIndicator(series)
    private val ema = EMAIndicator(cp1, 3)
    @Test
    fun plus() {
        val numericIndicator = of(cp1)
        val staticOp = numericIndicator.plus(5)
        TestUtils.assertNumEquals(1 + 5, staticOp[0])
        TestUtils.assertNumEquals(9 + 5, staticOp.getValue(8))
        val dynamicOp = numericIndicator.plus(ema)
        TestUtils.assertNumEquals(cp1[0].plus(ema[0]), dynamicOp[0])
        TestUtils.assertNumEquals(cp1.getValue(8).plus(ema.getValue(8)), dynamicOp.getValue(8))
    }

    @Test
    fun minus() {
        val numericIndicator = of(cp1)
        val staticOp = numericIndicator.minus(5)
        TestUtils.assertNumEquals(1 - 5, staticOp[0])
        TestUtils.assertNumEquals(9 - 5, staticOp.getValue(8))
        val dynamicOp = numericIndicator.minus(ema)
        TestUtils.assertNumEquals(cp1[0].minus(ema[0]), dynamicOp[0])
        TestUtils.assertNumEquals(cp1.getValue(8).minus(ema.getValue(8)), dynamicOp.getValue(8))
    }

    @Test
    fun multipliedBy() {
        val numericIndicator = of(cp1)
        val staticOp = numericIndicator.times(5)
        TestUtils.assertNumEquals(1 * 5, staticOp[0])
        TestUtils.assertNumEquals(9 * 5, staticOp.getValue(8))
        val dynamicOp = numericIndicator.times(ema)
        TestUtils.assertNumEquals(cp1[0].times(ema[0]), dynamicOp[0])
        TestUtils.assertNumEquals(cp1.getValue(8).times(ema.getValue(8)), dynamicOp.getValue(8))
    }

    @Test
    fun dividedBy() {
        val numericIndicator = of(cp1)
        val staticOp = numericIndicator.div(5)
        TestUtils.assertNumEquals(1 / 5.0, staticOp[0])
        TestUtils.assertNumEquals(9 / 5.0, staticOp.getValue(8))
        val zeroOp = numericIndicator.div(0)
        TestUtils.assertNumEquals(NaN.NaN, zeroOp[0])
        TestUtils.assertNumEquals(NaN.NaN, zeroOp.getValue(8))
        val dynamicOp = numericIndicator.div(ema)
        TestUtils.assertNumEquals(cp1[0].div(ema[0]), dynamicOp[0])
        TestUtils.assertNumEquals(cp1.getValue(8).div(ema.getValue(8)), dynamicOp.getValue(8))
    }

    @Test
    fun max() {
        val numericIndicator = of(cp1)
        val staticOp = numericIndicator.max(5)
        TestUtils.assertNumEquals(5, staticOp[0])
        TestUtils.assertNumEquals(9, staticOp.getValue(8))
        val dynamicOp = numericIndicator.max(ema)
        TestUtils.assertNumEquals(cp1[0].max(ema[0]), dynamicOp[0])
        TestUtils.assertNumEquals(cp1.getValue(8).max(ema.getValue(8)), dynamicOp.getValue(8))
    }

    @Test
    fun min() {
        val numericIndicator = of(cp1)
        val staticOp = numericIndicator.min(5)
        TestUtils.assertNumEquals(1, staticOp[0])
        TestUtils.assertNumEquals(5, staticOp.getValue(8))
        val dynamicOp = numericIndicator.min(ema)
        TestUtils.assertNumEquals(cp1[0].min(ema[0]), dynamicOp[0])
        TestUtils.assertNumEquals(cp1.getValue(8).min(ema.getValue(8)), dynamicOp.getValue(8))
    }

    @Test
    fun sqrt() {
        val numericIndicator = of(cp1)
        val dynamicOp = numericIndicator.sqrt()
        TestUtils.assertNumEquals(1, dynamicOp[0])
        TestUtils.assertNumEquals(Math.sqrt(2.0), dynamicOp.getValue(1))
        TestUtils.assertNumEquals(3, dynamicOp.getValue(8))
    }

    @Test
    fun abs() {
        val numericIndicator = of(cp1)
        val dynamicOp = numericIndicator.abs()
        TestUtils.assertNumEquals(1, dynamicOp[0])
        TestUtils.assertNumEquals(2, dynamicOp.getValue(series.barCount - 1))
    }

    @Test
    fun squared() {
        val numericIndicator = of(cp1)
        val dynamicOp = numericIndicator.squared()
        TestUtils.assertNumEquals(1, dynamicOp[0])
        TestUtils.assertNumEquals(81, dynamicOp.getValue(8))
    }

    @Test
    fun indicators() {
        val numericIndicator = of(cp1)
        Assert.assertEquals(SMAIndicator::class.java, numericIndicator.sma(5).delegate().javaClass)
        Assert.assertEquals(EMAIndicator::class.java, numericIndicator.ema(5).delegate().javaClass)
        Assert.assertEquals(StandardDeviationIndicator::class.java, numericIndicator.stddev(5).delegate().javaClass)
        Assert.assertEquals(HighestValueIndicator::class.java, numericIndicator.highest(5).delegate().javaClass)
        Assert.assertEquals(LowestValueIndicator::class.java, numericIndicator.lowest(5).delegate().javaClass)
        Assert.assertEquals(ClosePriceIndicator::class.java, closePrice(series).delegate().javaClass)
        Assert.assertEquals(VolumeIndicator::class.java, volume(series).delegate().javaClass)
        val adx = ADXIndicator(series, 5)
        Assert.assertEquals(ADXIndicator::class.java, of(adx).delegate().javaClass)
    }

    @Test
    fun rules() {
        val numericIndicator = of(cp1)
        Assert.assertEquals(CrossedUpIndicatorRule::class.java, numericIndicator.crossedOver(5).javaClass)
        Assert.assertEquals(CrossedUpIndicatorRule::class.java, numericIndicator.crossedOver(ema).javaClass)
        Assert.assertEquals(CrossedDownIndicatorRule::class.java, numericIndicator.crossedUnder(5).javaClass)
        Assert.assertEquals(CrossedDownIndicatorRule::class.java, numericIndicator.crossedUnder(ema).javaClass)
        Assert.assertEquals(OverIndicatorRule::class.java, numericIndicator.isGreaterThan(5).javaClass)
        Assert.assertEquals(OverIndicatorRule::class.java, numericIndicator.isGreaterThan(ema).javaClass)
        Assert.assertEquals(UnderIndicatorRule::class.java, numericIndicator.isLessThan(5).javaClass)
        Assert.assertEquals(UnderIndicatorRule::class.java, numericIndicator.isLessThan(ema).javaClass)
    }

    @Test
    fun previous() {
        val numericIndicator = of(cp1)
        val previous = numericIndicator.previous()
        TestUtils.assertNumEquals(cp1[0], previous.getValue(1))
        val previous3: Indicator<Num> = numericIndicator.previous(3)
        TestUtils.assertNumEquals(cp1.getValue(3), previous3.getValue(6))
    }

    @Test
    fun getValue() {
        val numericIndicator = of(cp1)
        for (i in 0 until series.barCount) {
            TestUtils.assertNumEquals(cp1[i], numericIndicator[i])
        }
    }

    @Test
    fun barSeries() {
        val numericIndicator = of(cp1)
        Assert.assertEquals(cp1.barSeries, numericIndicator.barSeries)
    }

    @Test
    fun numOf() {
        val numericIndicator = of(cp1)
        TestUtils.assertNumEquals(cp1.numOf(0), numericIndicator.numOf(0))
    }
}