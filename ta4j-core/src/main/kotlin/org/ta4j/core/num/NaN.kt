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

import org.ta4j.core.num.NaN.Companion.NaN
import java.util.function.Function

/**
 * Representation of an undefined or unrepresentable value: NaN (not a number)
 * <br></br>
 * Special behavior in methods such as:
 *
 *  * [NaN.plus] => NaN
 *  * [NaN.isEqual] => true
 *  * [NaN.isPositive] => false
 *  * [NaN.isNegativeOrZero] => false
 *  * [NaN.min] => NaN
 *  * [NaN.max] => NaN
 *  * [NaN.doubleValue] => [Double.NaN]
 *  * [NaN.intValue] => throws
 * [UnsupportedOperationException]
 *
 */
class NaN private constructor() : Num {

    override fun compareTo(other: Num): Int {
        return 0
    }

    override fun intValue(): Int {
        throw UnsupportedOperationException("No NaN represantation for int")
    }

    override fun longValue(): Long {
        throw UnsupportedOperationException("No NaN represantation for long")
    }

    override fun floatValue(): Float {
        return Float.NaN
    }

    override fun doubleValue(): Double {
        return Double.NaN
    }

    override val delegate: Number?
        get() = null
    override val name: String
        get() = toString()

    override fun toString(): String {
        return "NaN"
    }

    override fun equals(other: Any?): Boolean {
        return other != null && other === NaN
    }

    override fun plus(augend: Num): Num {
        return this
    }

    override fun minus(subtrahend: Num): Num {
        return this
    }

    override fun multipliedBy(multiplicand: Num): Num {
        return this
    }

    override fun dividedBy(divisor: Num): Num {
        return this
    }

    override fun remainder(divisor: Num): Num {
        return this
    }

    override fun floor(): Num {
        return this
    }

    override fun ceil(): Num {
        return this
    }

    override fun pow(n: Int): Num {
        return this
    }

    override fun pow(n: Num): Num {
        return this
    }

    override fun log(): Num {
        return this
    }

    override fun sqrt(): Num {
        return this
    }

    override fun sqrt(precision: Int): Num {
        return this
    }

    override fun abs(): Num {
        return this
    }

    override fun negate(): Num {
        return this
    }

    override val isZero: Boolean
        get() = false
    override val isPositive: Boolean
        get() = false
    override val isPositiveOrZero: Boolean
        get() = false
    override val isNegative: Boolean
        get() = false
    override val isNegativeOrZero: Boolean
        get() = false

    /**
     * NaN.isEqual(NaN) -> true
     *
     * @param other the other value, not null
     * @return flase if both values are not NaN
     */
    override fun isEqual(other: Num?): Boolean {
        return other != null && other == NaN
    }

    override fun isGreaterThan(other: Num?): Boolean {
        return false
    }

    override fun isGreaterThanOrEqual(other: Num?): Boolean {
        return false
    }

    override fun isLessThan(other: Num?): Boolean {
        return false
    }

    override fun isLessThanOrEqual(other: Num?): Boolean {
        return false
    }

    override fun min(other: Num): Num {
        return this
    }

    override fun max(other: Num): Num {
        return this
    }

    override fun function(): Function<Number?, Num> {
        return Function { NaN }
    }

    override val isNaN: Boolean
        get() = true


    override fun hashCode(): Int =0


    companion object {
        /** static Not-a-Number instance  */
        @JvmField
        val NaN: Num = NaN()

        /**
         * Returns a `Num` version of the given `Number`. Warning: This
         * method turns the number into NaN.
         *
         * @param v the number
         * @return [.NaN]
         */
        @JvmStatic
        fun valueOf(v: Number?): Num {
            return NaN
        }
    }
}