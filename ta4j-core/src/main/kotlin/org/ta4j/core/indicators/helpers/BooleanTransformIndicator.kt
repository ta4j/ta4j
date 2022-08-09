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

import org.ta4j.core.Indicator
import org.ta4j.core.indicators.CachedIndicator
import org.ta4j.core.num.*

/**
 * Simple boolean transform indicator.
 *
 * Transforms any decimal indicator to a boolean indicator by using common
 * logical operators.
 */
class BooleanTransformIndicator : CachedIndicator<Boolean> {
    /**
     * Select the type for transformation.
     */
    enum class BooleanTransformType {
        /**
         * Transforms the decimal indicator to a boolean indicator by
         * indicator.equals(coefficient).
         */
        equals,

        /**
         * Transforms the decimal indicator to a boolean indicator by
         * indicator.isGreaterThan(coefficient).
         */
        isGreaterThan,

        /**
         * Transforms the decimal indicator to a boolean indicator by
         * indicator.isGreaterThanOrEqual(coefficient).
         */
        isGreaterThanOrEqual,

        /**
         * Transforms the decimal indicator to a boolean indicator by
         * indicator.isLessThan(coefficient).
         */
        isLessThan,

        /**
         * Transforms the decimal indicator to a boolean indicator by
         * indicator.isLessThanOrEqual(coefficient).
         */
        isLessThanOrEqual
    }

    /**
     * Select the type for transformation.
     */
    enum class BooleanTransformSimpleType {
        /**
         * Transforms the decimal indicator to a boolean indicator by indicator.isNaN().
         */
        isNaN,

        /**
         * Transforms the decimal indicator to a boolean indicator by
         * indicator.isNegative().
         */
        isNegative,

        /**
         * Transforms the decimal indicator to a boolean indicator by
         * indicator.isNegativeOrZero().
         */
        isNegativeOrZero,

        /**
         * Transforms the decimal indicator to a boolean indicator by
         * indicator.isPositive().
         */
        isPositive,

        /**
         * Transforms the decimal indicator to a boolean indicator by
         * indicator.isPositiveOrZero().
         */
        isPositiveOrZero,

        /**
         * Transforms the decimal indicator to a boolean indicator by
         * indicator.isZero().
         */
        isZero
    }

    private var indicator: Indicator<Num>
    private var coefficient: Num? = null
    private var type: BooleanTransformType? = null
    private var simpleType: BooleanTransformSimpleType? = null

    /**
     * Constructor.
     *
     * @param indicator   the indicator
     * @param coefficient the value for transformation
     * @param type        the type of the transformation
     */
    constructor(indicator: Indicator<Num>, coefficient: Num?, type: BooleanTransformType?) : super(indicator) {
        this.indicator = indicator
        this.coefficient = coefficient
        this.type = type
    }

    /**
     * Constructor.
     *
     * @param indicator the indicator
     * @param type      the type of the transformation
     */
    constructor(indicator: Indicator<Num>, type: BooleanTransformSimpleType?) : super(indicator) {
        this.indicator = indicator
        simpleType = type
    }

    override fun calculate(index: Int): Boolean {
        val v = indicator[index]
        if (type != null) {
            when (type) {
                BooleanTransformType.equals -> return v == coefficient
                BooleanTransformType.isGreaterThan -> return v.isGreaterThan(coefficient)
                BooleanTransformType.isGreaterThanOrEqual -> return v.isGreaterThanOrEqual(coefficient)
                BooleanTransformType.isLessThan -> return v.isLessThan(coefficient)
                BooleanTransformType.isLessThanOrEqual -> return v.isLessThanOrEqual(coefficient)
                else -> {}
            }
        } else if (simpleType != null) {
            when (simpleType) {
                BooleanTransformSimpleType.isNaN -> return v.isNaN
                BooleanTransformSimpleType.isNegative -> return v.isNegative
                BooleanTransformSimpleType.isNegativeOrZero -> return v.isNegativeOrZero
                BooleanTransformSimpleType.isPositive -> return v.isPositive
                BooleanTransformSimpleType.isPositiveOrZero -> return v.isPositiveOrZero
                BooleanTransformSimpleType.isZero -> return v.isZero
                else -> {}
            }
        }
        return false
    }

    override fun toString(): String {
        return if (type != null) {
            javaClass.simpleName + " Coefficient: " + coefficient + " Transform(" + type!!.name + ")"
        } else javaClass.simpleName + "Transform(" + simpleType!!.name + ")"
    }
}