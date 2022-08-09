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

import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.ParseException
import java.util.*
import java.util.function.Function
import kotlin.math.pow

/**
 * Representation of arbitrary precision BigDecimal. A `Num` consists of a
 * `BigDecimal` with arbitrary [MathContext] (precision and rounding
 * mode).
 *
 * @see BigDecimal
 *
 * @see MathContext
 *
 * @see RoundingMode
 *
 * @see Num
 */
class DecimalNum : Num {
    /**
     * Returns the underlying [MathContext] mathContext
     *
     * @return MathContext of this instance
     */
    val mathContext: MathContext

    override val delegate: BigDecimal

    //     val delegate= BigDecimal.ZERO

    /**
     * Constructor.
     *
     * @param val the string representation of the Num value
     */
    private constructor(`val`: String) {
        delegate = BigDecimal(`val`)
        val precision = Math.max(delegate.precision(), DEFAULT_PRECISION)
        mathContext = MathContext(precision, RoundingMode.HALF_UP)
    }

    /**
     * Constructor. Above double precision, only String parameters can represent the
     * value.
     *
     * @param val       the string representation of the Num value
     * @param precision the int precision of the Num value
     */
    private constructor(`val`: String, precision: Int) {
        mathContext = MathContext(precision, RoundingMode.HALF_UP)
        delegate = BigDecimal(`val`, MathContext(precision, RoundingMode.HALF_UP))
    }

    private constructor(`val`: Short) {
        mathContext = MathContext(DEFAULT_PRECISION, RoundingMode.HALF_UP)
        delegate = BigDecimal(`val`.toInt(), mathContext)
    }

    private constructor(`val`: Int) {
        mathContext = MathContext(DEFAULT_PRECISION, RoundingMode.HALF_UP)
        delegate = BigDecimal.valueOf(`val`.toLong())
    }

    private constructor(`val`: Long) {
        mathContext = MathContext(DEFAULT_PRECISION, RoundingMode.HALF_UP)
        delegate = BigDecimal.valueOf(`val`)
    }

    private constructor(`val`: Float) {
        mathContext = MathContext(DEFAULT_PRECISION, RoundingMode.HALF_UP)
        delegate = BigDecimal(`val`.toDouble(), mathContext)
    }

    private constructor(`val`: Double) {
        mathContext = MathContext(DEFAULT_PRECISION, RoundingMode.HALF_UP)
        delegate = BigDecimal.valueOf(`val`)
    }

    private constructor(`val`: BigDecimal, precision: Int) {
        mathContext = MathContext(precision, RoundingMode.HALF_UP)
        delegate = Objects.requireNonNull(`val`)
    }

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
        return Function { number: Number? -> valueOf(number?.toString()?:"", mathContext.precision) }
    }

    override val name: String
        get() = this.javaClass.simpleName

    override fun plus(augend: Num): Num {
        if (augend.isNaN) {
            return NaN.NaN
        }
        val bigDecimal = (augend as DecimalNum?)!!.delegate
        val precision = mathContext.precision
        val result = delegate.add(bigDecimal, mathContext)
        return DecimalNum(result, precision)
    }

    /**
     * Returns a `Num` whose value is `(this - augend)`, with rounding
     * according to the context settings.
     *
     * @param subtrahend value to be subtracted from this `Num`.
     * @return `this - subtrahend`, rounded as necessary
     * @see BigDecimal.subtract
     */
    override fun minus(subtrahend: Num): Num {
        if (subtrahend.isNaN) {
            return NaN.NaN
        }
        val bigDecimal = (subtrahend as DecimalNum?)!!.delegate
        val precision = mathContext.precision
        val result = delegate.subtract(bigDecimal, mathContext)
        return DecimalNum(result, precision)
    }

    /**
     * Returns a `Num` whose value is `this * multiplicand`, with
     * rounding according to the context settings.
     *
     * @param multiplicand value to be multiplied by this `Num`.
     * @return `this * multiplicand`, rounded as necessary
     * @see BigDecimal.multiply
     */
    override fun multipliedBy(multiplicand: Num): Num {
        if (multiplicand.isNaN) {
            return NaN.Companion.NaN
        }
        val bigDecimal = (multiplicand as DecimalNum?)!!.delegate
        val precision = mathContext.precision
        val result = delegate.multiply(bigDecimal, MathContext(precision, RoundingMode.HALF_UP))
        return DecimalNum(result, precision)
    }

    /**
     * Returns a `Num` whose value is `(this / divisor)`, with rounding
     * according to the context settings.
     *
     * @param divisor value by which this `Num` is to be divided.
     * @return `this / divisor`, rounded as necessary
     * @see BigDecimal.divide
     */
    override fun dividedBy(divisor: Num): Num {
        if (divisor.isNaN || divisor.isZero) {
            return NaN.NaN
        }
        val bigDecimal = (divisor as DecimalNum?)!!.delegate
        val precision = mathContext.precision
        val result = delegate.divide(bigDecimal, MathContext(precision, RoundingMode.HALF_UP))
        return DecimalNum(result, precision)
    }

    /**
     * Returns a `Num` whose value is `(this % divisor)`, with rounding
     * according to the context settings.
     *
     * @param divisor value by which this `Num` is to be divided.
     * @return `this % divisor`, rounded as necessary.
     * @see BigDecimal.remainder
     */
    override fun remainder(divisor: Num): Num {
        if (divisor.isNaN) {
            return NaN.Companion.NaN
        }
        val bigDecimal = (divisor as DecimalNum).delegate
        val precision = mathContext.precision
        val result = delegate.remainder(bigDecimal, MathContext(precision, RoundingMode.HALF_UP))
        return DecimalNum(result, precision)
    }

    /**
     * Returns a `Num` whose value is rounded down to the nearest whole
     * number.
     *
     * @return `this` to whole Num rounded down
     */
    override fun floor(): Num {
        val precision = Math.max(mathContext.precision, DEFAULT_PRECISION)
        return DecimalNum(delegate.setScale(0, RoundingMode.FLOOR), precision)
    }

    /**
     * Returns a `Num` whose value is rounded up to the nearest whole number.
     *
     * @return `this` to whole Num rounded up
     */
    override fun ceil(): Num {
        val precision = Math.max(mathContext.precision, DEFAULT_PRECISION)
        return DecimalNum(delegate.setScale(0, RoundingMode.CEILING), precision)
    }

    /**
     * Returns a `Num` whose value is `(this<sup>n</sup>)`.
     *
     * @param n power to raise this `Num` to.
     * @return `this<sup>n</sup>`
     * @see BigDecimal.pow
     */
    override fun pow(n: Int): Num {
        val precision = mathContext.precision
        val result = delegate.pow(n, MathContext(precision, RoundingMode.HALF_UP))
        return DecimalNum(result, precision)
    }

    /**
     * Returns the correctly rounded positive square root of this `Num`. /!\
     * Warning! Uses DEFAULT_PRECISION.
     *
     * @return the positive square root of `this`
     * @see DecimalNum.sqrt
     */
    override fun sqrt(): Num {
        return sqrt(DEFAULT_PRECISION)
    }

    /**
     * Returns a `num` whose value is `√(this)`.
     *
     * @param precision to calculate.
     * @return `this<sup>n</sup>`
     */
    override fun sqrt(precision: Int): Num {
        log.trace("delegate {}", delegate)
        val comparedToZero = delegate.compareTo(BigDecimal.ZERO)
        when (comparedToZero) {
            -1 -> return NaN.Companion.NaN
            0 -> return valueOf(0)
        }

        // Direct implementation of the example in:
        // https://en.wikipedia.org/wiki/Methods_of_computing_square_roots#Babylonian_method
        val precisionContext = MathContext(precision, RoundingMode.HALF_UP)
        var estimate = BigDecimal(delegate.toString(), precisionContext)
        val string = String.format(Locale.ROOT, "%1.1e", estimate)
        log.trace("scientific notation {}", string)
        if (string.contains("e")) {
            val parts = string.split("e".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            var mantissa = BigDecimal(parts[0])
            var exponent = BigDecimal(parts[1])
            if (exponent.remainder(BigDecimal(2)).compareTo(BigDecimal.ZERO) > 0) {
                exponent = exponent.subtract(BigDecimal.ONE)
                mantissa = mantissa.multiply(BigDecimal.TEN)
                log.trace("modified notatation {}e{}", mantissa, exponent)
            }
            val estimatedMantissa = if (mantissa.compareTo(BigDecimal.TEN) < 0) BigDecimal(2) else BigDecimal(6)
            val estimatedExponent = exponent.divide(BigDecimal(2))
            val estimateString = String.format("%sE%s", estimatedMantissa, estimatedExponent)
            if (log.isTraceEnabled) {
                log.trace("x[0] =~ sqrt({}...*10^{}) =~ {}", mantissa, exponent, estimateString)
            }
            val format = DecimalFormat()
            format.isParseBigDecimal = true
            try {
                estimate = format.parse(estimateString) as BigDecimal
            } catch (e: ParseException) {
                log.error("PrecicionNum ParseException:", e)
            }
        }
        var delta: BigDecimal
        var test: BigDecimal?
        var sum: BigDecimal
        var newEstimate: BigDecimal
        val two = BigDecimal(2)
        var estimateString: String
        var endIndex: Int
        var frontEndIndex: Int
        var backStartIndex: Int
        var i = 1
        do {
            test = delegate.divide(estimate, precisionContext)
            sum = estimate.add(test)
            newEstimate = sum.divide(two, precisionContext)
            delta = newEstimate.subtract(estimate).abs()
            estimate = newEstimate
            if (log.isTraceEnabled) {
                estimateString = String.format("%1." + precision + "e", estimate)
                endIndex = estimateString.length
                frontEndIndex = if (20 > endIndex) endIndex else 20
                backStartIndex = if (20 > endIndex) 0 else endIndex - 20
                log.trace(
                    "x[{}] = {}..{}, delta = {}", i, estimateString.substring(0, frontEndIndex),
                    estimateString.substring(backStartIndex, endIndex), String.format("%1.1e", delta)
                )
                i++
            }
        } while (delta.compareTo(BigDecimal.ZERO) > 0)
        return valueOf(estimate, precision)
    }

    /**
     * Returns a `Num` whose value is the natural logarithm of this
     * `Num`.
     *
     * @return `log(this)`
     */
    override fun log(): Num {
        // Algorithm: http://functions.wolfram.com/ElementaryFunctions/Log/10/
        // https://stackoverflow.com/a/6169691/6444586
        val logx: Num
        if (isNegativeOrZero) {
            return NaN.NaN
        }
        if (delegate == BigDecimal.ONE) {
            logx = valueOf(BigDecimal.ZERO, mathContext.precision)
        } else {
            val ITER: Long = 1000
            val x = delegate.subtract(BigDecimal.ONE)
            var ret = BigDecimal(ITER + 1)
            for (i in ITER downTo 0) {
                var N = BigDecimal(i / 2 + 1).pow(2)
                N = N.multiply(x, mathContext)
                ret = N.divide(ret, mathContext)
                N = BigDecimal(i + 1)
                ret = ret.add(N, mathContext)
            }
            ret = x.divide(ret, mathContext)
            logx = valueOf(ret, mathContext.precision)
        }
        return logx
    }

    /**
     * Returns a `Num` whose value is the absolute value of this `Num`.
     *
     * @return `abs(this)`
     */
    override fun abs(): Num {
        return DecimalNum(delegate.abs(), mathContext.precision)
    }

    /**
     * Returns a `num` whose value is (-this), and whose scale is
     * this.scale().
     *
     * @return `negate(this)`
     */
    override fun negate(): Num {
        return DecimalNum(delegate.negate(), mathContext.precision)
    }

    /**
     * Checks if the value is zero.
     *
     * @return true if the value is zero, false otherwise
     */
    override val isZero: Boolean
        get() = delegate.signum() == 0

    /**
     * Checks if the value is greater than zero.
     *
     * @return true if the value is greater than zero, false otherwise
     */
    override val isPositive: Boolean
        get() = delegate.signum() > 0

    /**
     * Checks if the value is zero or greater.
     *
     * @return true if the value is zero or greater, false otherwise
     */
    override val isPositiveOrZero: Boolean
        get() = delegate.signum() >= 0

    /**
     * Checks if the value is less than zero.
     *
     * @return true if the value is less than zero, false otherwise
     */
    override val isNegative: Boolean
        get() = delegate.signum() < 0

    /**
     * Checks if the value is zero or less.
     *
     * @return true if the value is zero or less, false otherwise
     */
    override val isNegativeOrZero: Boolean
        get() = delegate.signum() <= 0

    /**
     * Checks if this value is equal to another.
     *
     * @param other the other value, not null
     * @return true is this is greater than the specified value, false otherwise
     */
    override fun isEqual(other: Num?): Boolean {
        if (other==null) return false
        return !other.isNaN && compareTo(other) == 0
    }

    /**
     * Checks if this value matches another to a precision.
     *
     * @param other     the other value, not null
     * @param precision the int precision
     * @return true is this matches the specified value to a precision, false
     * otherwise
     */
    fun matches(other: Num, precision: Int): Boolean {
        val otherNum: Num = valueOf(other.toString(), precision)
        val thisNum: Num = valueOf(this.toString(), precision)
        if (thisNum.toString() == otherNum.toString()) {
            return true
        }
        if (log.isDebugEnabled) {
            log.debug("{} from {} does not match", thisNum, this)
            log.debug("{} from {} to precision {}", otherNum, other, precision)
        }
        return false
    }

    /**
     * Checks if this value matches another within an offset.
     *
     * @param other the other value, not null
     * @param delta the [Num] offset
     * @return true is this matches the specified value within an offset, false
     * otherwise
     */
    fun matches(other: Num, delta: Num): Boolean {
        val result = this.minus(other)
        if (!result.isGreaterThan(delta)) {
            return true
        }
        if (log.isDebugEnabled) {
            log.debug("{} does not match", this)
            log.debug("{} within offset {}", other, delta)
        }
        return false
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
        return !other.isNaN && delegate.compareTo((other as DecimalNum?)!!.delegate) < 1
    }

    override fun compareTo(other: Num): Int {
        return if (other.isNaN) 0 else delegate.compareTo((other as DecimalNum).delegate)
    }

    /**
     * Returns the minimum of this `Num` and `other`.
     *
     * @param other value with which the minimum is to be computed
     * @return the `Num` whose value is the lesser of this `Num` and
     * `other`. If they are equal, as defined by the
     * [compareTo][.compareTo] method, `this` is returned.
     */
    override fun min(other: Num): Num {
        return if (other.isNaN) NaN.Companion.NaN else if (compareTo(other) <= 0) this else other
    }

    /**
     * Returns the maximum of this `Num` and `other`.
     *
     * @param other value with which the maximum is to be computed
     * @return the `Num` whose value is the greater of this `Num` and
     * `other`. If they are equal, as defined by the
     * [compareTo][.compareTo] method, `this` is returned.
     */
    override fun max(other: Num): Num {
        return if (other.isNaN) NaN.Companion.NaN else if (compareTo(other) >= 0) this else other
    }

    override fun hashCode(): Int {
        return Objects.hash(delegate)
    }

    /**
     * {@inheritDoc} Warning: This method returns true if `this` and `obj` are both
     * NaN.NaN.
     */
    override fun equals(other: Any?): Boolean {
        return if (other !is DecimalNum) {
            false
        } else delegate.compareTo(other.delegate) == 0
    }

    override fun toString(): String {
        return delegate.toString()
    }

    override fun pow(n: Num): Num {
        // There is no BigDecimal.pow(BigDecimal). We could do:
        // double Math.pow(double delegate.doubleValue(), double n)
        // But that could overflow any of the three doubles.
        // Instead perform:
        // x^(a+b) = x^a * x^b
        // Where:
        // n = a+b
        // a is a whole number (make sure it doesn't overflow int)
        // remainder 0 <= b < 1
        // So:
        // x^a uses PrecisionNum ((PrecisionNum) x).pow(int a) cannot overflow Num
        // x^b uses double Math.pow(double x, double b) cannot overflow double because b
        // < 1.
        // As suggested: https://stackoverflow.com/a/3590314

        // get n = a+b, same precision as n
        val aplusb = (n as DecimalNum).delegate
        // get the remainder 0 <= b < 1, looses precision as double
        val b = aplusb.remainder(BigDecimal.ONE)
        // bDouble looses precision
        val bDouble = b.toDouble()
        // get the whole number a
        val a = aplusb.subtract(b)
        // convert a to an int, fails on overflow
        val aInt = a.intValueExact()
        // use BigDecimal pow(int)
        val xpowa = delegate.pow(aInt)
        // use double pow(double, double)
        val xpowb = delegate.toDouble().pow(bDouble)
        // use PrecisionNum.multiply(PrecisionNum)
        val result = xpowa.multiply(BigDecimal(xpowb))
        return DecimalNum(result.toString())
    }

    companion object {
        private const val DEFAULT_PRECISION = 32
        private val log = LoggerFactory.getLogger(DecimalNum::class.java)
        private val ZERO = valueOf(0)
        private val ONE = valueOf(1)
        private val HUNDRED = valueOf(100)

        /**
         * Returns a `Num` version of the given `String`.
         *
         * @param val the number
         * @return the `Num`
         */
        @JvmStatic
        fun valueOf(`val`: String): DecimalNum {
            if (`val`.equals("NAN", ignoreCase = true)) {
                throw NumberFormatException()
            }
            return DecimalNum(`val`)
        }

        /**
         * Returns a `Num) version of the given { String} with a precision.
         *
         * val the number
         *
         * precision the precision
         * the { Num}`
         */
        @JvmStatic
        fun valueOf(`val`: String, precision: Int): DecimalNum {
            if (`val`.equals("NAN", ignoreCase = true)) {
                throw NumberFormatException()
            }
            return DecimalNum(`val`, precision)
        }

        /**
         * Returns a `Num` version of the given `short`.
         *
         * @param val the number
         * @return the `Num`
         */
        @JvmStatic
        fun valueOf(`val`: Short): DecimalNum {
            return DecimalNum(`val`)
        }

        /**
         * Returns a `Num` version of the given `int`.
         *
         * @param val the number
         * @return the `Num`
         */
        @JvmStatic
        fun valueOf(`val`: Int): DecimalNum {
            return DecimalNum(`val`)
        }

        /**
         * Returns a `Num` version of the given `long`.
         *
         * @param val the number
         * @return the `Num`
         */
        @JvmStatic
        fun valueOf(`val`: Long): DecimalNum {
            return DecimalNum(`val`)
        }

        /**
         * Returns a `Num` version of the given `float`. Using the float
         * version could introduce inaccuracies.
         *
         * @param val the number
         * @return the `Num`
         */
        @JvmStatic
        fun valueOf(`val`: Float): DecimalNum {
            if (java.lang.Float.isNaN(`val`)) {
                throw NumberFormatException()
            }
            return DecimalNum(`val`)
        }

        @JvmStatic
        fun valueOf(`val`: BigDecimal): DecimalNum {
            return DecimalNum(`val`, `val`.precision())
        }

        @JvmStatic
        fun valueOf(`val`: BigDecimal, precision: Int): DecimalNum {
            return DecimalNum(`val`, precision)
        }

        /**
         * Returns a `Num` version of the given `double`. Using the double
         * version could introduce inaccuracies.
         *
         * @param val the number
         * @return the `Num`
         */
        @JvmStatic
        fun valueOf(`val`: Double): DecimalNum {
            if (java.lang.Double.isNaN(`val`)) {
                throw NumberFormatException()
            }
            return DecimalNum(`val`)
        }

        /**
         * Returns a `Num` version of the given `Num`.
         *
         * @param val the number
         * @return the `Num`
         */
        @JvmStatic
        fun valueOf(`val`: DecimalNum): DecimalNum {
            return `val`
        }

        /**
         * Returns a `Num` version of the given `Number`. Warning: This
         * method turns the number into a string first
         *
         * @param val the number
         * @return the `Num`
         */
        @JvmStatic
        fun valueOf(`val`: Number?): DecimalNum {
            return DecimalNum(`val`.toString())
        }
    }
}