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

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.function.Function

/**
 * Ta4js definition of operations that must be fulfilled by an object that
 * should be used as base for calculations
 *
 * @see Num
 *
 * @see Num.function
 * @see DoubleNum
 *
 * @see DecimalNum
 */
interface Num : Comparable<Num> {
    /**
     * @return the Num of 0
     */
    fun zero(): Num {
        return numOf(0)
    }

    /**
     * @return the Num of 1
     */
    fun one(): Num {
        return numOf(1)
    }

    /**
     * @return the Num of 100
     */
    fun hundred(): Num {
        return numOf(100)
    }

    /**
     * @return the delegate used from this `Num` implementation
     */
    val delegate: Number?

    /**
     * Returns the name/description of this Num implementation
     *
     * @return the name/description
     */
    val name: String

    /**
     * Returns a `num` whose value is `(this + augend)`,
     *
     * @param augend value to be added to this `num`.
     * @return `this + augend`, rounded as necessary
     */
    operator fun plus(augend: Num): Num

    /**
     * Returns a `num` whose value is `(this - augend)`,
     *
     * @param subtrahend value to be subtracted from this `num`.
     * @return `this - subtrahend`, rounded as necessary
     */
    operator fun minus(subtrahend: Num): Num

    /**
     * Returns a `num` whose value is `this * multiplicand`,
     *
     * @param multiplicand value to be multiplied by this `num`.
     * @return `this * multiplicand`, rounded as necessary
     */
    fun multipliedBy(multiplicand: Num): Num

    operator fun times(multiplicand: Num):Num =multipliedBy(multiplicand)

    /**
     * Returns a `num` whose value is `(this / divisor)`,
     *
     * @param divisor value by which this `num` is to be divided.
     * @return `this / divisor`, rounded as necessary
     */
    fun dividedBy(divisor: Num): Num

    operator fun div(divisor: Num): Num=dividedBy(divisor)

    /**
     * Returns a `num` whose value is `(this % divisor)`,
     *
     * @param divisor value by which this `num` is to be divided.
     * @return `this % divisor`, rounded as necessary.
     */
    fun remainder(divisor: Num): Num

    operator fun rem(divisor: Num): Num= remainder(divisor)

    /**
     * Returns a `Num` whose value is rounded down to the nearest whole
     * number.
     *
     * @return `this` to whole Num rounded down
     */
    fun floor(): Num

    /**
     * Returns a `Num` whose value is rounded up to the nearest whole number.
     *
     * @return `this` to whole Num rounded up
     */
    fun ceil(): Num

    /**
     * Returns a `num` whose value is `(this<sup>n</sup>)`.
     *
     * @param n power to raise this `num` to.
     * @return `this<sup>n</sup>`
     */
    fun pow(n: Int): Num

    /**
     * Returns a `num` whose value is `(this<sup>n</sup>)`.
     *
     * @param n power to raise this `num` to.
     * @return `this<sup>n</sup>`
     */
    fun pow(n: Num): Num

    /**
     * Returns a `num` whose value is `ln(this)`.
     *
     * @return `this<sup>n</sup>`
     */
    fun log(): Num

    /**
     * Returns a `num` whose value is `√(this)`.
     *
     * @return `this<sup>n</sup>`
     */
    fun sqrt(): Num

    /**
     * Returns a `num` whose value is `√(this)`.
     *
     * @param precision to calculate.
     * @return `this<sup>n</sup>`
     */
    fun sqrt(precision: Int): Num

    /**
     * Returns a `num` whose value is the absolute value of this `num`.
     *
     * @return `abs(this)`
     */
    fun abs(): Num

    /**
     * Returns a `num` whose value is (-this), and whose scale is
     * this.scale().
     *
     * @return `negate(this)`
     */
    fun negate(): Num

    /**
     * Checks if the value is zero.
     *
     * @return true if the value is zero, false otherwise
     */
    val isZero: Boolean

    /**
     * Checks if the value is greater than zero.
     *
     * @return true if the value is greater than zero, false otherwise
     */
    val isPositive: Boolean

    /**
     * Checks if the value is zero or greater.
     *
     * @return true if the value is zero or greater, false otherwise
     */
    val isPositiveOrZero: Boolean

    /**
     * Checks if the value is less than zero.
     *
     * @return true if the value is less than zero, false otherwise
     */
    val isNegative: Boolean

    /**
     * Checks if the value is zero or less.
     *
     * @return true if the value is zero or less, false otherwise
     */
    val isNegativeOrZero: Boolean

    /**
     * Checks if this value is equal to another.
     *
     * @param other the other value, not null
     * @return true if this is greater than the specified value, false otherwise
     */
    fun isEqual(other: Num?): Boolean

    /**
     * Checks if this value is greater than another.
     *
     * @param other the other value, not null
     * @return true if this is greater than the specified value, false otherwise
     */
    fun isGreaterThan(other: Num?): Boolean

    /**
     * Checks if this value is greater than or equal to another.
     *
     * @param other the other value, not null
     * @return true if this is greater than or equal to the specified value, false
     * otherwise
     */
    fun isGreaterThanOrEqual(other: Num?): Boolean

    /**
     * Checks if this value is less than another.
     *
     * @param other the other value, not null
     * @return true if this is less than the specified value, false otherwise
     */
    fun isLessThan(other: Num?): Boolean

    /**
     * Checks if this value is less than another.
     *
     * @param other the other value, not null
     * @return true if this is less than or equal the specified value, false
     * otherwise
     */
    fun isLessThanOrEqual(other: Num?): Boolean

    /**
     * Returns the minimum of this `num` and `other`.
     *
     * @param other value with which the minimum is to be computed
     * @return the `num` whose value is the lesser of this `num` and
     * `other`. If they are equal, method, `this` is returned.
     */
    fun min(other: Num): Num

    /**
     * Returns the maximum of this `num` and `other`.
     *
     * @param other value with which the maximum is to be computed
     * @return the `num` whose value is the greater of this `num` and
     * `other`. If they are equal, method, `this` is returned.
     */
    fun max(other: Num): Num

    /**
     * Returns the [Function] to convert a number instance into the
     * corresponding Num instance
     *
     * @return function which converts a number instance into the corresponding Num
     * instance
     */
    fun function(): Function<Number?, Num>

    /**
     * Transforms a [Number] into a new Num instance of this `Num`
     * implementation
     *
     * @param value the Number to transform
     * @return the corresponding Num implementation of the `value`
     */
    fun numOf(value: Number): Num {
        return function().apply(value)
    }

    /**
     * Transforms a [String] into a new Num instance of this with a precision
     * `Num` implementation
     *
     * @param value     the String to transform
     * @param precision the precision
     * @return the corresponding Num implementation of the `value`
     */
    fun numOf(value: String?, precision: Int): Num? {
        val mathContext = MathContext(precision, RoundingMode.HALF_UP)
        return this.numOf(BigDecimal(value, mathContext))
    }

    /**
     * Only for NaN this should be true
     *
     * @return false if this implementation is not NaN
     */
    val isNaN: Boolean
        get() = false

    /**
     * Converts this `num` to a `double`.
     *
     * @return this `num` converted to a `double`
     */
    fun doubleValue(): Double {
        return delegate!!.toDouble()
    }

    fun intValue(): Int {
        return delegate!!.toInt()
    }

    fun longValue(): Long {
        return delegate!!.toLong()
    }

    fun floatValue(): Float {
        return delegate!!.toFloat()
    }

    override fun hashCode(): Int
    override fun toString(): String

    /**
     * {@inheritDoc}
     */
    override fun equals(other: Any?): Boolean
}