/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan, Ta4j Organization & respective authors (see AUTHORS)

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
package org.ta4j.core.Num;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.function.Function;

import static org.ta4j.core.Num.NaN.NaN;
/**
 * Representation of BigDecimal. High precision, low performance.
 * A {@code Num} consists of a {@code BigDecimal} with arbitrary {@link MathContext} (precision and rounding mode).
 *
 * @see BigDecimal
 * @see MathContext
 * @see RoundingMode
 * @see Num
 * @apiNote the delegate should never become a NaN.NaN value. No self NaN.NaN checks provided
 *
 */
public final class BigDecimalNum implements Num {

    private static final long serialVersionUID = 785564782721079992L;

    private static final MathContext MATH_CONTEXT = new MathContext(32, RoundingMode.HALF_UP);

    private final BigDecimal delegate;


    @Override
    public Function<Number, Num> function() {
        return BigDecimalNum::valueOf;
    }

    /**
     * Constructor.
     * @param val the string representation of the Num value
     */
    private BigDecimalNum(String val) {
        delegate = new BigDecimal(val, MATH_CONTEXT);
    }

    private BigDecimalNum(short val) {
        delegate = new BigDecimal(val, MATH_CONTEXT);
    }

    private BigDecimalNum(int val) {
        delegate = BigDecimal.valueOf(val);
    }

    private BigDecimalNum(long val) {
        delegate = BigDecimal.valueOf(val);
    }

    private BigDecimalNum(float val) {
        delegate = new BigDecimal(val, MATH_CONTEXT);
    }

    private BigDecimalNum(double val) {
        delegate = BigDecimal.valueOf(val);
    }

    private BigDecimalNum(BigDecimal val) {
        delegate = Objects.requireNonNull(val);
    }

    /**
     * Returns the underlying {@link BigDecimal} delegate
     * @return BigDecimal delegate instance of this instance
     */
    public Number getDelegate(){
        return delegate;
    }

    @Override
    public String getName() {
        return "BigDecimalNum";
    }


    public Num plus(Num augend) {
        if (augend == NaN) {
            return NaN;
        }
        BigDecimal bigDecimal = ((BigDecimalNum)augend).delegate;
        return new BigDecimalNum(delegate.add(bigDecimal, MATH_CONTEXT));
    }

    /**
     * Returns a {@code Num} whose value is {@code (this - augend)},
     * with rounding according to the context settings.
     * @param subtrahend value to be subtracted from this {@code Num}.
     * @return {@code this - subtrahend}, rounded as necessary
     * @see BigDecimal#subtract(java.math.BigDecimal, java.math.MathContext)
     */
    public Num minus(Num subtrahend) {
        if (subtrahend == NaN) {
            return NaN;
        }
        BigDecimal bigDecimal = ((BigDecimalNum)subtrahend).delegate;
        return new BigDecimalNum(delegate.subtract(bigDecimal, MATH_CONTEXT));
    }

    /**
     * Returns a {@code Num} whose value is {@code this * multiplicand},
     * with rounding according to the context settings.
     * @param multiplicand value to be multiplied by this {@code Num}.
     * @return {@code this * multiplicand}, rounded as necessary
     * @see BigDecimal#multiply(java.math.BigDecimal, java.math.MathContext)
     */
    public Num multipliedBy(Num multiplicand) {
        if ((multiplicand == NaN)) {
            return NaN;
        }
        BigDecimal bigDecimal = ((BigDecimalNum)multiplicand).delegate;
        return new BigDecimalNum(delegate.multiply(bigDecimal, MATH_CONTEXT));
    }

    /**
     * Returns a {@code Num} whose value is {@code (this / divisor)},
     * with rounding according to the context settings.
     * @param divisor value by which this {@code Num} is to be divided.
     * @return {@code this / divisor}, rounded as necessary
     * @see BigDecimal#divide(java.math.BigDecimal, java.math.MathContext)
     */
    public Num dividedBy(Num divisor) {
        if ((divisor == NaN) || divisor.isZero()) {
            return NaN;
        }
        BigDecimal bigDecimal = ((BigDecimalNum)divisor).delegate;
        return new BigDecimalNum(delegate.divide(bigDecimal, MATH_CONTEXT));
    }

    /**
     * Returns a {@code Num} whose value is {@code (this % divisor)},
     * with rounding according to the context settings.
     * @param divisor value by which this {@code Num} is to be divided.
     * @return {@code this % divisor}, rounded as necessary.
     * @see BigDecimal#remainder(java.math.BigDecimal, java.math.MathContext)
     */
    @Override
    public Num remainder(Num divisor) {
        if ( (divisor == NaN) || divisor.isZero()) {
            return NaN;
        }
        return new BigDecimalNum(delegate.remainder(((BigDecimalNum)divisor).delegate, MATH_CONTEXT));
    }

    /**
     * Returns a {@code Num} whose value is rounded down to the nearest whole number.
     * @return <tt>this<sup>n</sup></tt>
     */
    public Num floor() {
        return new BigDecimalNum(delegate.setScale(0, RoundingMode.FLOOR));
    }

    /**
     * Returns a {@code Num} whose value is rounded up to the nearest whole number.
     * @return <tt>this<sup>n</sup></tt>
     */
    public Num ceil() {
        return new BigDecimalNum(delegate.setScale(0, RoundingMode.CEILING));
    }

    /**
     * Returns a {@code Num} whose value is <tt>(this<sup>n</sup>)</tt>.
     * @param n power to raise this {@code Num} to.
     * @return <tt>this<sup>n</sup></tt>
     * @see BigDecimal#pow(int, java.math.MathContext)
     */
    public Num pow(int n) {
        return new BigDecimalNum(delegate.pow(n, MATH_CONTEXT));
    }

    /**
     * Returns the correctly rounded positive square root of the <code>double</code> value of this {@code Num}.
     * /!\ Warning! Uses the {@code StrictMath#sqrt(double)} method under the hood.
     * @return the positive square root of {@code this}
     * @see StrictMath#sqrt(double)
     */
    public Num sqrt() {
        return new BigDecimalNum(StrictMath.sqrt(delegate.doubleValue()));
    }

    /**
     * Returns a {@code Num} whose value is the absolute value
     * of this {@code Num}.
     * @return {@code abs(this)}
     */
    public Num abs() {
        return new BigDecimalNum(delegate.abs());
    }

    /**
     * Checks if the value is zero.
     * @return true if the value is zero, false otherwise
     */
    public boolean isZero() {
        return compareTo(BigDecimalNum.valueOf(0)) == 0;
    }

    /**
     * Checks if the value is greater than zero.
     * @return true if the value is greater than zero, false otherwise
     */
    public boolean isPositive() {
        return compareTo(BigDecimalNum.valueOf(0)) > 0;
    }

    /**
     * Checks if the value is zero or greater.
     * @return true if the value is zero or greater, false otherwise
     */
    public boolean isPositiveOrZero() {
        return compareTo(BigDecimalNum.valueOf(0)) >= 0;
    }

    /**
     * Checks if the value is less than zero.
     * @return true if the value is less than zero, false otherwise
     */
    public boolean isNegative() {
        return compareTo(function().apply(0)) < 0;
    }

    /**
     * Checks if the value is zero or less.
     * @return true if the value is zero or less, false otherwise
     */
    public boolean isNegativeOrZero() {
        return compareTo(BigDecimalNum.valueOf(0)) <= 0;
    }

    /**
     * Checks if this value is equal to another.
     * @param other the other value, not null
     * @return true is this is greater than the specified value, false otherwise
     */
    public boolean isEqual(Num other) {
        return (other != NaN) && compareTo(other) == 0;
    }

    /**
     * Checks if this value is greater than another.
     * @param other the other value, not null
     * @return true is this is greater than the specified value, false otherwise
     */
    public boolean isGreaterThan(Num other) {
        return (other != NaN) && compareTo(other) > 0;
    }

    /**
     * Checks if this value is greater than or equal to another.
     * @param other the other value, not null
     * @return true is this is greater than or equal to the specified value, false otherwise
     */
    public boolean isGreaterThanOrEqual(Num other) {
        return (other != NaN) && compareTo(other) > -1;
    }

    /**
     * Checks if this value is less than another.
     * @param other the other value, not null
     * @return true is this is less than the specified value, false otherwise
     */
    public boolean isLessThan(Num other) {
        return (other != NaN) && compareTo(other) < 0;
    }

    @Override
    public boolean isLessThanOrEqual(Num other) {
        return (other != NaN) && delegate.compareTo(((BigDecimalNum) other).delegate) < 1;
    }

    /**
     * Checks if this value is less than or equal to another.
     * @param other the other value, not null
     * @return true is this is less than or equal to the specified value, false otherwise
     */
    public boolean isLessThanOrEqual(Number other) {
        return (other != NaN) && compareTo(function().apply(other)) < 1;
    }

    public int compareTo(Num other) {
        if ((other == NaN)) {
            return 0;
        }
        return delegate.compareTo(((BigDecimalNum) other).delegate);
    }

    /**
     * Returns the minimum of this {@code Num} and {@code other}.
     * @param other value with which the minimum is to be computed
     * @return the {@code Num} whose value is the lesser of this
     *         {@code Num} and {@code other}.  If they are equal,
     *         as defined by the {@link #compareTo(Num) compareTo}
     *         method, {@code this} is returned.
     */
    public Num min(Num other) {
        if (other == NaN) {
            return NaN;
        }
        return (compareTo(other) <= 0 ? this : other);
    }

    /**
     * Returns the maximum of this {@code Num} and {@code other}.
     * @param  other value with which the maximum is to be computed
     * @return the {@code Num} whose value is the greater of this
     *         {@code Num} and {@code other}.  If they are equal,
     *         as defined by the {@link #compareTo(Num) compareTo}
     *         method, {@code this} is returned.
     */
    public Num max(Num other) {
        if (other == NaN) {
            return NaN;
        }
        return (compareTo(other) >= 0 ? this : other);
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegate);
    }

    /**
     * {@inheritDoc}
     * Warning: This method returns true if `this` and `obj` are both NaN.NaN.
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Num)) {
            return false;
        }
        // `this` cannot be NaN
        return obj != NaN && this.delegate.compareTo(((BigDecimalNum) obj).delegate) == 0;
    }

    /**
     * Returns a {@code Num} version of the given {@code String}.
     * @param val the number
     * @return the {@code Num}
     */
    public static Num valueOf(String val) {
        return val.toUpperCase().equals("NAN") ? NaN : new BigDecimalNum(val);
    }

    /**
     * Returns a {@code Num} version of the given {@code short}.
     * @param val the number
     * @return the {@code Num}
     */
    public static Num valueOf(short val) {
        return new BigDecimalNum(val);
    }

    /**
     * Returns a {@code Num} version of the given {@code int}.
     * @param val the number
     * @return the {@code Num}
     */
    public static Num valueOf(int val) {
        return new BigDecimalNum(val);
    }

    /**
     * Returns a {@code Num} version of the given {@code long}.
     * @param val the number
     * @return the {@code Num}
     */
    public static Num valueOf(long val) {
        return new BigDecimalNum(val);
    }

    /**
     * Returns a {@code Num} version of the given {@code float}.
     * @param val the number
     * @return the {@code Num}
     */
    public static Num valueOf(float val) {
        return val == Float.NaN ? NaN : new BigDecimalNum(val);
    }

    public static BigDecimalNum valueOf(BigDecimal val){
                return new BigDecimalNum(val);
    }

    /**
     * Returns a {@code Num} version of the given {@code double}.
     * @param val the number
     * @return the {@code Num}
     */
    public static Num valueOf(double val) {
        return val == Double.NaN ? NaN :new BigDecimalNum(val);
    }

    /**
     * Returns a {@code Num} version of the given {@code Num}.
     * @param val the number
     * @return the {@code Num}
     */
    public static BigDecimalNum valueOf(BigDecimalNum val) {
        return val;
    }

    /**
     * Returns a {@code Num} version of the given {@code Number}.
     * Warning: This method turns the number into a string first
     * @param val the number
     * @return the {@code Num}
     */
    public static BigDecimalNum valueOf(Number val) {
        return new BigDecimalNum(val.toString());
    }

    @Override
    public String toString(){
        return delegate.toString();
    }
}