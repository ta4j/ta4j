/*******************************************************************************
 *   The MIT License (MIT)
 *
 *   Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2018 Ta4j Organization 
 *   & respective authors (see AUTHORS)
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy of
 *   this software and associated documentation files (the "Software"), to deal in
 *   the Software without restriction, including without limitation the rights to
 *   use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 *   the Software, and to permit persons to whom the Software is furnished to do so,
 *   subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in all
 *   copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 *   FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *   COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 *   IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *   CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *******************************************************************************/
package org.ta4j.core.num;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.function.Function;

/**
 * Ta4js definition of operations that must be fulfilled by an object that
 * should be used as base for calculations
 * @see Num
 * @see Num#function()
 * @see DoubleNum
 * @see BigDecimalNum
 * @See PrecisionNum

 */
public interface Num extends Comparable<Num>, Serializable {

    /**
     * @return the delegate used from this <code>Num</code> implementation
     */
    Number getDelegate();

    /**
     * Returns the name/description of this Num implementation
     * @return the name/description
     */
    String getName();

    /**
     * Returns a {@code num} whose value is {@code (this + augend)},
     * @param augend value to be added to this {@code num}.
     * @return {@code this + augend}, rounded as necessary
     */
    Num plus(Num augend);

    /**
     * Returns a {@code num} whose value is {@code (this - augend)},
     * @param subtrahend value to be subtracted from this {@code num}.
     * @return {@code this - subtrahend}, rounded as necessary
     */
    Num minus(Num subtrahend);

    /**
     * Returns a {@code num} whose value is {@code this * multiplicand},
     * @param multiplicand value to be multiplied by this {@code num}.
     * @return {@code this * multiplicand}, rounded as necessary
     */
    Num multipliedBy(Num multiplicand);

    /**
     * Returns a {@code num} whose value is {@code (this / divisor)},
     * @param divisor value by which this {@code num} is to be divided.
     * @return {@code this / divisor}, rounded as necessary
     */
    Num dividedBy(Num divisor);

    /**
     * Returns a {@code num} whose value is {@code (this % divisor)},
     * @param divisor value by which this {@code num} is to be divided.
     * @return {@code this % divisor}, rounded as necessary.
     */
    Num remainder(Num divisor);

    /**
     * Returns a {@code num} whose value is <tt>(this<sup>n</sup>)</tt>.
     * @param n power to raise this {@code num} to.
     * @return <tt>this<sup>n</sup></tt>
     */
    Num pow(int n);

    /**
     * Returns a {@code num} whose value is <tt>(this<sup>n</sup>)</tt>.
     * @param n power to raise this {@code num} to.
     * @return <tt>this<sup>n</sup></tt>
     */
    Num pow(Num n);

    /**
     * Returns a {@code num} whose value is <tt>√(this)</tt>.
     * @return <tt>this<sup>n</sup></tt>
     */
    Num sqrt();

    /**
     * Returns a {@code num} whose value is <tt>√(this)</tt>.
     * @param precision to calculate.
     * @return <tt>this<sup>n</sup></tt>
     */
    Num sqrt(int precision);

    /**
     * Returns a {@code num} whose value is the absolute value
     * of this {@code num}.
     * @return {@code abs(this)}
     */
    Num abs();

    /**
     * Checks if the value is zero.
     * @return true if the value is zero, false otherwise
     */
    boolean isZero();

    /**
     * Checks if the value is greater than zero.
     * @return true if the value is greater than zero, false otherwise
     */
    boolean isPositive();

    /**
     * Checks if the value is zero or greater.
     * @return true if the value is zero or greater, false otherwise
     */
    boolean isPositiveOrZero();

    /**
     * Checks if the value is less than zero.
     * @return true if the value is less than zero, false otherwise
     */
    boolean isNegative();

    /**
     * Checks if the value is zero or less.
     * @return true if the value is zero or less, false otherwise
     */
    boolean isNegativeOrZero();

    /**
     * Checks if this value is equal to another.
     * @param other the other value, not null
     * @return true is this is greater than the specified value, false otherwise
     */
    boolean isEqual(Num other);

    /**
     * Checks if this value is greater than another.
     * @param other the other value, not null
     * @return true is this is greater than the specified value, false otherwise
     */
    boolean isGreaterThan(Num other);

    /**
     * Checks if this value is greater than or equal to another.
     * @param other the other value, not null
     * @return true is this is greater than or equal to the specified value, false otherwise
     */
    boolean isGreaterThanOrEqual(Num other);

    /**
     * Checks if this value is less than another.
     * @param other the other value, not null
     * @return true is this is less than the specified value, false otherwise
     */
    boolean isLessThan(Num other);

    /**
     * Checks if this value is less than another.
     * @param other the other value, not null
     * @return true is this is less than or equal the specified value, false otherwise
     */
    boolean isLessThanOrEqual(Num other);

    /**
     * Returns the minimum of this {@code num} and {@code other}.
     * @param other value with which the minimum is to be computed
     * @return the {@code num} whose value is the lesser of this
     *         {@code num} and {@code other}.  If they are equal,
     *         method, {@code this} is returned.
     */
   Num min(Num other);

    /**
     * Returns the maximum of this {@code num} and {@code other}.
     * @param  other value with which the maximum is to be computed
     * @return the {@code num} whose value is the greater of this
     *         {@code num} and {@code other}.  If they are equal,
     *         method, {@code this} is returned.
     */
    Num max(Num other);

    /**
     * Retruns the {@link Function} to convert a number instance to
     * the corresponding Num instance
     * @return
     */
    Function<Number, Num> function();

    /**
     * Transforms a {@link Number} into a new Num instance of this
     * <code>Num</code> implementation
     * @param value the Number to transform
     * @return the corresponding Num implementation of the <code>value</code>
     */
    default Num numOf(Number value){
        return function().apply(value);
    }

    /**
     * Transforms a {@link String} into a new Num instance of this with a precision
     * <code>Num</code> implementation
     * @param value the String to transform
     * @param precision the precision
     * @return the corresponding Num implementation of the <code>value</code>
     */
    default Num numOf(String string, int precision) {
        MathContext mathContext = new MathContext(precision, RoundingMode.HALF_UP);
        return this.numOf(new BigDecimal(string, mathContext));
    }

    /**
      * Only for NaN this should be true
      * @return false if this implementation is not NaN
      */
    default boolean isNaN(){
        return false;
    }



    /**
     * Converts this {@code num} to a {@code double}.
     * @return this {@code num} converted to a {@code double}
     */
    default double doubleValue(){
        return getDelegate().doubleValue();
    }

    default int intValue(){
        return getDelegate().intValue();
    }

    default long longValue(){
        return getDelegate().longValue();
    }

    default float floatValue(){
        return getDelegate().floatValue();
    }


    @Override
    int hashCode();

    @Override
    String toString();

    /**
     * {@inheritDoc}
     * @apiNote: This method should return true if `this` and `obj` are both NaN.
     */
    @Override
    boolean equals(Object obj);

}
