/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Immutable, arbitrary-precision signed decimal numbers designed for technical analysis.
 * <p>
 * A {@code Decimal} consists of a {@code BigDecimal} with arbitrary {@link MathContext} (precision and rounding mode).
 *
 * @see BigDecimal
 * @see MathContext
 * @see RoundingMode
 */
public final class Decimal
        extends Number
        implements Comparable<Number>, Serializable {

    private static final long serialVersionUID = 2225130444465033658L;

    public static final MathContext MATH_CONTEXT = new MathContext(32, RoundingMode.HALF_UP);

    /** Not-a-Number instance (infinite error) */
    public static final Decimal NaN = new Decimal();

    public static final Decimal ZERO = valueOf(0);
    public static final Decimal ONE = valueOf(1);
    public static final Decimal TWO = valueOf(2);
    public static final Decimal THREE = valueOf(3);
    public static final Decimal TEN = valueOf(10);
    public static final Decimal HUNDRED = valueOf(100);
    public static final Decimal THOUSAND = valueOf(1000);

    private final BigDecimal delegate;

    /**
     * Constructor.
     * Only used for NaN instance.
     */
    private Decimal() {
        delegate = null;
    }

    /**
     * Constructor.
     * @param val the string representation of the decimal value
     */
    private Decimal(String val) {
        delegate = new BigDecimal(val, MATH_CONTEXT);
    }

    private Decimal(short val) {
        delegate = new BigDecimal(val, MATH_CONTEXT);
    }

    private Decimal(int val) {
        delegate = BigDecimal.valueOf(val);
    }

    private Decimal(long val) {
        delegate = BigDecimal.valueOf(val);
    }

    private Decimal(float val) {
        delegate = new BigDecimal(val, MATH_CONTEXT);
    }

    private Decimal(double val) {
        delegate = BigDecimal.valueOf(val);
    }

    private Decimal(BigDecimal val) {
        delegate = Objects.requireNonNull(val);
    }

    /**
     * Returns the underlying {@link BigDecimal} delegate
     * @return BigDecimal delegate instance of this instance
     */
    public BigDecimal getDelegate(){
        return delegate;
    }

    /**
     * Returns a {@code Decimal} whose value is {@code (this + augend)},
     * with rounding according to the context settings.
     * @param augend value to be added to this {@code Decimal}.
     * @return {@code this + augend}, rounded as necessary
     * @see BigDecimal#add(java.math.BigDecimal, java.math.MathContext)
     */
    public Decimal plus(Number augend) {
        if ((this == NaN) || (augend == NaN)) {
            return NaN;
        }
        return new Decimal(delegate.add(Decimal.valueOf(augend).delegate, MATH_CONTEXT));
    }

    /**
     * Returns a {@code Decimal} whose value is {@code (this - augend)},
     * with rounding according to the context settings.
     * @param subtrahend value to be subtracted from this {@code Decimal}.
     * @return {@code this - subtrahend}, rounded as necessary
     * @see BigDecimal#subtract(java.math.BigDecimal, java.math.MathContext)
     */
    public Decimal minus(Number subtrahend) {
        if ((this == NaN) || (subtrahend == NaN)) {
            return NaN;
        }
        return new Decimal(delegate.subtract(Decimal.valueOf(subtrahend).delegate, MATH_CONTEXT));
    }

    /**
     * Returns a {@code Decimal} whose value is {@code this * multiplicand},
     * with rounding according to the context settings.
     * @param multiplicand value to be multiplied by this {@code Decimal}.
     * @return {@code this * multiplicand}, rounded as necessary
     * @see BigDecimal#multiply(java.math.BigDecimal, java.math.MathContext)
     */
    public Decimal multipliedBy(Number multiplicand) {
        if ((this == NaN) || (multiplicand == NaN)) {
            return NaN;
        }
        return new Decimal(delegate.multiply(Decimal.valueOf(multiplicand).delegate, MATH_CONTEXT));
    }

    /**
     * Returns a {@code Decimal} whose value is {@code (this / divisor)},
     * with rounding according to the context settings.
     * @param divisor value by which this {@code Decimal} is to be divided.
     * @return {@code this / divisor}, rounded as necessary
     * @see BigDecimal#divide(java.math.BigDecimal, java.math.MathContext)
     */
    public Decimal dividedBy(Number divisor) {
        if ((this == NaN) || (divisor == NaN) || Decimal.valueOf(divisor).isZero()) {
            return NaN;
        }
        return new Decimal(delegate.divide(Decimal.valueOf(divisor).delegate, MATH_CONTEXT));
    }

    /**
     * Returns a {@code Decimal} whose value is {@code (this % divisor)},
     * with rounding according to the context settings.
     * @param divisor value by which this {@code Decimal} is to be divided.
     * @return {@code this % divisor}, rounded as necessary.
     * @see BigDecimal#remainder(java.math.BigDecimal, java.math.MathContext)
     */
    public Decimal remainder(Number divisor) {
        if ((this == NaN) || (divisor == NaN) || Decimal.valueOf(divisor).isZero()) {
            return NaN;
        }
        return new Decimal(delegate.remainder(Decimal.valueOf(divisor).delegate, MATH_CONTEXT));
    }

    /**
     * Returns a {@code Decimal} whose value is rounded down to the nearest whole number.
     * @return <tt>this<sup>n</sup></tt>
     */
    public Decimal floor() {
        if (this == NaN) {
            return NaN;
        }
        return new Decimal(delegate.setScale(0, RoundingMode.FLOOR));
    }

    /**
     * Returns a {@code Decimal} whose value is rounded up to the nearest whole number.
     * @return <tt>this<sup>n</sup></tt>
     */
    public Decimal ceil() {
        if (this == NaN) {
            return NaN;
        }
        return new Decimal(delegate.setScale(0, RoundingMode.CEILING));
    }

    /**
     * Returns a {@code Decimal} whose value is <tt>(this<sup>n</sup>)</tt>.
     * @param n power to raise this {@code Decimal} to.
     * @return <tt>this<sup>n</sup></tt>
     * @see BigDecimal#pow(int, java.math.MathContext)
     */
    public Decimal pow(int n) {
        if (this == NaN) {
            return NaN;
        }
        return new Decimal(delegate.pow(n, MATH_CONTEXT));
    }

    /**
     * Returns the correctly rounded positive square root of the <code>double</code> value of this {@code Decimal}.
     * /!\ Warning! Uses the {@code StrictMath#sqrt(double)} method under the hood.
     * @return the positive square root of {@code this}
     * @see StrictMath#sqrt(double)
     */
    public Decimal sqrt() {
        if (this == NaN) {
            return NaN;
        }
        return new Decimal(StrictMath.sqrt(delegate.doubleValue()));
    }

    /**
     * Returns a {@code Decimal} whose value is the absolute value
     * of this {@code Decimal}.
     * @return {@code abs(this)}
     */
    public Decimal abs() {
        if (this == NaN) {
            return NaN;
        }
        return new Decimal(delegate.abs());
    }

    /**
     * Checks if the value is zero.
     * @return true if the value is zero, false otherwise
     */
    public boolean isZero() {
        if (this == NaN) {
            return false;
        }
        return compareTo(ZERO) == 0;
    }

    /**
     * Checks if the value is greater than zero.
     * @return true if the value is greater than zero, false otherwise
     */
    public boolean isPositive() {
        if (this == NaN) {
            return false;
        }
        return compareTo(ZERO) > 0;
    }

    /**
     * Checks if the value is zero or greater.
     * @return true if the value is zero or greater, false otherwise
     */
    public boolean isPositiveOrZero() {
        if (this == NaN) {
            return false;
        }
        return compareTo(ZERO) >= 0;
    }

    /**
     * Checks if the value is Not-a-Number.
     * @return true if the value is Not-a-Number (NaN), false otherwise
     */
    public boolean isNaN() {
        return this == NaN;
    }

    /**
     * Checks if the value is less than zero.
     * @return true if the value is less than zero, false otherwise
     */
    public boolean isNegative() {
        if (this == NaN) {
            return false;
        }
        return compareTo(ZERO) < 0;
    }

    /**
     * Checks if the value is zero or less.
     * @return true if the value is zero or less, false otherwise
     */
    public boolean isNegativeOrZero() {
        if (this == NaN) {
            return false;
        }
        return compareTo(ZERO) <= 0;
    }

    /**
     * Checks if this value is equal to another.
     * @param other the other value, not null
     * @return true is this is greater than the specified value, false otherwise
     */
    public boolean isEqual(Number other) {
        if ((this == NaN) || (other == NaN)) {
            return false;
        }
        return compareTo(Decimal.valueOf(other)) == 0;
    }

    /**
     * Checks if this value is greater than another.
     * @param other the other value, not null
     * @return true is this is greater than the specified value, false otherwise
     */
    public boolean isGreaterThan(Number other) {
        if ((this == NaN) || (other == NaN)) {
            return false;
        }
        return compareTo(Decimal.valueOf(other)) > 0;
    }

    /**
     * Checks if this value is greater than or equal to another.
     * @param other the other value, not null
     * @return true is this is greater than or equal to the specified value, false otherwise
     */
    public boolean isGreaterThanOrEqual(Number other) {
        if ((this == NaN) || (other == NaN)) {
            return false;
        }
        return compareTo(Decimal.valueOf(other)) > -1;
    }

    /**
     * Checks if this value is less than another.
     * @param other the other value, not null
     * @return true is this is less than the specified value, false otherwise
     */
    public boolean isLessThan(Number other) {
        if ((this == NaN) || (other == NaN)) {
            return false;
        }
        return compareTo(Decimal.valueOf(other)) < 0;
    }

    /**
     * Checks if this value is less than or equal to another.
     * @param other the other value, not null
     * @return true is this is less than or equal to the specified value, false otherwise
     */
    public boolean isLessThanOrEqual(Number other) {
        if ((this == NaN) || (other == NaN)) {
            return false;
        }
        return compareTo(Decimal.valueOf(other)) < 1;
    }

    @Override
    public int compareTo(Number other) {
        if ((this == NaN) || (other == NaN)) {
            return 0;
        }
        return delegate.compareTo(Decimal.valueOf(other).delegate);
    }

    /**
     * Returns the minimum of this {@code Decimal} and {@code other}.
     * @param other value with which the minimum is to be computed
     * @return the {@code Decimal} whose value is the lesser of this
     *         {@code Decimal} and {@code other}.  If they are equal,
     *         as defined by the {@link #compareTo(Number) compareTo}
     *         method, {@code this} is returned.
     */
    public Decimal min(Number other) {
        if ((this == NaN) || (other == NaN)) {
            return NaN;
        }
        return (compareTo(other) <= 0 ? this : Decimal.valueOf(other));
    }

    /**
     * Returns the maximum of this {@code Decimal} and {@code other}.
     * @param  other value with which the maximum is to be computed
     * @return the {@code Decimal} whose value is the greater of this
     *         {@code Decimal} and {@code other}.  If they are equal,
     *         as defined by the {@link #compareTo(Number) compareTo}
     *         method, {@code this} is returned.
     */
    public Decimal max(Number other) {
        if ((this == NaN) || (other == NaN)) {
            return NaN;
        }
        return (compareTo(other) >= 0 ? this : Decimal.valueOf(other));
    }

    /**
     * Converts this {@code Decimal} to a {@code short}.
     * @return this {@code Decimal} converted to a {@code short}
     * @see BigDecimal#shortValue()
     */
    public short shortValue() {
        if (this == NaN) {
            return 0;
        }
        return delegate.shortValue();
    }

    /**
     * Converts this {@code Decimal} to a {@code int}.
     * @return this {@code Decimal} converted to a {@code int}
     * @see BigDecimal#intValue()
     */
    public int intValue() {
        if (this == NaN) {
            return 0;
        }
        return delegate.intValue();
    }

    /**
     * Converts this {@code Decimal} to a {@code long}.
     * @return this {@code Decimal} converted to a {@code long}
     * @see BigDecimal#longValue()
     */
    public long longValue() {
        if (this == NaN) {
            return 0;
        }
        return delegate.longValue();
    }

    /**
     * Converts this {@code Decimal} to a {@code float}.
     * @return this {@code Decimal} converted to a {@code float}
     * @see BigDecimal#floatValue()
     */
    public float floatValue() {
        if (this == NaN) {
            return Float.NaN;
        }
        return delegate.floatValue();
    }

    /**
     * Converts this {@code Decimal} to a {@code double}.
     * @return this {@code Decimal} converted to a {@code double}
     * @see BigDecimal#doubleValue()
     */
    public double doubleValue() {
        if (this == NaN) {
            return Double.NaN;
        }
        return delegate.doubleValue();
    }

    /**
     * @see Decimal#doubleValue()
     */
    @Deprecated
    public double toDouble() {
        return doubleValue();
    }

    @Override
    public String toString() {
        if (this == NaN) {
            return "NaN";
        }
        return delegate.toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegate);
    }

    /**
     * {@inheritDoc}
     * Warning: This method returns true if `this` and `obj` are both NaN.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof Decimal)) {
            return false;
        }
        final Decimal other = (Decimal) obj;
        return this.delegate == other.delegate
                || (this.delegate != null && (this.delegate.compareTo(other.delegate) == 0));
    }

    /**
     * Returns a {@code Decimal} version of the given {@code String}.
     * @param val the number
     * @return the {@code Decimal}
     */
    public static Decimal valueOf(String val) {
        return val.equals("NaN")
                ? Decimal.NaN
                : new Decimal(val);
    }

    /**
     * Returns a {@code Decimal} version of the given {@code short}.
     * @param val the number
     * @return the {@code Decimal}
     */
    public static Decimal valueOf(short val) {
        return new Decimal(val);
    }

    /**
     * Returns a {@code Decimal} version of the given {@code int}.
     * @param val the number
     * @return the {@code Decimal}
     */
    public static Decimal valueOf(int val) {
        return new Decimal(val);
    }

    /**
     * Returns a {@code Decimal} version of the given {@code long}.
     * @param val the number
     * @return the {@code Decimal}
     */
    public static Decimal valueOf(long val) {
        return new Decimal(val);
    }

    /**
     * Returns a {@code Decimal} version of the given {@code float}.
     * @param val the number
     * @return the {@code Decimal}
     */
    public static Decimal valueOf(float val) {
        if (val == Float.NaN) {
            return Decimal.NaN;
        }
        return new Decimal(val);
    }

    public static Decimal valueOf(BigDecimal val){
                return new Decimal(val);
    }

    /**
     * Returns a {@code Decimal} version of the given {@code double}.
     * @param val the number
     * @return the {@code Decimal}
     */
    public static Decimal valueOf(double val) {
        if (val == Double.NaN) {
            return Decimal.NaN;
        }
        return new Decimal(val);
    }

    /**
     * Returns a {@code Decimal} version of the given {@code Decimal}.
     * @param val the number
     * @return the {@code Decimal}
     */
    public static Decimal valueOf(Decimal val) {
        return val;
    }

    /**
     * Returns a {@code Decimal} version of the given {@code Number}.
     * Warning: This method turns the number into a string first
     * @param val the number
     * @return the {@code Decimal}
     */
    public static Decimal valueOf(Number val) {
        return new Decimal(val.toString());
    }
}