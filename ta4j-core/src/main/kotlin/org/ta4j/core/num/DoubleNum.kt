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

import java.util.function.Function
import kotlin.math.ln
import kotlin.math.pow

/**
 * Representation of Double. High performance, lower precision.
 *
 * @apiNote the delegate should never become a NaN value. No self NaN checks
 * provided
 */
class DoubleNum private constructor(override val delegate: Double) : Num {
    override fun zero(): Num {
        return ZERO
    }

    override fun one(): Num {
        return ONE
    }

    override fun hundred(): Num {
        return HUNDRED
    }

    override fun function(): Function<Number?, Num> {
        return Function { i: Number? -> valueOf(i) }
    }

    override val name: String
        get() = this.javaClass.simpleName

    override fun plus(augend: Num): Num {
        return if (augend.isNaN) NaN.NaN else DoubleNum(delegate + (augend as DoubleNum).delegate)
    }

    override fun minus(subtrahend: Num): Num {
        return if (subtrahend.isNaN) NaN.NaN else DoubleNum(delegate - (subtrahend as DoubleNum).delegate)
    }

    override fun multipliedBy(multiplicand: Num): Num {
        return if (multiplicand.isNaN) NaN.NaN else DoubleNum(delegate * (multiplicand as DoubleNum).delegate)
    }

    override fun dividedBy(divisor: Num): Num {
        if (divisor.isNaN || divisor.isZero) {
            return NaN.NaN
        }
        val divisorD = divisor as DoubleNum
        return DoubleNum(delegate / divisorD.delegate)
    }

    override fun remainder(divisor: Num): Num {
        return if (divisor.isNaN) NaN.NaN else DoubleNum(delegate % (divisor as DoubleNum).delegate)
    }

    override fun floor(): Num {
        return DoubleNum(kotlin.math.floor(delegate))
    }

    override fun ceil(): Num {
        return DoubleNum(kotlin.math.ceil(delegate))
    }

    override fun pow(n: Int): Num {
        return DoubleNum(delegate.pow(n.toDouble()))
    }

    override fun pow(n: Num): Num {
        return DoubleNum(delegate.pow(n.doubleValue()))
    }

    override fun sqrt(): Num {
        return if (delegate < 0) {
            NaN.NaN
        } else DoubleNum(kotlin.math.sqrt(delegate))
    }

    override fun sqrt(precision: Int): Num {
        return sqrt()
    }

    override fun abs(): Num {
        return DoubleNum(kotlin.math.abs(delegate))
    }

    override fun negate(): Num {
        return DoubleNum(-delegate)
    }

    override val isZero: Boolean
        get() = delegate == 0.0
    override val isPositive: Boolean
        get() = delegate > 0
    override val isPositiveOrZero: Boolean
        get() = delegate >= 0
    override val isNegative: Boolean
        get() = delegate < 0
    override val isNegativeOrZero: Boolean
        get() = delegate <= 0

    override fun isEqual(other: Num?): Boolean {
        if (other==null) return false
        return !other.isNaN && delegate == (other as DoubleNum?)!!.delegate
    }

    override fun log(): Num {
        return if (delegate <= 0) {
            NaN.NaN
        } else DoubleNum(ln(delegate))
    }

    /**
     * Checks if this value is greater than another.
     *
     * @param other the other value, not null
     * @return true is this is greater than the specified value, false otherwise
     */
    override fun isGreaterThan(other: Num?): Boolean {
        if (other==null) return true
        return !other.isNaN && compareTo(other) > 0
    }

    /**
     * Checks if this value is greater than or equal to another.
     *
     * @param other the other value, not null
     * @return true is this is greater than or equal to the specified value, false
     * otherwise
     */
    override fun isGreaterThanOrEqual(other: Num?): Boolean {
        if (other==null) return true
        return !other.isNaN && compareTo(other) > -1
    }

    /**
     * Checks if this value is less than another.
     *
     * @param other the other value, not null
     * @return true is this is less than the specified value, false otherwise
     */
    override fun isLessThan(other: Num?): Boolean {
        if (other==null) return true
        return !other.isNaN && compareTo(other) < 0
    }

    override fun isLessThanOrEqual(other: Num?): Boolean {
        if (other==null) return true
        return !other.isNaN && compareTo(other) < 1
    }

    override fun min(other: Num): Num {
        return if (other.isNaN) NaN.NaN else DoubleNum(kotlin.math.min(delegate, (other as DoubleNum).delegate))
    }

    override fun max(other: Num): Num {
        return if (other.isNaN) NaN.NaN else DoubleNum(kotlin.math.max(delegate, (other as DoubleNum).delegate))
    }

    override fun hashCode(): Int {
        return delegate.hashCode()
    }

    override fun toString(): String {
        return delegate.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is DoubleNum) {
            return false
        }
        return kotlin.math.abs(delegate - other.delegate) < EPS
    }

    override fun compareTo(other: Num): Int {
        if (this === NaN.NaN || other === NaN.NaN) {
            return 0
        }
        val doubleNumO = other as DoubleNum
        return delegate.compareTo(doubleNumO.delegate)
    }

    companion object {
        private val ZERO = valueOf(0)
        private val ONE = valueOf(1)
        private val HUNDRED = valueOf(100)
        private const val EPS = 0.00001 // precision

        @JvmStatic
        fun valueOf(i: Int): DoubleNum {
            return DoubleNum(i.toDouble())
        }

        @JvmStatic
        fun valueOf(i: Long): DoubleNum {
            return DoubleNum(i.toDouble())
        }

        @JvmStatic
        fun valueOf(i: Short): DoubleNum {
            return DoubleNum(i.toDouble())
        }

        @JvmStatic
        fun valueOf(i: Float): DoubleNum {
            return DoubleNum(i.toDouble())
        }

        @JvmStatic
        fun valueOf(i: String): DoubleNum {
            return DoubleNum(i.toDouble())
        }

        @JvmStatic
        fun valueOf(i: Number?): DoubleNum {
            return DoubleNum(i!!.toDouble())
        }
    }
}