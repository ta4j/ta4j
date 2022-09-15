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
package org.ta4j.core.num;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Representation of Double. High performance, lower precision.
 *
 * @apiNote the delegate should never become a NaN value. No self NaN checks
 * provided
 */
public class DoubleNum implements Num {
    private static final DoubleNum DZERO = new DoubleNum(0d);
    private static final DoubleNum DNZERO = new DoubleNum(-0.0d);
    private static final DoubleNum D1_0 = new DoubleNum(1d);

    private static final DoubleNum HUNDRED = DoubleNum.valueOf(100d);
    public static Map<Double, DoubleNum> cache = new WeakHashMap<>();
    public static boolean useCache = false;

    public static DoubleNum bind(double delegate) {
        if (0d == delegate) return DZERO;
        if (1d == delegate) return D1_0;
        if (-0.0d == delegate) return DNZERO;
        if (!useCache) return new DoubleNum(delegate); //this should be a basic case for the C2 var profiler inlining

        DoubleNum ref = cache.get(delegate);
        if (null == ref) {
            ref = new DoubleNum(delegate);
            cache.put(delegate, ref);
        }
        return ref;

    }


    private final static double EPS = 0.00001; // precision
    public static final long negativeZeroDoubleBits = Double.doubleToRawLongBits(-0.0d);
    private final double delegate;

    private DoubleNum(double val) {
        delegate = val;
    }

    public static DoubleNum valueOf(int i) {
        return bind(i);
    }

    public static DoubleNum valueOf(long i) {
        return bind((double) i);
    }

    public static DoubleNum valueOf(short i) {
        return bind(i);
    }

    public static DoubleNum valueOf(float i) {
        return bind(i);
    }

    public static DoubleNum valueOf(String i) {
        return bind(Double.parseDouble(i));
    }


    public static DoubleNum valueOf(Number i) {
        /*@see https://github.com/ta4j/ta4j/issues/731#issuecomment-1172802423*/
        /*double delegate1 = Double.parseDouble(i.toString());*/
        return bind(i.doubleValue());
    }
    @Override
    public Num zero() {
        return DZERO;
    }

    @Override
    public Num one() {
        return D1_0;
    }

    @Override
    public Num hundred() {
        return HUNDRED;
    }

    @Override
    public Function<Number, Num> function() {
        return DoubleNum::valueOf;
    }

    @Override
    public Double getDelegate() {
        return delegate;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public Num plus(Num augend) {
        if (augend.isNaN()) return NaN;

        if (0d == delegate || -0d == delegate) return augend;
        double v = augend.doubleValue();
        if (0d == v || -0d == v) return this;
        return bind(delegate + v);
    }

    @Override
    public Num minus(Num subtrahend) {
        if (subtrahend.isNaN()) return NaN;
        double v = subtrahend.doubleValue();
        if (0d == v || -0d == v) return this;
        if (v == delegate) return DZERO;
        return bind(delegate - v);
    }

    @Override
    public Num multipliedBy(Num multiplicand) {
        if (multiplicand.isNaN()) return NaN;
        double v = multiplicand.doubleValue();
        return 0d == v || 1d == delegate ? multiplicand : 1d == v || 0d == delegate ? this : bind(delegate * v);
    }

    @Override
    public Num dividedBy(Num divisor) {
        if (divisor.isNaN() || divisor.isZero()) return NaN;
        double v = divisor.doubleValue();
        if (1d == v || 0d == delegate)
            return this;

        return bind(delegate / v);
    }

    @Override
    public Num remainder(Num divisor) {
        if (divisor.isNaN()) return NaN;
        double v = divisor.doubleValue();
        return 1d == v ? this : bind(delegate % v);
    }

    @Override
    public Num floor() {
        return bind(Math.floor(delegate));
    }

    @Override
    public Num ceil() {
        return bind(Math.ceil(delegate));
    }

    @Override
    public Num pow(int n) {
        return bind(Math.pow(delegate, n));
    }

    @Override
    public Num pow(Num n) {
        double b = n.doubleValue();
        return 1d == b ? this : bind(Math.pow(delegate, b));
    }

    @Override
    public Num sqrt() {
        if (delegate < 0) {
            return NaN;
        }
        return bind(Math.sqrt(delegate));
    }

    @Override
    public Num sqrt(int precision) {
        return sqrt();
    }

    @Override
    public Num abs() {
        return bind(Math.abs(delegate));
    }

    @Override
    public Num negate() {
        return bind(-delegate);
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

    public Num log() {
        if (delegate <= 0) {
            return NaN;
        }
        return bind(Math.log(delegate));
    }

    /**
     * Checks if this value is greater than another.
     *
     * @param other the other value, not null
     * @return true is this is greater than the specified value, false otherwise
     */
    public boolean isGreaterThan(Num other) {
        return !other.isNaN() && compareTo(other) > 0;
    }

    /**
     * Checks if this value is greater than or equal to another.
     *
     * @param other the other value, not null
     * @return true is this is greater than or equal to the specified value, false
     * otherwise
     */
    public boolean isGreaterThanOrEqual(Num other) {
        return !other.isNaN() && compareTo(other) > -1;
    }

    /**
     * Checks if this value is less than another.
     *
     * @param other the other value, not null
     * @return true is this is less than the specified value, false otherwise
     */
    public boolean isLessThan(Num other) {
        return !other.isNaN() && compareTo(other) < 0;
    }

    @Override
    public boolean isLessThanOrEqual(Num other) {
        return !other.isNaN() && compareTo(other) < 1;
    }

    @Override
    public Num min(Num other) {
        double a = delegate;
        //noinspection ConstantConditions
        if (a != a)
            return NaN;   // a is NaN
        double b = other.getDelegate().doubleValue();
        if ((a == 0d) &&
                (b == 0d) &&
                (Double.doubleToRawLongBits(b) == negativeZeroDoubleBits)) {
            // Raw conversion ok since NaN can't map to -0d.
            return other;
        }
        return (a <= b) ? this : other;

    }

    @Override
    public Num max(Num other) {
        double a = delegate;
        //noinspection ConstantConditions
        if (a != a) return NaN;
        double b = (double) other.getDelegate();
        if ((a == 0d) &&
                (b == 0d) &&
                (Double.doubleToRawLongBits(a) == negativeZeroDoubleBits)) {
            // Raw conversion ok since NaN can't map to -0d.
            return other;
        }
        return (a >= b) ? this : other;
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
