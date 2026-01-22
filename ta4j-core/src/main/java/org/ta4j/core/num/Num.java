/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.num;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Ta4js definition of operations that must be fulfilled by an object that
 * should be used as base for calculations.
 *
 * @see Num
 * @see DoubleNum
 * @see DecimalNum
 */
public interface Num extends Comparable<Num>, Serializable {

    /**
     * @return the delegate used from this {@code Num} implementation
     */
    Number getDelegate();

    /**
     * @return factory that created this instance with defined precision
     */
    NumFactory getNumFactory();

    /**
     * Returns the name/description of this Num implementation.
     *
     * @return the name/description
     */
    String getName();

    /**
     * Returns a {@code Num} whose value is {@code (this + augend)}.
     *
     * @param augend value to be added to this {@code Num}
     * @return {@code this + augend}, rounded as necessary
     */
    Num plus(Num augend);

    /**
     * Returns a {@code Num} whose value is {@code (this - augend)}.
     *
     * @param subtrahend value to be subtracted from this {@code Num}
     * @return {@code this - subtrahend}, rounded as necessary
     */
    Num minus(Num subtrahend);

    /**
     * Returns a {@code Num} whose value is {@code this * multiplicand}.
     *
     * @param multiplicand value to be multiplied by this {@code Num}
     * @return {@code this * multiplicand}, rounded as necessary
     */
    Num multipliedBy(Num multiplicand);

    /**
     * Returns a {@code Num} whose value is {@code (this / divisor)}.
     *
     * @param divisor value by which this {@code Num} is to be divided
     * @return {@code this / divisor}, rounded as necessary
     */
    Num dividedBy(Num divisor);

    /**
     * Returns a {@code Num} whose value is {@code (this % divisor)}.
     *
     * @param divisor value by which this {@code Num} is to be divided
     * @return {@code this % divisor}, rounded as necessary
     */
    Num remainder(Num divisor);

    /**
     * Returns a {@code Num} whose value is rounded down to the nearest whole
     * number.
     *
     * @return {@code this} to whole Num rounded down
     */
    Num floor();

    /**
     * Returns a {@code Num} whose value is rounded up to the nearest whole number.
     *
     * @return {@code this} to whole Num rounded up
     */
    Num ceil();

    /**
     * Returns a {@code Num} whose value is <code>(this<sup>n</sup>)</code>.
     *
     * @param n power to raise this {@code Num} to.
     * @return <code>this<sup>n</sup></code>
     */
    Num pow(int n);

    /**
     * Returns a {@code Num} whose value is <code>(this<sup>n</sup>)</code>.
     *
     * @param n power to raise this {@code Num} to.
     * @return <code>this<sup>n</sup></code>
     */
    Num pow(Num n);

    /**
     * Returns a {@code Num} whose value is {@code log(this)}.
     *
     * @return {@code log(this)}
     */
    Num log();

    /**
     * Returns a {@code Num} whose value is {@code e^this}.
     *
     * @return {@code e^this}
     */
    Num exp();

    /**
     * Returns a {@code Num} whose value is {@code √(this)}.
     *
     * @return {@code √(this)}
     */
    Num sqrt();

    /**
     * Returns a {@code Num} whose value is {@code √(this)}.
     *
     * @param mathContext to calculate.
     * @return {@code √(this)}
     */
    Num sqrt(MathContext mathContext);

    /**
     * Returns a {@code Num} whose value is the absolute value of this {@code Num}.
     *
     * @return {@code abs(this)}
     */
    Num abs();

    /**
     * Returns a {@code Num} whose value is (-this), and whose scale is
     * this.scale().
     *
     * @return {@code negate(this)}
     */
    Num negate();

    /**
     * Checks if {@code this} is zero.
     *
     * @return true if {@code this == 0}, false otherwise
     */
    boolean isZero();

    /**
     * Checks if {@code this} is greater than zero.
     *
     * @return true if {@code this > 0}, false otherwise
     */
    boolean isPositive();

    /**
     * Checks if {@code this} is zero or greater.
     *
     * @return true if {@code this ≥ 0}, false otherwise
     */
    boolean isPositiveOrZero();

    /**
     * Checks if {@code this} is less than zero.
     *
     * @return true if {@code this < 0}, false otherwise
     */
    boolean isNegative();

    /**
     * Checks if {@code this} is zero or less.
     *
     * @return true if {@code this ≤ 0}, false otherwise
     */
    boolean isNegativeOrZero();

    /**
     * Checks if {@code this} is equal to {@code other}.
     *
     * @param other the other value, not null
     * @return true if {@code this == other}, false otherwise
     */
    boolean isEqual(Num other);

    /**
     * Checks if {@code this} is greater than {@code other}.
     *
     * @param other the other value, not null
     * @return true if {@code this > other}, false otherwise
     */
    boolean isGreaterThan(Num other);

    /**
     * Checks if {@code this} is greater than or equal to {@code other}.
     *
     * @param other the other value, not null
     * @return true if {@code this ≥ other}, false otherwise
     */
    boolean isGreaterThanOrEqual(Num other);

    /**
     * Checks if {@code this} is less than {@code other}.
     *
     * @param other the other value, not null
     * @return true if {@code this < other}, false otherwise
     */
    boolean isLessThan(Num other);

    /**
     * Checks if {@code this} is less than {@code other}.
     *
     * @param other the other value, not null
     * @return true if {@code this ≤ other}, false otherwise
     */
    boolean isLessThanOrEqual(Num other);

    /**
     * Returns the {@code Num} whose value is the smaller of {@code this} and
     * {@code other}.
     *
     * @param other value with which to calculate the minimum
     * @return the smaller of {@code this} and {@code other}. If they are equal,
     *         {@code this} is returned.
     */
    Num min(Num other);

    /**
     * Returns the {@code Num} whose value is the greater of {@code this} and
     * {@code other}.
     *
     * @param other value with which to calculate the maximum
     * @return the greater of {@code this} and {@code other}. If they are equal,
     *         {@code this} is returned.
     */
    Num max(Num other);

    /**
     * Returns true only if {@code this} is an instance of {@link NaN}.
     *
     * @return false if this implementation is not {@link NaN}
     */
    default boolean isNaN() {
        return false;
    }

    /**
     * Converts this {@code Num} to an {@code integer}.
     *
     * @return this {@code Num} converted to an {@code integer}
     */
    default int intValue() {
        return getDelegate().intValue();
    }

    /**
     * Converts this {@code Num} to a {@code long}.
     *
     * @return this {@code Num} converted to a {@code long}
     */
    default long longValue() {
        return getDelegate().longValue();
    }

    /**
     * Converts this {@code Num} to a {@code float}.
     *
     * @return this {@code Num} converted to a {@code float}
     */
    default float floatValue() {
        return getDelegate().floatValue();
    }

    /**
     * Converts this {@code Num} to a {@code double}.
     *
     * @return this {@code Num} converted to a {@code double}
     */
    default double doubleValue() {
        return getDelegate().doubleValue();
    }

    /**
     * Checks if a Num value is null or NaN.
     *
     * <p>
     * This method performs comprehensive NaN detection by checking:
     * <ul>
     * <li>If the value is null</li>
     * <li>If {@link Num#isNaN()} returns true</li>
     * <li>If the underlying double value is {@link Double#NaN} (handles DoubleNum
     * edge cases)</li>
     * </ul>
     *
     * <p>
     * This is necessary because {@link DoubleNumFactory} can surface
     * {@link Double#NaN} values that may not satisfy {@link Num#isNaN()}.
     *
     * @param value the value to check, may be null
     * @return true if the value is null or NaN, false otherwise
     */
    static boolean isNaNOrNull(Num value) {
        return value == null || value.isNaN() || Double.isNaN(value.doubleValue());
    }

    /**
     * Checks if a Num value is valid (not null and not NaN).
     *
     * <p>
     * This is the logical complement of {@link #isNaNOrNull(Num)}. A value is
     * considered valid if it is non-null and represents a real number (not NaN).
     *
     * @param value the value to check, may be null
     * @return true if the value is non-null and not NaN, false otherwise
     * @since 0.22.0
     */
    static boolean isValid(Num value) {
        return !isNaNOrNull(value);
    }

    /**
     * Converts this {@code Num} to a {@code BigDecimal}.
     *
     * @return this {@code Num} converted to a {@code BigDecimal}
     */
    BigDecimal bigDecimalValue();

    @Override
    int hashCode();

    @Override
    String toString();

    /**
     * {@inheritDoc}
     */
    @Override
    boolean equals(Object obj);

}
