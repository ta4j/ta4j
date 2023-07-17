/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Representation of arbitrary precision {@link BigDecimal}. A {@code Num}
 * consists of a {@code BigDecimal} with arbitrary {@link MathContext}
 * (precision and rounding mode).
 *
 * @see BigDecimal
 * @see MathContext
 * @see RoundingMode
 * @see Num
 */
public final class DecimalNum implements Num {

    private static final int DEFAULT_PRECISION = 32;
    private static final Logger log = LoggerFactory.getLogger(DecimalNum.class);

    public static final DecimalNum ZERO = DecimalNum.valueOf(0);
    private static final DecimalNum ONE = DecimalNum.valueOf(1);
    private static final DecimalNum HUNDRED = DecimalNum.valueOf(100);

    private final MathContext mathContext;
    private final BigDecimal delegate;

    /**
     * Constructor.
     * 
     * <p>
     * Constructs the most precise {@code Num}, because it converts a {@code String}
     * to a {@code Num} with a precision of {@link #DEFAULT_PRECISION}; only a
     * string parameter can accurately represent a value.
     *
     * @param val the string representation of the Num value
     */
    private DecimalNum(String val) {
        delegate = new BigDecimal(val);
        int precision = Math.max(delegate.precision(), DEFAULT_PRECISION);
        mathContext = new MathContext(precision, RoundingMode.HALF_UP);
    }

    /**
     * Constructor.
     * 
     * <p>
     * Constructs a more precise {@code Num} than from {@code double}, because it
     * converts a {@code String} to a {@code Num} with a precision of
     * {@code precision}; only a string parameter can accurately represent a value.
     *
     * @param val       the string representation of the Num value
     * @param precision the int precision of the Num value
     */
    private DecimalNum(String val, int precision) {
        mathContext = new MathContext(precision, RoundingMode.HALF_UP);
        delegate = new BigDecimal(val, new MathContext(precision, RoundingMode.HALF_UP));
    }

    private DecimalNum(short val) {
        mathContext = new MathContext(DEFAULT_PRECISION, RoundingMode.HALF_UP);
        delegate = new BigDecimal(val, mathContext);
    }

    private DecimalNum(int val) {
        mathContext = new MathContext(DEFAULT_PRECISION, RoundingMode.HALF_UP);
        delegate = BigDecimal.valueOf(val);
    }

    private DecimalNum(long val) {
        mathContext = new MathContext(DEFAULT_PRECISION, RoundingMode.HALF_UP);
        delegate = BigDecimal.valueOf(val);
    }

    private DecimalNum(float val) {
        mathContext = new MathContext(DEFAULT_PRECISION, RoundingMode.HALF_UP);
        delegate = new BigDecimal(val, mathContext);
    }

    private DecimalNum(double val) {
        mathContext = new MathContext(DEFAULT_PRECISION, RoundingMode.HALF_UP);
        delegate = BigDecimal.valueOf(val);
    }

    private DecimalNum(BigDecimal val, int precision) {
        mathContext = new MathContext(precision, RoundingMode.HALF_UP);
        delegate = Objects.requireNonNull(val);
    }

    /**
     * Returns a {@code Num} version of the given {@code String}.
     * 
     * <p>
     * Constructs the most precise {@code Num}, because it converts a {@code String}
     * to a {@code Num} with a precision of {@link #DEFAULT_PRECISION}; only a
     * string parameter can accurately represent a value.
     *
     * @param val the number
     * @return the {@code Num} with a precision of {@link #DEFAULT_PRECISION}
     * @throws NumberFormatException if {@code val} is {@code "NaN"}
     */
    public static DecimalNum valueOf(String val) {
        if (val.equalsIgnoreCase("NAN")) {
            throw new NumberFormatException();
        }
        return new DecimalNum(val);
    }

    /**
     * Returns a {@code Num} version of the given {@code String} with a precision of
     * {@code precision}.
     *
     * @param val       the number
     * @param precision the precision
     * @return the {@code Num} with a precision of {@code precision}
     * @throws NumberFormatException if {@code val} is {@code "NaN"}
     */
    public static DecimalNum valueOf(String val, int precision) {
        if (val.equalsIgnoreCase("NAN")) {
            throw new NumberFormatException();
        }
        return new DecimalNum(val, precision);
    }

    /**
     * Returns a {@code Num} version of the given {@code Number}.
     * 
     * <p>
     * Returns the most precise {@code Num}, because it first converts {@code val}
     * to a {@code String} and then to a {@code Num} with a precision of
     * {@link #DEFAULT_PRECISION}; only a string parameter can accurately represent
     * a value.
     *
     * @param val the number
     * @return the {@code Num} with a precision of {@link #DEFAULT_PRECISION}
     * @throws NumberFormatException if {@code val} is {@code "NaN"}
     */
    public static DecimalNum valueOf(Number val) {
        return valueOf(val.toString());
    }

    /**
     * Returns a {@code DecimalNum} version of the given {@code DoubleNum}.
     * 
     * <p>
     * Returns the most precise {@code Num}, because it first converts {@code val}
     * to a {@code String} and then to a {@code Num} with a precision of
     * {@link #DEFAULT_PRECISION}; only a string parameter can accurately represent
     * a value.
     *
     * @param val the number
     * @return the {@code Num} with a precision of {@link #DEFAULT_PRECISION}
     * @throws NumberFormatException if {@code val} is {@code "NaN"}
     */
    public static DecimalNum valueOf(DoubleNum val) {
        return valueOf(val.toString());
    }

    /**
     * Returns a {@code Num} version of the given {@code int}.
     *
     * @param val the number
     * @return the {@code Num}
     */
    public static DecimalNum valueOf(int val) {
        return new DecimalNum(val);
    }

    /**
     * Returns a {@code Num} version of the given {@code long}.
     *
     * @param val the number
     * @return the {@code Num}
     */
    public static DecimalNum valueOf(long val) {
        return new DecimalNum(val);
    }

    /**
     * Returns a {@code Num} version of the given {@code short}.
     *
     * @param val the number
     * @return the {@code Num}
     */
    public static DecimalNum valueOf(short val) {
        return new DecimalNum(val);
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
     * @throws NumberFormatException if {@code val} is {@code Float.NaN}
     */
    public static DecimalNum valueOf(float val) {
        if (Float.isNaN(val)) {
            throw new NumberFormatException();
        }
        return new DecimalNum(val);
    }

    /**
     * Returns a {@code Num} version of the given {@code double}.
     *
     * <p>
     * <b>Warning:</b> The {@code Num} returned may have inaccuracies.
     *
     * @param val the number
     * @return the {@code Num} whose value is equal to or approximately equal to the
     *         value of {@code val}.
     * @throws NumberFormatException if {@code val} is {@code Double.NaN}
     */
    public static DecimalNum valueOf(double val) {
        if (Double.isNaN(val)) {
            throw new NumberFormatException();
        }
        return new DecimalNum(val);
    }

    /**
     * Returns a {@code Num} version of the given {@code BigDecimal}.
     * 
     * <p>
     * <b>Warning:</b> The {@code Num} returned may have inaccuracies because it
     * only inherits the precision of {@code val}.
     *
     * @param val the number
     * @return the {@code Num}
     */
    public static DecimalNum valueOf(BigDecimal val) {
        return new DecimalNum(val, val.precision());
    }

    /**
     * Returns a {@code Num} version of the given {@code BigDecimal} with a
     * precision of {@code precision}.
     *
     * @param val       the number
     * @param precision the precision
     * @return the {@code Num}
     */
    public static DecimalNum valueOf(BigDecimal val, int precision) {
        return new DecimalNum(val, precision);
    }

    @Override
    public Num zero() {
        return mathContext.getPrecision() == DEFAULT_PRECISION ? ZERO : function().apply(0);
    }

    @Override
    public Num one() {
        return mathContext.getPrecision() == DEFAULT_PRECISION ? ONE : function().apply(1);
    }

    @Override
    public Num hundred() {
        return mathContext.getPrecision() == DEFAULT_PRECISION ? HUNDRED : function().apply(100);
    }

    @Override
    public Function<Number, Num> function() {
        return (number -> DecimalNum.valueOf(number.toString(), mathContext.getPrecision()));
    }

    /**
     * Returns the underlying {@link BigDecimal} delegate.
     *
     * @return BigDecimal delegate instance of this instance
     */
    @Override
    public BigDecimal getDelegate() {
        return delegate;
    }

    /**
     * Returns the underlying {@link MathContext} mathContext.
     *
     * @return MathContext of this instance
     */
    public MathContext getMathContext() {
        return mathContext;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public Num plus(Num augend) {
        if (augend.isNaN()) {
            return NaN;
        }
        BigDecimal bigDecimal = ((DecimalNum) augend).delegate;
        int precision = mathContext.getPrecision();
        BigDecimal result = delegate.add(bigDecimal, mathContext);
        return new DecimalNum(result, precision);
    }

    /**
     * Returns a {@code Num} whose value is {@code (this - augend)}, with rounding
     * according to the context settings.
     *
     * @see BigDecimal#subtract(java.math.BigDecimal, java.math.MathContext)
     */
    @Override
    public Num minus(Num subtrahend) {
        if (subtrahend.isNaN()) {
            return NaN;
        }
        BigDecimal bigDecimal = ((DecimalNum) subtrahend).delegate;
        int precision = mathContext.getPrecision();
        BigDecimal result = delegate.subtract(bigDecimal, mathContext);
        return new DecimalNum(result, precision);
    }

    /**
     * Returns a {@code Num} whose value is {@code this * multiplicand}, with
     * rounding according to the context settings.
     *
     * @see BigDecimal#multiply(java.math.BigDecimal, java.math.MathContext)
     */
    @Override
    public Num multipliedBy(Num multiplicand) {
        if (multiplicand.isNaN()) {
            return NaN;
        }
        BigDecimal bigDecimal = ((DecimalNum) multiplicand).delegate;
        int precision = mathContext.getPrecision();
        BigDecimal result = delegate.multiply(bigDecimal, new MathContext(precision, RoundingMode.HALF_UP));
        return new DecimalNum(result, precision);
    }

    /**
     * Returns a {@code Num} whose value is {@code (this / divisor)}, with rounding
     * according to the context settings.
     *
     * @see BigDecimal#divide(java.math.BigDecimal, java.math.MathContext)
     */
    @Override
    public Num dividedBy(Num divisor) {
        if (divisor.isNaN() || divisor.isZero()) {
            return NaN;
        }
        BigDecimal bigDecimal = ((DecimalNum) divisor).delegate;
        int precision = mathContext.getPrecision();
        BigDecimal result = delegate.divide(bigDecimal, new MathContext(precision, RoundingMode.HALF_UP));
        return new DecimalNum(result, precision);
    }

    /**
     * Returns a {@code Num} whose value is {@code (this % divisor)}, with rounding
     * according to the context settings.
     *
     * @see BigDecimal#remainder(java.math.BigDecimal, java.math.MathContext)
     */
    @Override
    public Num remainder(Num divisor) {
        if (divisor.isNaN()) {
            return NaN;
        }
        BigDecimal bigDecimal = ((DecimalNum) divisor).delegate;
        int precision = mathContext.getPrecision();
        BigDecimal result = delegate.remainder(bigDecimal, new MathContext(precision, RoundingMode.HALF_UP));
        return new DecimalNum(result, precision);
    }

    @Override
    public Num floor() {
        int precision = Math.max(mathContext.getPrecision(), DEFAULT_PRECISION);
        return new DecimalNum(delegate.setScale(0, RoundingMode.FLOOR), precision);
    }

    @Override
    public Num ceil() {
        int precision = Math.max(mathContext.getPrecision(), DEFAULT_PRECISION);
        return new DecimalNum(delegate.setScale(0, RoundingMode.CEILING), precision);
    }

    /**
     * @see BigDecimal#pow(int, java.math.MathContext)
     */
    @Override
    public Num pow(int n) {
        int precision = mathContext.getPrecision();
        BigDecimal result = delegate.pow(n, new MathContext(precision, RoundingMode.HALF_UP));
        return new DecimalNum(result, precision);
    }

    /**
     * Returns a {@code Num} whose value is {@code √(this)} with {@code precision} =
     * {@link #DEFAULT_PRECISION}.
     * 
     * @see DecimalNum#sqrt(int)
     */
    @Override
    public Num sqrt() {
        return sqrt(DEFAULT_PRECISION);
    }

    @Override
    public Num sqrt(int precision) {
        log.trace("delegate {}", delegate);
        int comparedToZero = delegate.compareTo(BigDecimal.ZERO);
        switch (comparedToZero) {
        case -1:
            return NaN;
        case 0:
            return DecimalNum.valueOf(0);
        }

        // Direct implementation of the example in:
        // https://en.wikipedia.org/wiki/Methods_of_computing_square_roots#Babylonian_method
        MathContext precisionContext = new MathContext(precision, RoundingMode.HALF_UP);
        BigDecimal estimate = new BigDecimal(delegate.toString(), precisionContext);
        String string = String.format(Locale.ROOT, "%1.1e", estimate);
        log.trace("scientific notation {}", string);
        if (string.contains("e")) {
            String[] parts = string.split("e");
            BigDecimal mantissa = new BigDecimal(parts[0]);
            BigDecimal exponent = new BigDecimal(parts[1]);
            if (exponent.remainder(new BigDecimal(2)).compareTo(BigDecimal.ZERO) > 0) {
                exponent = exponent.subtract(BigDecimal.ONE);
                mantissa = mantissa.multiply(BigDecimal.TEN);
                log.trace("modified notatation {}e{}", mantissa, exponent);
            }
            BigDecimal estimatedMantissa = mantissa.compareTo(BigDecimal.TEN) < 0 ? new BigDecimal(2)
                    : new BigDecimal(6);
            BigDecimal estimatedExponent = exponent.divide(new BigDecimal(2));
            String estimateString = String.format("%sE%s", estimatedMantissa, estimatedExponent);
            if (log.isTraceEnabled()) {
                log.trace("x[0] =~ sqrt({}...*10^{}) =~ {}", mantissa, exponent, estimateString);
            }
            DecimalFormat format = new DecimalFormat();
            format.setParseBigDecimal(true);
            try {
                estimate = (BigDecimal) format.parse(estimateString);
            } catch (ParseException e) {
                log.error("PrecicionNum ParseException:", e);
            }
        }
        BigDecimal delta;
        BigDecimal test;
        BigDecimal sum;
        BigDecimal newEstimate;
        BigDecimal two = new BigDecimal(2);
        String estimateString;
        int endIndex;
        int frontEndIndex;
        int backStartIndex;
        int i = 1;
        do {
            test = delegate.divide(estimate, precisionContext);
            sum = estimate.add(test);
            newEstimate = sum.divide(two, precisionContext);
            delta = newEstimate.subtract(estimate).abs();
            estimate = newEstimate;
            if (log.isTraceEnabled()) {
                estimateString = String.format("%1." + precision + "e", estimate);
                endIndex = estimateString.length();
                frontEndIndex = 20 > endIndex ? endIndex : 20;
                backStartIndex = 20 > endIndex ? 0 : endIndex - 20;
                log.trace("x[{}] = {}..{}, delta = {}", i, estimateString.substring(0, frontEndIndex),
                        estimateString.substring(backStartIndex, endIndex), String.format("%1.1e", delta));
                i++;
            }
        } while (delta.compareTo(BigDecimal.ZERO) > 0);
        return DecimalNum.valueOf(estimate, precision);
    }

    @Override
    public Num log() {
        // Algorithm: http://functions.wolfram.com/ElementaryFunctions/Log/10/
        // https://stackoverflow.com/a/6169691/6444586
        Num logx;
        if (isNegativeOrZero()) {
            return NaN;
        }

        if (delegate.equals(BigDecimal.ONE)) {
            logx = DecimalNum.valueOf(BigDecimal.ZERO, mathContext.getPrecision());
        } else {
            long ITER = 1000;
            BigDecimal x = delegate.subtract(BigDecimal.ONE);
            BigDecimal ret = new BigDecimal(ITER + 1);
            for (long i = ITER; i >= 0; i--) {
                BigDecimal N = new BigDecimal(i / 2 + 1).pow(2);
                N = N.multiply(x, mathContext);
                ret = N.divide(ret, mathContext);

                N = new BigDecimal(i + 1);
                ret = ret.add(N, mathContext);

            }
            ret = x.divide(ret, mathContext);

            logx = DecimalNum.valueOf(ret, mathContext.getPrecision());
        }
        return logx;
    }

    @Override
    public Num abs() {
        return new DecimalNum(delegate.abs(), mathContext.getPrecision());
    }

    @Override
    public Num negate() {
        return new DecimalNum(delegate.negate(), mathContext.getPrecision());
    }

    @Override
    public boolean isZero() {
        return delegate.signum() == 0;
    }

    @Override
    public boolean isPositive() {
        return delegate.signum() > 0;
    }

    @Override
    public boolean isPositiveOrZero() {
        return delegate.signum() >= 0;
    }

    @Override
    public boolean isNegative() {
        return delegate.signum() < 0;
    }

    @Override
    public boolean isNegativeOrZero() {
        return delegate.signum() <= 0;
    }

    @Override
    public boolean isEqual(Num other) {
        return !other.isNaN() && compareTo(other) == 0;
    }

    /**
     * Checks if this value matches another to a precision.
     *
     * @param other     the other value, not null
     * @param precision the int precision
     * @return true if this matches the specified value to a precision, false
     *         otherwise
     */
    public boolean matches(Num other, int precision) {
        Num otherNum = DecimalNum.valueOf(other.toString(), precision);
        Num thisNum = DecimalNum.valueOf(this.toString(), precision);
        if (thisNum.toString().equals(otherNum.toString())) {
            return true;
        }
        if (log.isDebugEnabled()) {
            log.debug("{} from {} does not match", thisNum, this);
            log.debug("{} from {} to precision {}", otherNum, other, precision);
        }
        return false;
    }

    /**
     * Checks if this value matches another within an offset.
     *
     * @param other the other value, not null
     * @param delta the {@link Num} offset
     * @return true if this matches the specified value within an offset, false
     *         otherwise
     */
    public boolean matches(Num other, Num delta) {
        Num result = this.minus(other);
        if (!result.isGreaterThan(delta)) {
            return true;
        }
        if (log.isDebugEnabled()) {
            log.debug("{} does not match", this);
            log.debug("{} within offset {}", other, delta);
        }
        return false;
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
        return !other.isNaN() && delegate.compareTo(((DecimalNum) other).delegate) < 1;
    }

    @Override
    public int compareTo(Num other) {
        return other.isNaN() ? 0 : delegate.compareTo(((DecimalNum) other).delegate);
    }

    /**
     * @return the {@code Num} whose value is the smaller of this {@code Num} and
     *         {@code other}. If they are equal, as defined by the
     *         {@link #compareTo(Num) compareTo} method, {@code this} is returned.
     */
    @Override
    public Num min(Num other) {
        return other.isNaN() ? NaN : (compareTo(other) <= 0 ? this : other);
    }

    /**
     * @return the {@code Num} whose value is the greater of this {@code Num} and
     *         {@code other}. If they are equal, as defined by the
     *         {@link #compareTo(Num) compareTo} method, {@code this} is returned.
     */
    @Override
    public Num max(Num other) {
        return other.isNaN() ? NaN : (compareTo(other) >= 0 ? this : other);
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegate);
    }

    /**
     * <b>Warning:</b> This method returns {@code true} if {@code this} and
     * {@code obj} are both {@link NaN#NaN}.
     * 
     * @return true if {@code this} object is the same as the {@code obj} argument,
     *         as defined by the {@link #compareTo(Num) compareTo} method; false
     *         otherwise.
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DecimalNum)) {
            return false;
        }
        return this.delegate.compareTo(((DecimalNum) obj).delegate) == 0;
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public Num pow(Num n) {
        // There is no BigDecimal.pow(BigDecimal). We could do:
        // double Math.pow(double delegate.doubleValue(), double n)
        // But that could overflow any of the three doubles.
        // Instead perform:
        // x^(a+b) = x^a * x^b
        // Where:
        // n = a+b
        // a is a whole number (make sure it doesn't overflow int)
        // remainder 0 <= b < 1
        // So:
        // x^a uses DecimalNum ((DecimalNum) x).pow(int a) cannot overflow Num
        // x^b uses double Math.pow(double x, double b) cannot overflow double because b
        // < 1.
        // As suggested: https://stackoverflow.com/a/3590314

        // get n = a+b, same precision as n
        BigDecimal aplusb = (((DecimalNum) n).delegate);
        // get the remainder 0 <= b < 1, looses precision as double
        BigDecimal b = aplusb.remainder(BigDecimal.ONE);
        // bDouble looses precision
        double bDouble = b.doubleValue();
        // get the whole number a
        BigDecimal a = aplusb.subtract(b);
        // convert a to an int, fails on overflow
        int aInt = a.intValueExact();
        // use BigDecimal pow(int)
        BigDecimal xpowa = delegate.pow(aInt);
        // use double pow(double, double)
        double xpowb = Math.pow(delegate.doubleValue(), bDouble);
        // use PrecisionNum.multiply(PrecisionNum)
        BigDecimal result = xpowa.multiply(new BigDecimal(xpowb));
        return new DecimalNum(result.toString());
    }

}
