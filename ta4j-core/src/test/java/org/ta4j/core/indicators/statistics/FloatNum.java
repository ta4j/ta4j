/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import static org.ta4j.core.num.NaN.NaN;

import java.math.BigDecimal;
import java.math.MathContext;

import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Test-only {@link Num} backed by a {@code float} delegate, mirroring
 * {@link org.ta4j.core.num.DoubleNum} with single-precision arithmetic.
 *
 * <p>
 * The ta4j core ships no float-backed {@code Num}; this implementation exists
 * to exercise the primitive-backed accumulation guards in
 * {@link DynamicTimeWarpingSupport}, whose overflow ceiling must match the
 * delegate domain.
 */
class FloatNum implements Num {

    private static final long serialVersionUID = 1L;

    private final float delegate;

    private FloatNum(final float val) {
        this.delegate = val;
    }

    static FloatNum valueOf(final Number val) {
        return new FloatNum(val.floatValue());
    }

    static FloatNum valueOf(final String val) {
        try {
            return new FloatNum(Float.parseFloat(val));
        } catch (NumberFormatException e) {
            throw new NumberFormatException("not a float: \"" + val + "\"");
        }
    }

    @Override
    public NumFactory getNumFactory() {
        return SinglePrecisionNumFactory.getInstance();
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public Float getDelegate() {
        return this.delegate;
    }

    @Override
    public BigDecimal bigDecimalValue() {
        return Float.isNaN(this.delegate) || Float.isInfinite(this.delegate) ? null
                : new BigDecimal(Float.toString(this.delegate));
    }

    @Override
    public Num plus(final Num augend) {
        return augend.isNaN() ? NaN : new FloatNum(this.delegate + ((FloatNum) augend).delegate);
    }

    @Override
    public Num minus(final Num subtrahend) {
        return subtrahend.isNaN() ? NaN : new FloatNum(this.delegate - ((FloatNum) subtrahend).delegate);
    }

    @Override
    public Num multipliedBy(final Num multiplicand) {
        return multiplicand.isNaN() ? NaN : new FloatNum(this.delegate * ((FloatNum) multiplicand).delegate);
    }

    @Override
    public Num dividedBy(final Num divisor) {
        if (divisor.isNaN() || divisor.isZero()) {
            return NaN;
        }
        return new FloatNum(this.delegate / ((FloatNum) divisor).delegate);
    }

    @Override
    public Num remainder(final Num divisor) {
        return divisor.isNaN() ? NaN : new FloatNum(this.delegate % ((FloatNum) divisor).delegate);
    }

    @Override
    public Num floor() {
        return new FloatNum((float) Math.floor(this.delegate));
    }

    @Override
    public Num ceil() {
        return new FloatNum((float) Math.ceil(this.delegate));
    }

    @Override
    public Num pow(final int n) {
        return new FloatNum((float) Math.pow(this.delegate, n));
    }

    @Override
    public Num pow(final Num n) {
        return new FloatNum((float) Math.pow(this.delegate, n.doubleValue()));
    }

    @Override
    public Num sqrt() {
        if (this.delegate < 0) {
            return NaN;
        }
        return new FloatNum((float) Math.sqrt(this.delegate));
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
        return new FloatNum((float) Math.log(this.delegate));
    }

    @Override
    public Num exp() {
        return new FloatNum((float) Math.exp(this.delegate));
    }

    @Override
    public Num abs() {
        return new FloatNum(Math.abs(this.delegate));
    }

    @Override
    public Num negate() {
        return new FloatNum(-this.delegate);
    }

    @Override
    public boolean isZero() {
        return this.delegate == 0;
    }

    @Override
    public boolean isNaN() {
        return Float.isNaN(this.delegate);
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
        return !other.isNaN() && this.delegate == ((FloatNum) other).delegate;
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
        return other.isNaN() ? NaN : new FloatNum(Math.min(this.delegate, ((FloatNum) other).delegate));
    }

    @Override
    public Num max(final Num other) {
        return other.isNaN() ? NaN : new FloatNum(Math.max(this.delegate, ((FloatNum) other).delegate));
    }

    @Override
    public int hashCode() {
        return Float.valueOf(this.delegate).hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof final FloatNum floatNumObj)) {
            return false;
        }
        // Exact comparison: tolerance would break the equals/hashCode
        // contract (distinct values comparing equal while Float.hashCode
        // stays distinct) and NaN would never equal itself. Float.compare
        // treats all NaN bit patterns as one canonical value, matching
        // Float.hashCode.
        return Float.compare(this.delegate, floatNumObj.delegate) == 0;
    }

    @Override
    public int compareTo(final Num o) {
        if (o.isNaN()) {
            return 0;
        }
        return Float.compare(this.delegate, ((FloatNum) o).delegate);
    }

    @Override
    public String toString() {
        return Float.toString(this.delegate);
    }
}
