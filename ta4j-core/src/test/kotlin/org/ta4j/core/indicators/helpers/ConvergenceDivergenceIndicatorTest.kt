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
package org.ta4j.core.indicators.helpers

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseBarSeries
import org.ta4j.core.Indicator
import org.ta4j.core.indicators.helpers.ConvergenceDivergenceIndicator.ConvergenceDivergenceStrictType
import org.ta4j.core.indicators.helpers.ConvergenceDivergenceIndicator.ConvergenceDivergenceType
import org.ta4j.core.num.Num

class ConvergenceDivergenceIndicatorTest {
    private var refPosCon: Indicator<Num>? = null
    private var otherPosCon: Indicator<Num>? = null
    private var refNegCon: Indicator<Num>? = null
    private var otherNegCon: Indicator<Num>? = null
    private var refPosDiv: Indicator<Num>? = null
    private var otherNegDiv: Indicator<Num>? = null
    private var refNegDig: Indicator<Num>? = null
    private var otherPosDiv: Indicator<Num>? = null
    private var isPosCon: ConvergenceDivergenceIndicator? = null
    private var isNegCon: ConvergenceDivergenceIndicator? = null
    private var isPosDiv: ConvergenceDivergenceIndicator? = null
    private var isNegDiv: ConvergenceDivergenceIndicator? = null
    private var isPosConStrict: ConvergenceDivergenceIndicator? = null
    private var isNegConStrict: ConvergenceDivergenceIndicator? = null
    private var isPosDivStrict: ConvergenceDivergenceIndicator? = null
    private var isNegDivStrict: ConvergenceDivergenceIndicator? = null
    @Before
    fun setUp() {
        val series: BarSeries = BaseBarSeries()
        refPosCon = FixedDecimalIndicator(series, 1.0, 2.0, 3.0, 4.0, 5.0, 8.0, 3.0, 2.0, -2.0, 1.0)
        otherPosCon = FixedDecimalIndicator(series, 10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 7.0, 5.0, 3.0, 2.0)
        refNegCon = FixedDecimalIndicator(series, 150.0, 60.0, 20.0, 10.0, -20.0, -60.0, -200.0, -1.0, -200.0, 100.0)
        otherNegCon = FixedDecimalIndicator(series, 80.0, 50.0, 40.0, 20.0, 10.0, 0.0, -30.0, -50.0, -150.0, 7.0)
        refPosDiv = FixedDecimalIndicator(series, 1.0, 4.0, 8.0, 12.0, 15.0, 20.0, 3.0, 2.0, -2.0, 1.0)
        otherNegDiv = FixedDecimalIndicator(series, 80.0, 50.0, 20.0, -10.0, 0.0, -100.0, -200.0, -2.0, 5.0, 7.0)
        refNegDig = FixedDecimalIndicator(series, 100.0, 30.0, 15.0, 4.0, 2.0, -10.0, -3.0, -100.0, -2.0, 100.0)
        otherPosDiv = FixedDecimalIndicator(series, 20.0, 40.0, 70.0, 80.0, 90.0, 100.0, 200.0, 220.0, -50.0, 7.0)

        // for convergence and divergence
        isPosCon = ConvergenceDivergenceIndicator(
            refPosCon!!, otherPosCon!!, 3,
            ConvergenceDivergenceType.positiveConvergent
        )
        isNegCon = ConvergenceDivergenceIndicator(
            refNegCon!!, otherNegCon!!, 3,
            ConvergenceDivergenceType.negativeConvergent
        )
        isPosDiv = ConvergenceDivergenceIndicator(
            refPosDiv!!, otherNegDiv!!, 3,
            ConvergenceDivergenceType.positiveDivergent
        )
        isNegDiv = ConvergenceDivergenceIndicator(
            refNegDig!!, otherPosDiv!!, 3,
            ConvergenceDivergenceType.negativeDivergent
        )

        // for strict convergence and divergence
        isPosConStrict = ConvergenceDivergenceIndicator(
            refPosCon!!, otherPosDiv!!, 3,
            ConvergenceDivergenceStrictType.positiveConvergentStrict
        )
        isNegConStrict = ConvergenceDivergenceIndicator(
            refNegDig!!, otherNegCon!!, 3,
            ConvergenceDivergenceStrictType.negativeConvergentStrict
        )
        isPosDivStrict = ConvergenceDivergenceIndicator(
            otherPosDiv!!, refNegDig!!, 3,
            ConvergenceDivergenceStrictType.positiveDivergentStrict
        )
        isNegDivStrict = ConvergenceDivergenceIndicator(
            refNegDig!!, otherPosDiv!!, 3,
            ConvergenceDivergenceStrictType.negativeDivergentStrict
        )
    }

    @Test
    fun isSatisfied() {
        testPositiveConvergent()
        testNegativeConvergent()
        testPositiveDivergent()
        testNegativeDivergent()

        // testPositiveConvergentStrict();
        // testNegativeConvergentStrict();
        // testPositiveDivergentStrict();
        // testNegativeDivergentStrict();
    }

    fun testPositiveConvergent() {
        Assert.assertFalse(isPosCon!![0])
        Assert.assertFalse(isPosCon!!.getValue(1))
        Assert.assertFalse(isPosCon!!.getValue(2))
        Assert.assertTrue(isPosCon!!.getValue(3))
        Assert.assertTrue(isPosCon!!.getValue(4))
        Assert.assertTrue(isPosCon!!.getValue(5))
        Assert.assertFalse(isPosCon!!.getValue(6))
        Assert.assertFalse(isPosCon!!.getValue(7))
        Assert.assertTrue(isPosCon!!.getValue(8))
        Assert.assertFalse(isPosCon!!.getValue(9))
    }

    fun testNegativeConvergent() {
        Assert.assertFalse(isNegCon!![0])
        Assert.assertFalse(isNegCon!!.getValue(1))
        Assert.assertFalse(isNegCon!!.getValue(2))
        Assert.assertTrue(isNegCon!!.getValue(3))
        Assert.assertFalse(isNegCon!!.getValue(4))
        Assert.assertFalse(isNegCon!!.getValue(5))
        Assert.assertFalse(isNegCon!!.getValue(6))
        Assert.assertFalse(isNegCon!!.getValue(7))
        Assert.assertFalse(isNegCon!!.getValue(8))
        Assert.assertFalse(isNegCon!!.getValue(9))
    }

    fun testPositiveDivergent() {
        Assert.assertFalse(isPosDiv!![0])
        Assert.assertFalse(isPosDiv!!.getValue(1))
        Assert.assertFalse(isPosDiv!!.getValue(2))
        Assert.assertTrue(isPosDiv!!.getValue(3))
        Assert.assertFalse(isPosDiv!!.getValue(4))
        Assert.assertTrue(isPosDiv!!.getValue(5))
        Assert.assertFalse(isPosDiv!!.getValue(6))
        Assert.assertFalse(isPosDiv!!.getValue(7))
        Assert.assertFalse(isPosDiv!!.getValue(8))
        Assert.assertFalse(isPosDiv!!.getValue(9))
    }

    fun testNegativeDivergent() {
        Assert.assertFalse(isNegDiv!![0])
        Assert.assertFalse(isNegDiv!!.getValue(1))
        Assert.assertFalse(isNegDiv!!.getValue(2))
        Assert.assertTrue(isNegDiv!!.getValue(3))
        Assert.assertTrue(isNegDiv!!.getValue(4))
        Assert.assertFalse(isNegDiv!!.getValue(5))
        Assert.assertFalse(isNegDiv!!.getValue(6))
        Assert.assertFalse(isNegDiv!!.getValue(7))
        Assert.assertFalse(isNegDiv!!.getValue(8))
        Assert.assertFalse(isNegDiv!!.getValue(9))
    }

    fun testPositiveConvergentStrict() {
        Assert.assertFalse(isPosConStrict!![0])
        Assert.assertFalse(isPosConStrict!!.getValue(1))
        Assert.assertFalse(isPosConStrict!!.getValue(2))
        Assert.assertTrue(isPosConStrict!!.getValue(3))
        Assert.assertTrue(isPosConStrict!!.getValue(4))
        Assert.assertTrue(isPosConStrict!!.getValue(5))
        Assert.assertFalse(isPosConStrict!!.getValue(6))
        Assert.assertFalse(isPosConStrict!!.getValue(7))
        Assert.assertTrue(isPosConStrict!!.getValue(8))
        Assert.assertFalse(isPosConStrict!!.getValue(9))
    }

    fun testNegativeConvergentStrict() {
        Assert.assertFalse(isNegConStrict!![0])
        Assert.assertFalse(isNegConStrict!!.getValue(1))
        Assert.assertFalse(isNegConStrict!!.getValue(2))
        Assert.assertTrue(isNegConStrict!!.getValue(3))
        Assert.assertFalse(isNegConStrict!!.getValue(4))
        Assert.assertFalse(isNegConStrict!!.getValue(5))
        Assert.assertFalse(isNegConStrict!!.getValue(6))
        Assert.assertFalse(isNegConStrict!!.getValue(7))
        Assert.assertFalse(isNegConStrict!!.getValue(8))
        Assert.assertFalse(isNegConStrict!!.getValue(9))
    }

    fun testPositiveDivergentStrict() {
        Assert.assertFalse(isPosDivStrict!![0])
        Assert.assertFalse(isPosDivStrict!!.getValue(1))
        Assert.assertFalse(isPosDivStrict!!.getValue(2))
        Assert.assertTrue(isPosDivStrict!!.getValue(3))
        Assert.assertFalse(isPosDivStrict!!.getValue(4))
        Assert.assertTrue(isPosDivStrict!!.getValue(5))
        Assert.assertFalse(isPosDivStrict!!.getValue(6))
        Assert.assertFalse(isPosDivStrict!!.getValue(7))
        Assert.assertFalse(isPosDivStrict!!.getValue(8))
        Assert.assertFalse(isPosDivStrict!!.getValue(9))
    }

    fun testNegativeDivergentStrict() {
        Assert.assertFalse(isNegDivStrict!![0])
        Assert.assertFalse(isNegDivStrict!!.getValue(1))
        Assert.assertFalse(isNegDivStrict!!.getValue(2))
        Assert.assertTrue(isNegDivStrict!!.getValue(3))
        Assert.assertTrue(isNegDivStrict!!.getValue(4))
        Assert.assertFalse(isNegDivStrict!!.getValue(5))
        Assert.assertFalse(isNegDivStrict!!.getValue(6))
        Assert.assertFalse(isNegDivStrict!!.getValue(7))
        Assert.assertFalse(isNegDivStrict!!.getValue(8))
        Assert.assertFalse(isNegDivStrict!!.getValue(9))
    }
}