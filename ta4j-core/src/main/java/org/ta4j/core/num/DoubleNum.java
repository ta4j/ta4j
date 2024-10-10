/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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
package org.ta4j.core.num;

import static org.ta4j.core.num.NaN.NaN;

import java.math.BigDecimal;

/**
 * Representation of {@link Double}. High performance, lower precision.
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

    private DoubleNum(double val) {
        delegate = val;
    }

    /**
     * Returns a {@code Num} version of the given {@code String}.
     *
     * @param val the number
     * @return the {@code Num}
     */
    public static DoubleNum valueOf(String val) {
        return new DoubleNum(Double.parseDouble(val));
    }

    /**
     * Returns a {@code Num} version of the given {@code Number}.
     *
     * @param i the number
     * @return the {@code Num}
     */
    public static DoubleNum valueOf(Number i) {
        return new DoubleNum(i.doubleValue());
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
    public static DoubleNum valueOf(DecimalNum val) {
        return valueOf(val.toString());
    }

    /**
     * Returns a {@code Num} version of the given {@code int}.
     *
     * @param val the number
     * @return the {@code Num}
     */
    public static DoubleNum valueOf(int val) {
        return new DoubleNum(val);
    }

    /**
     * Returns a {@code Num} version of the given {@code long}.
     *
     * @param val the number
     * @return the {@code Num}
     */
    public static DoubleNum valueOf(long val) {
        return new DoubleNum(val);
    }

    /**
     * Returns a {@code Num} version of the given {@code short}.
     *
     * @param val the number
     * @return the {@code Num}
     */
    public static DoubleNum valueOf(short val) {
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
    public static DoubleNum valueOf(float val) {
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
        return delegate;
    }

    @Override
    public BigDecimal bigDecimalValue() {
        return Double.isNaN(delegate) || Double.isInfinite(delegate) ? null : BigDecimal.valueOf(delegate);
    }

    @Override
    public Num plus(Num augend) {
        return augend.isNaN() ? NaN : new DoubleNum(delegate + ((DoubleNum) augend).delegate);
    }

    @Override
    public Num minus(Num subtrahend) {
        return subtrahend.isNaN() ? NaN : new DoubleNum(delegate - ((DoubleNum) subtrahend).delegate);
    }

    @Override
    public Num multipliedBy(Num multiplicand) {
        return multiplicand.isNaN() ? NaN : new DoubleNum(delegate * ((DoubleNum) multiplicand).delegate);
    }

    @Override
    public Num dividedBy(Num divisor) {
        if (divisor.isNaN() || divisor.isZero()) {
            return NaN;
        }
        DoubleNum divisorD = (DoubleNum) divisor;
        return new DoubleNum(delegate / divisorD.delegate);
    }

    @Override
    public Num remainder(Num divisor) {
        return divisor.isNaN() ? NaN : new DoubleNum(delegate % ((DoubleNum) divisor).delegate);
    }

    @Override
    public Num floor() {
        return new DoubleNum(Math.floor(delegate));
    }

    @Override
    public Num ceil() {
        return new DoubleNum(Math.ceil(delegate));
    }

    @Override
    public Num pow(int n) {
        return new DoubleNum(Math.pow(delegate, n));
    }

    @Override
    public Num pow(Num n) {
        return new DoubleNum(Math.pow(delegate, n.doubleValue()));
    }

    @Override
    public Num sqrt() {
        if (delegate < 0) {
            return NaN;
        }
        return new DoubleNum(Math.sqrt(delegate));
    }

    @Override
    public Num sqrt(int precision) {
        return sqrt();
    }

    @Override
    public Num abs() {
        return new DoubleNum(Math.abs(delegate));
    }

    @Override
    public Num negate() {
        return new DoubleNum(-delegate);
    }

    @Override
    public boolean isZero() {
        return delegate == 0;
    }

    @Override
    public boolean isPositive() {
        return delegate > 0;
    }

    @Override
    public boolean isPositiveOrZero() {
        return delegate >= 0;
    }

    @Override
    public boolean isNegative() {
        return delegate < 0;
    }

    @Override
    public boolean isNegativeOrZero() {
        return delegate <= 0;
    }

    @Override
    public boolean isEqual(Num other) {
        return !other.isNaN() && delegate == ((DoubleNum) other).delegate;
    }

    @Override
    public Num log() {
        if (delegate <= 0) {
            return NaN;
        }
        return new DoubleNum(Math.log(delegate));
    }

    @Override
    public boolean isGreaterThan(Num other) {
        return !other.isNaN() && compareTo(other) > 0;
    }

    @Override
    public boolean isGreaterThanOrEqual(Num other) {
        return !other.isNaN() && compareTo(other) > -1;
    }

    @Override
    public boolean isLessThan(Num other) {
        return !other.isNaN() && compareTo(other) < 0;
    }

    @Override
    public boolean isLessThanOrEqual(Num other) {
        return !other.isNaN() && compareTo(other) < 1;
    }

    @Override
    public Num min(Num other) {
        return other.isNaN() ? NaN : new DoubleNum(Math.min(delegate, ((DoubleNum) other).delegate));
    }

    @Override
    public Num max(Num other) {
        return other.isNaN() ? NaN : new DoubleNum(Math.max(delegate, ((DoubleNum) other).delegate));
    }

    @Override
    public int hashCode() {
        return ((Double) (delegate)).hashCode();
    }

    @Override
    public String toString() {
        return Double.toString(delegate);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DoubleNum)) {
            return false;
        }

        DoubleNum doubleNumObj = (DoubleNum) obj;
        return Math.abs(delegate - doubleNumObj.delegate) < EPS;
    }

    @Override
    public int compareTo(Num o) {
        if (this == NaN || o == NaN) {
            return 0;
        }
        DoubleNum doubleNumO = (DoubleNum) o;
        return Double.compare(delegate, doubleNumO.delegate);
    }
}
