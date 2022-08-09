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
import org.ta4j.core.indicators.AbstractIndicatorTest
import org.ta4j.core.indicators.helpers.BooleanTransformIndicator.BooleanTransformSimpleType
import org.ta4j.core.indicators.helpers.BooleanTransformIndicator.BooleanTransformType
import org.ta4j.core.num.Num
import java.util.function.Function

class BooleanTransformIndicatorTest(numFunction: Function<Number?, Num>) :
    AbstractIndicatorTest<Indicator<Boolean?>?, Num>(numFunction) {
    private lateinit var transEquals: BooleanTransformIndicator
    private lateinit var transIsGreaterThan: BooleanTransformIndicator
    private lateinit var transIsGreaterThanOrEqual: BooleanTransformIndicator
    private lateinit var transIsLessThan: BooleanTransformIndicator
    private lateinit var transIsLessThanOrEqual: BooleanTransformIndicator
    private lateinit var transIsNaN: BooleanTransformIndicator
    private lateinit var transIsNegative: BooleanTransformIndicator
    private lateinit var transIsNegativeOrZero: BooleanTransformIndicator
    private lateinit var transIsPositive: BooleanTransformIndicator
    private lateinit var transIsPositiveOrZero: BooleanTransformIndicator
    private lateinit var transIsZero: BooleanTransformIndicator
    @Before
    fun setUp() {
        val FOUR = numFunction.apply(4)
        val minusFOUR = numFunction.apply(-4)
        val series: BarSeries = BaseBarSeries()
        val constantIndicator = ConstantIndicator(series, FOUR)
        transEquals = BooleanTransformIndicator(constantIndicator, FOUR, BooleanTransformType.equals)
        transIsGreaterThan = BooleanTransformIndicator(
            constantIndicator, numFunction.apply(3),
            BooleanTransformType.isGreaterThan
        )
        transIsGreaterThanOrEqual = BooleanTransformIndicator(
            constantIndicator, FOUR,
            BooleanTransformType.isGreaterThanOrEqual
        )
        transIsLessThan = BooleanTransformIndicator(
            constantIndicator, numFunction.apply(10),
            BooleanTransformType.isLessThan
        )
        transIsLessThanOrEqual = BooleanTransformIndicator(
            constantIndicator, FOUR,
            BooleanTransformType.isLessThanOrEqual
        )
        transIsNaN = BooleanTransformIndicator(constantIndicator, BooleanTransformSimpleType.isNaN)
        transIsNegative = BooleanTransformIndicator(
            ConstantIndicator(series, minusFOUR),
            BooleanTransformSimpleType.isNegative
        )
        transIsNegativeOrZero = BooleanTransformIndicator(
            constantIndicator,
            BooleanTransformSimpleType.isNegativeOrZero
        )
        transIsPositive = BooleanTransformIndicator(constantIndicator, BooleanTransformSimpleType.isPositive)
        transIsPositiveOrZero = BooleanTransformIndicator(
            constantIndicator,
            BooleanTransformSimpleType.isPositiveOrZero
        )
        transIsZero = BooleanTransformIndicator(
            ConstantIndicator(series, numFunction.apply(0)),
            BooleanTransformSimpleType.isZero
        )
    }

    @Test
    fun getValue() {
        Assert.assertTrue(transEquals[0])
        Assert.assertTrue(transIsGreaterThan[0])
        Assert.assertTrue(transIsGreaterThanOrEqual[0])
        Assert.assertTrue(transIsLessThan[0])
        Assert.assertTrue(transIsLessThanOrEqual[0])
        Assert.assertFalse(transIsNaN[0])
        Assert.assertTrue(transIsNegative[0])
        Assert.assertFalse(transIsNegativeOrZero[0])
        Assert.assertTrue(transIsPositive[0])
        Assert.assertTrue(transIsPositiveOrZero[0])
        Assert.assertTrue(transIsZero[0])
    }
}