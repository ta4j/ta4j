/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2015 Marc de Verdelhan & respective authors
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
package eu.verdelhan.ta4j;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Immutable, arbitrary-precision signed decimal numbers designed for technical analysis.
 * <p>
 * A {@code TADecimal} consists of a {@code BigDecimal} with arbitrary {@link MathContext} (precision and rounding mode).
 *
 * @see BigDecimal
 * @see MathContext
 * @see RoundingMode
 */
public class TADecimal implements Comparable<TADecimal> {

    public static final MathContext MATH_CONTEXT = new MathContext(32, RoundingMode.HALF_UP);

    public static final TADecimal ZERO = valueOf(0);
    public static final TADecimal ONE = valueOf(1);
    public static final TADecimal TWO = valueOf(2);
    public static final TADecimal THREE = valueOf(3);
    public static final TADecimal TEN = valueOf(10);
    public static final TADecimal HUNDRED = valueOf(100);

    private BigDecimal delegate;

    public TADecimal(String val) {
        delegate = new BigDecimal(val, MATH_CONTEXT);
    }

    public TADecimal(double val) {
        delegate = new BigDecimal(val, MATH_CONTEXT);
    }

    public TADecimal(BigInteger val) {
        delegate = new BigDecimal(val, MATH_CONTEXT);
    }

    public TADecimal(int val) {
        delegate = new BigDecimal(val, MATH_CONTEXT);
    }

    public TADecimal(long val) {
        delegate = new BigDecimal(val, MATH_CONTEXT);
    }

    public TADecimal(BigDecimal val) {
        this(val.toString());
    }

    /**
     * Returns a {@code TADecimal} whose value is {@code (this + augend)},
     * with rounding according to the context settings.
     * @param augend value to be added to this {@code TADecimal}.
     * @return {@code this + augend}, rounded as necessary
     * @see BigDecimal#add(java.math.BigDecimal, java.math.MathContext)
     */
    public TADecimal plus(TADecimal augend) {
        return new TADecimal(delegate.add(augend.delegate, MATH_CONTEXT));
    }

    /**
     * Returns a {@code TADecimal} whose value is {@code (this - augend)},
     * with rounding according to the context settings.
     * @param subtrahend value to be subtracted from this {@code TADecimal}.
     * @return {@code this - subtrahend}, rounded as necessary
     * @see BigDecimal#subtract(java.math.BigDecimal, java.math.MathContext)
     */
    public TADecimal minus(TADecimal subtrahend) {
        return new TADecimal(delegate.subtract(subtrahend.delegate, MATH_CONTEXT));
    }

    /**
     * Returns a {@code TADecimal} whose value is {@code this * multiplicand},
     * with rounding according to the context settings.
     * @param multiplicand value to be multiplied by this {@code TADecimal}.
     * @return {@code this * multiplicand}, rounded as necessary
     * @see BigDecimal#multiply(java.math.BigDecimal, java.math.MathContext)
     */
    public TADecimal multipliedBy(TADecimal multiplicand) {
        return new TADecimal(delegate.multiply(multiplicand.delegate, MATH_CONTEXT));
    }

    /**
     * Returns a {@code TADecimal} whose value is {@code (this / divisor)},
     * with rounding according to the context settings.
     * @param divisor value by which this {@code TADecimal} is to be divided.
     * @return {@code this / divisor}, rounded as necessary
     * @see BigDecimal#divide(java.math.BigDecimal, java.math.MathContext)
     */
    public TADecimal dividedBy(TADecimal divisor) {
        return new TADecimal(delegate.divide(divisor.delegate, MATH_CONTEXT));
    }

    /**
     * Returns a {@code TADecimal} whose value is {@code (this % divisor)},
     * with rounding according to the context settings.
     * @param divisor value by which this {@code TADecimal} is to be divided.
     * @return {@code this % divisor}, rounded as necessary.
     * @see BigDecimal#remainder(java.math.BigDecimal, java.math.MathContext)
     */
    public TADecimal remainder(TADecimal divisor) {
        return new TADecimal(delegate.remainder(divisor.delegate, MATH_CONTEXT));
    }


    /**
     * Returns a {@code TADecimal} whose value is <tt>(this<sup>n</sup>)</tt>.
     * @param n power to raise this {@code TADecimal} to.
     * @return <tt>this<sup>n</sup></tt>
     * @see BigDecimal#pow(int, java.math.MathContext)
     */
    public TADecimal pow(int n) {
        return new TADecimal(delegate.pow(n, MATH_CONTEXT));
    }

    /**
     * Returns the correctly rounded positive square root of the <code>double</code> value of this {@code TADecimal}.
     * /!\ Warning! Uses the {@code StrictMath#sqrt(double)} method under the hood.
     * @return the positive square root of {@code this}
     * @see StrictMath#sqrt(double)
     */
    public TADecimal sqrt() {
        return new TADecimal(StrictMath.sqrt(delegate.doubleValue()));
    }

    /**
     * Returns a {@code TADecimal} whose value is the absolute value
     * of this {@code TADecimal}.
     * @return {@code abs(this)}
     */
    public TADecimal abs() {
        return new TADecimal(delegate.abs());
    }

    /**
     * Checks if the value is zero.
     * @return true if the value is zero, false otherwise
     */
    public boolean isZero() {
        return compareTo(ZERO) == 0;
    }

    /**
     * Checks if the value is greater than zero.
     * @return true if the value is greater than zero, false otherwise
     */
    public boolean isPositive() {
        return compareTo(ZERO) > 0;
    }

    /**
     * Checks if the value is zero or greater.
     * @return true if the value is zero or greater, false otherwise
     */
    public boolean isPositiveOrZero() {
        return compareTo(ZERO) >= 0;
    }

    /**
     * Checks if the value is less than zero.
     * @return true if the value is less than zero, false otherwise
     */
    public boolean isNegative() {
        return compareTo(ZERO) < 0;
    }

    /**
     * Checks if the value is zero or less.
     * @return true if the value is zero or less, false otherwise
     */
    public boolean isNegativeOrZero() {
        return compareTo(ZERO) <= 0;
    }

    /**
     * Checks if this value is equal to another.
     * @param other the other value, not null
     * @return true is this is greater than the specified value, false otherwise
     */
    public boolean isEqual(TADecimal other) {
        return compareTo(other) == 0;
    }

    /**
     * Checks if this value is greater than another.
     * @param other the other value, not null
     * @return true is this is greater than the specified value, false otherwise
     */
    public boolean isGreaterThan(TADecimal other) {
        return compareTo(other) > 0;
    }

    /**
     * Checks if this value is greater than or equal to another.
     * @param other the other value, not null
     * @return true is this is greater than or equal to the specified value, false otherwise
     */
    public boolean isGreaterThanOrEqual(TADecimal other) {
        return compareTo(other) > -1;
    }

    /**
     * Checks if this value is less than another.
     * @param other the other value, not null
     * @return true is this is less than the specified value, false otherwise
     */
    public boolean isLessThan(TADecimal other) {
        return compareTo(other) < 0;
    }

    /**
     * Checks if this value is less than or equal to another.
     * @param other the other value, not null
     * @return true is this is less than or equal to the specified value, false otherwise
     */
    public boolean isLessThanOrEqual(TADecimal other) {
        return compareTo(other) < 1;
    }

    @Override
    public int compareTo(TADecimal other) {
        return delegate.compareTo(other.delegate);
    }

    /**
     * Returns the minimum of this {@code TADecimal} and {@code other}.
     * @param other value with which the minimum is to be computed
     * @return the {@code TADecimal} whose value is the lesser of this
     *         {@code TADecimal} and {@code other}.  If they are equal,
     *         as defined by the {@link #compareTo(TADecimal) compareTo}
     *         method, {@code this} is returned.
     */
    public TADecimal min(TADecimal other) {
        return (compareTo(other) <= 0 ? this : other);
    }

    /**
     * Returns the maximum of this {@code TADecimal} and {@code other}.
     * @param  other value with which the maximum is to be computed
     * @return the {@code TADecimal} whose value is the greater of this
     *         {@code TADecimal} and {@code other}.  If they are equal,
     *         as defined by the {@link #compareTo(TADecimal) compareTo}
     *         method, {@code this} is returned.
     */
    public TADecimal max(TADecimal other) {
        return (compareTo(other) >= 0 ? this : other);
    }

    /**
     * Converts this {@code TADecimal} to a {@code double}.
     * @return this {@code TADecimal} converted to a {@code double}
     * @see BigDecimal#doubleValue()
     */
    public double toDouble() {
        return delegate.doubleValue();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 53 * hash + (this.delegate != null ? this.delegate.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final TADecimal other = (TADecimal) obj;
        if (this.delegate != other.delegate
                && (this.delegate == null || (this.delegate.compareTo(other.delegate) != 0))) {
            return false;
        }
        return true;
    }

    public static TADecimal valueOf(String val) {
        return new TADecimal(val);
    }

    public static TADecimal valueOf(double val) {
        return new TADecimal(val);
    }

    public static TADecimal valueOf(int val) {
        return new TADecimal(val);
    }

    public static TADecimal valueOf(long val) {
        return new TADecimal(val);
    }
}
