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
package org.ta4j.core.indicators

import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.ta4j.core.Indicator
import org.ta4j.core.IndicatorFactory
import org.ta4j.core.num.DecimalNum
import org.ta4j.core.num.DoubleNum
import org.ta4j.core.num.Num
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.function.Function

/**
 * Abstract test class to extend BarSeries, Indicator an other test cases. The
 * extending class will be called twice. First time with
 * [DecimalNum.valueOf], second time with [DoubleNum.valueOf] as
 * `Function<Number></Number>, Num>`> parameter. This should ensure that the
 * defined test case is valid for both data types.
 *
 * @param <D> Data source of test object, needed for Excel-Sheet validation
 * (could be `Indicator<Num></Num>` or `BarSeries`, ...)
 * @param <I> The generic class of the test indicator (could be
 * `Num`, `Boolean`, ...)
</I></D> */
@RunWith(Parameterized::class)
abstract class AbstractIndicatorTest<D, I> {
    @JvmField
    val numFunction: Function<Number?, Num>
    private val factory: IndicatorFactory<D, I>?

    /**
     * Constructor.
     *
     * @param factory     IndicatorFactory for building an Indicator given data and
     * parameters.
     * @param numFunction the function to convert a Number into a Num implementation
     * (automatically inserted by Junit)
     */
    constructor(factory: IndicatorFactory<D, I>?, numFunction: Function<Number?, Num>) {
        this.numFunction = numFunction
        this.factory = factory
    }

    /**
     * Constructor
     *
     * @param numFunction the function to convert a Number into a Num implementation
     * (automatically inserted by Junit)
     */
    constructor(numFunction: Function<Number?, Num>) {
        this.numFunction = numFunction
        factory = null
    }

    /**
     * Generates an Indicator from data and parameters.
     *
     * @param data   indicator data
     * @param params indicator parameters
     * @return Indicator<I> from data given parameters
    </I> */
    fun getIndicator(data: D, vararg params: Any): Indicator<I> {
        assert(factory != null)
        return factory!!.getIndicator(data, *params)
    }

    protected fun numOf(n: Number?): Num {
        return numFunction.apply(n)
    }

    fun numOf(string: String?, precision: Int): Num {
        val mathContext = MathContext(precision, RoundingMode.HALF_UP)
        return this.numOf(BigDecimal(string, mathContext))
    }

    companion object {
        @Parameterized.Parameters(name = "Test Case: {index} (0=DoubleNum, 1=DecimalNum)")
        @JvmStatic
        fun function(): List<Function<Number, Num>> {
            return listOf(
                Function { obj: Number? -> DoubleNum.valueOf(obj) },
                Function { obj: Number? -> DecimalNum.valueOf(obj) })
        }
    }
}