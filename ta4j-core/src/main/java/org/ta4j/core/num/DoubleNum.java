/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.num;

import static org.ta4j.core.num.NaN.NaN;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Representation of {@link Double}. High performance, lower precision.
 *
 * *
 * <p>
 * It uses a precision of up to {@value #EPS} decimal places.
 *
 * @apiNote the delegate should never become a NaN value. No self NaN checks are
 *          provided.
 */
public class DoubleNum implements Num {

    private static final long serialVersionUID = 1L;

    public static final DoubleNum MINUS_ONE = DoubleNum.valueOf(-1);
    public static final DoubleNum ZERO = DoubleNum.valueOf(0);
    public static final DoubleNum ONE = DoubleNum.valueOf(1);
    public static final DoubleNum TWO = DoubleNum.valueOf(2);
    public static final DoubleNum THREE = DoubleNum.valueOf(3);
    public static final DoubleNum HUNDRED = DoubleNum.valueOf(100);
    public static final DoubleNum THOUSAND = DoubleNum.valueOf(1000);

    private final static double EPS = 0.00001; // precision
    private final double delegate;

    private DoubleNum(final double val) {
        this.delegate = val;
    }

    /**
     * Returns a {@code Num} version of the given {@code String}.
     *
     * @param val the number
     * @return the {@code Num}
     */
    public static DoubleNum valueOf(final String val) {
        return new DoubleNum(Double.parseDouble(val));
    }

    /**
     * Returns a {@code Num} version of the given {@code Number}.
     *
     * @param val the number
     * @return the {@code Num}
     */
    public static DoubleNum valueOf(final Number val) {
        return new DoubleNum(val.doubleValue());
    }

    /**
     * Returns a {@code DoubleNum} version of the given {@code DecimalNum}.
     *
     * <p>
     * <b>Warning:</b> The {@code Num} returned may have inaccuracies.
     *
     * @param val the number
     * @return the {@code Num} whose value is equal to or approximately equal to the
     *         value of {@code val}.
     */
    public static DoubleNum valueOf(final DecimalNum val) {
        return valueOf(val.toString());
    }

    /**
     * Returns a {@code Num} version of the given {@code int}.
     *
     * @param val the number
     * @return the {@code Num}
     */
    public static DoubleNum valueOf(final int val) {
        return new DoubleNum(val);
    }

    /**
     * Returns a {@code Num} version of the given {@code long}.
     *
     * @param val the number
     * @return the {@code Num}
     */
    public static DoubleNum valueOf(final long val) {
        return new DoubleNum(val);
    }

    /**
     * Returns a {@code Num} version of the given {@code short}.
     *
     * @param val the number
     * @return the {@code Num}
     */
    public static DoubleNum valueOf(final short val) {
        return new DoubleNum(val);
    }

    /**
     * Returns a {@code Num} version of the given {@code float}.
     *
     * <p>
     * <b>Warning:</b> The {@code Num} returned may have inaccuracies.
     *
     * @param val the number
     * @return the {@code Num} whose value is equal to or approximately equal to the
     *         value of {@code val}.
     */
    public static DoubleNum valueOf(final float val) {
        return new DoubleNum(val);
    }

    @Override
    public NumFactory getNumFactory() {
        return DoubleNumFactory.getInstance();
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public Double getDelegate() {
        return this.delegate;
    }

    @Override
    public BigDecimal bigDecimalValue() {
        return Double.isNaN(this.delegate) || Double.isInfinite(this.delegate) ? null
                : BigDecimal.valueOf(this.delegate);
    }

    @Override
    public Num plus(final Num augend) {
        return augend.isNaN() ? NaN : new DoubleNum(this.delegate + ((DoubleNum) augend).delegate);
    }

    @Override
    public Num minus(final Num subtrahend) {
        return subtrahend.isNaN() ? NaN : new DoubleNum(this.delegate - ((DoubleNum) subtrahend).delegate);
    }

    @Override
    public Num multipliedBy(final Num multiplicand) {
        return multiplicand.isNaN() ? NaN : new DoubleNum(this.delegate * ((DoubleNum) multiplicand).delegate);
    }

    @Override
    public Num dividedBy(final Num divisor) {
        if (divisor.isNaN() || divisor.isZero()) {
            return NaN;
        }
        final DoubleNum divisorD = (DoubleNum) divisor;
        return new DoubleNum(this.delegate / divisorD.delegate);
    }

    @Override
    public Num remainder(final Num divisor) {
        return divisor.isNaN() ? NaN : new DoubleNum(this.delegate % ((DoubleNum) divisor).delegate);
    }

    @Override
    public Num floor() {
        return new DoubleNum(Math.floor(this.delegate));
    }

    @Override
    public Num ceil() {
        return new DoubleNum(Math.ceil(this.delegate));
    }

    @Override
    public Num pow(final int n) {
        return new DoubleNum(Math.pow(this.delegate, n));
    }

    @Override
    public Num pow(final Num n) {
        return new DoubleNum(Math.pow(this.delegate, n.doubleValue()));
    }

    @Override
    public Num sqrt() {
        if (this.delegate < 0) {
            return NaN;
        }
        return new DoubleNum(Math.sqrt(this.delegate));
    }

    @Override
    public Num sqrt(final MathContext mathContext) {
        return sqrt();
    }

    @Override
    public Num log() {
        if (this.delegate <= 0) {
            return NaN;
        }
        return new DoubleNum(Math.log(this.delegate));
    }

    @Override
    public Num exp() {
        return new DoubleNum(Math.exp(this.delegate));
    }

    @Override
    public Num abs() {
        return new DoubleNum(Math.abs(this.delegate));
    }

    @Override
    public Num negate() {
        return new DoubleNum(-this.delegate);
    }

    @Override
    public boolean isZero() {
        return this.delegate == 0;
    }

    @Override
    public boolean isPositive() {
        return this.delegate > 0;
    }

    @Override
    public boolean isPositiveOrZero() {
        return this.delegate >= 0;
    }

    @Override
    public boolean isNegative() {
        return this.delegate < 0;
    }

    @Override
    public boolean isNegativeOrZero() {
        return this.delegate <= 0;
    }

    @Override
    public boolean isEqual(final Num other) {
        return !other.isNaN() && this.delegate == ((DoubleNum) other).delegate;
    }

    @Override
    public boolean isGreaterThan(final Num other) {
        return !other.isNaN() && compareTo(other) > 0;
    }

    @Override
    public boolean isGreaterThanOrEqual(final Num other) {
        return !other.isNaN() && compareTo(other) > -1;
    }

    @Override
    public boolean isLessThan(final Num other) {
        return !other.isNaN() && compareTo(other) < 0;
    }

    @Override
    public boolean isLessThanOrEqual(final Num other) {
        return !other.isNaN() && compareTo(other) < 1;
    }

    @Override
    public Num min(final Num other) {
        return other.isNaN() ? NaN : new DoubleNum(Math.min(this.delegate, ((DoubleNum) other).delegate));
    }

    @Override
    public Num max(final Num other) {
        return other.isNaN() ? NaN : new DoubleNum(Math.max(this.delegate, ((DoubleNum) other).delegate));
    }

    @Override
    public int hashCode() {
        return ((Double) (this.delegate)).hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof final DoubleNum doubleNumObj)) {
            return false;
        }

        return Math.abs(this.delegate - doubleNumObj.delegate) < EPS;
    }

    @Override
    public int compareTo(final Num o) {
        if (this == NaN || o == NaN) {
            return 0;
        }
        final DoubleNum doubleNumO = (DoubleNum) o;
        return Double.compare(this.delegate, doubleNumO.delegate);
    }

    @Override
    public String toString() {
        return Double.toString(this.delegate);
    }
}
