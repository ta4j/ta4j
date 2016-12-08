/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2016 Marc de Verdelhan & respective authors (see AUTHORS)
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

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Immutable, arbitrary-precision signed decimal numbers designed for technical analysis.
 * <p>
 * A {@code Decimal} consists of a {@code BigDecimal} with arbitrary {@link MathContext} (precision and rounding mode).
 *
 * @see BigDecimal
 * @see MathContext
 * @see RoundingMode
 */
public final class Decimal implements Comparable<Decimal>, Serializable {

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

    /**
     * Constructor.
     * @param val the double value
     */
    private Decimal(double val) {
        delegate = new BigDecimal(val, MATH_CONTEXT);
    }

    private Decimal(int val) {
        delegate = new BigDecimal(val, MATH_CONTEXT);
    }

    private Decimal(long val) {
        delegate = new BigDecimal(val, MATH_CONTEXT);
    }

    private Decimal(BigDecimal val) {
        delegate = val;
    }

    /**
     * Returns a {@code Decimal} whose value is {@code (this + augend)},
     * with rounding according to the context settings.
     * @param augend value to be added to this {@code Decimal}.
     * @return {@code this + augend}, rounded as necessary
     * @see BigDecimal#add(java.math.BigDecimal, java.math.MathContext)
     */
    public Decimal plus(Decimal augend) {
        if ((this == NaN) || (augend == NaN)) {
            return NaN;
        }
        return new Decimal(delegate.add(augend.delegate, MATH_CONTEXT));
    }

    /**
     * Returns a {@code Decimal} whose value is {@code (this - augend)},
     * with rounding according to the context settings.
     * @param subtrahend value to be subtracted from this {@code Decimal}.
     * @return {@code this - subtrahend}, rounded as necessary
     * @see BigDecimal#subtract(java.math.BigDecimal, java.math.MathContext)
     */
    public Decimal minus(Decimal subtrahend) {
        if ((this == NaN) || (subtrahend == NaN)) {
            return NaN;
        }
        return new Decimal(delegate.subtract(subtrahend.delegate, MATH_CONTEXT));
    }

    /**
     * Returns a {@code Decimal} whose value is {@code this * multiplicand},
     * with rounding according to the context settings.
     * @param multiplicand value to be multiplied by this {@code Decimal}.
     * @return {@code this * multiplicand}, rounded as necessary
     * @see BigDecimal#multiply(java.math.BigDecimal, java.math.MathContext)
     */
    public Decimal multipliedBy(Decimal multiplicand) {
        if ((this == NaN) || (multiplicand == NaN)) {
            return NaN;
        }
        return new Decimal(delegate.multiply(multiplicand.delegate, MATH_CONTEXT));
    }

    /**
     * Returns a {@code Decimal} whose value is {@code (this / divisor)},
     * with rounding according to the context settings.
     * @param divisor value by which this {@code Decimal} is to be divided.
     * @return {@code this / divisor}, rounded as necessary
     * @see BigDecimal#divide(java.math.BigDecimal, java.math.MathContext)
     */
    public Decimal dividedBy(Decimal divisor) {
        if ((this == NaN) || (divisor == NaN) || divisor.isZero()) {
            return NaN;
        }
        return new Decimal(delegate.divide(divisor.delegate, MATH_CONTEXT));
    }

    /**
     * Returns a {@code Decimal} whose value is {@code (this % divisor)},
     * with rounding according to the context settings.
     * @param divisor value by which this {@code Decimal} is to be divided.
     * @return {@code this % divisor}, rounded as necessary.
     * @see BigDecimal#remainder(java.math.BigDecimal, java.math.MathContext)
     */
    public Decimal remainder(Decimal divisor) {
        if ((this == NaN) || (divisor == NaN) || divisor.isZero()) {
            return NaN;
        }
        return new Decimal(delegate.remainder(divisor.delegate, MATH_CONTEXT));
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
     * Returns the correctly rounded natural logarithm (base e) of the <code>double</code> value of this {@code Decimal}.
     * /!\ Warning! Uses the {@code StrictMath#log(double)} method under the hood.
     * @return the natural logarithm (base e) of {@code this}
     * @see StrictMath#log(double)
     */
    public Decimal log() {
        if (this == NaN) {
            return NaN;
        }
        return new Decimal(StrictMath.log(delegate.doubleValue()));
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
    public boolean isEqual(Decimal other) {
        if ((this == NaN) || (other == NaN)) {
            return false;
        }
        return compareTo(other) == 0;
    }

    /**
     * Checks if this value is greater than another.
     * @param other the other value, not null
     * @return true is this is greater than the specified value, false otherwise
     */
    public boolean isGreaterThan(Decimal other) {
        if ((this == NaN) || (other == NaN)) {
            return false;
        }
        return compareTo(other) > 0;
    }

    /**
     * Checks if this value is greater than or equal to another.
     * @param other the other value, not null
     * @return true is this is greater than or equal to the specified value, false otherwise
     */
    public boolean isGreaterThanOrEqual(Decimal other) {
        if ((this == NaN) || (other == NaN)) {
            return false;
        }
        return compareTo(other) > -1;
    }

    /**
     * Checks if this value is less than another.
     * @param other the other value, not null
     * @return true is this is less than the specified value, false otherwise
     */
    public boolean isLessThan(Decimal other) {
        if ((this == NaN) || (other == NaN)) {
            return false;
        }
        return compareTo(other) < 0;
    }

    /**
     * Checks if this value is less than or equal to another.
     * @param other the other value, not null
     * @return true is this is less than or equal to the specified value, false otherwise
     */
    public boolean isLessThanOrEqual(Decimal other) {
        if ((this == NaN) || (other == NaN)) {
            return false;
        }
        return compareTo(other) < 1;
    }

    @Override
    public int compareTo(Decimal other) {
        if ((this == NaN) || (other == NaN)) {
            return 0;
        }
        return delegate.compareTo(other.delegate);
    }

    /**
     * Returns the minimum of this {@code Decimal} and {@code other}.
     * @param other value with which the minimum is to be computed
     * @return the {@code Decimal} whose value is the lesser of this
     *         {@code Decimal} and {@code other}.  If they are equal,
     *         as defined by the {@link #compareTo(Decimal) compareTo}
     *         method, {@code this} is returned.
     */
    public Decimal min(Decimal other) {
        if ((this == NaN) || (other == NaN)) {
            return NaN;
        }
        return (compareTo(other) <= 0 ? this : other);
    }

    /**
     * Returns the maximum of this {@code Decimal} and {@code other}.
     * @param  other value with which the maximum is to be computed
     * @return the {@code Decimal} whose value is the greater of this
     *         {@code Decimal} and {@code other}.  If they are equal,
     *         as defined by the {@link #compareTo(Decimal) compareTo}
     *         method, {@code this} is returned.
     */
    public Decimal max(Decimal other) {
        if ((this == NaN) || (other == NaN)) {
            return NaN;
        }
        return (compareTo(other) >= 0 ? this : other);
    }

    /**
     * Converts this {@code Decimal} to a {@code double}.
     * @return this {@code Decimal} converted to a {@code double}
     * @see BigDecimal#doubleValue()
     */
    public double toDouble() {
        if (this == NaN) {
            return Double.NaN;
        }
        return delegate.doubleValue();
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
        int hash = 7;
        hash = 53 * hash + (this.delegate != null ? this.delegate.hashCode() : 0);
        return hash;
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
        if (this.delegate != other.delegate
                && (this.delegate == null || (this.delegate.compareTo(other.delegate) != 0))) {
            return false;
        }
        return true;
    }

    public static Decimal valueOf(String val) {
        if ("NaN".equals(val)) {
            return NaN;
        }
        return new Decimal(val);
    }

    public static Decimal valueOf(double val) {
        if (Double.isNaN(val)) {
            return NaN;
        }
        return new Decimal(val);
    }

    public static Decimal valueOf(int val) {
        return new Decimal(val);
    }

    public static Decimal valueOf(long val) {
        return new Decimal(val);
    }
}
