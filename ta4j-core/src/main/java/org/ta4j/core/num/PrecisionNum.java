/*
  The MIT License (MIT)

  Copyright (c) 2014-2018 Marc de Verdelhan, Ta4j Organization & respective authors (see AUTHORS)

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
package org.ta4j.core.num;

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

import static org.ta4j.core.num.NaN.NaN;
/**
 * Representation of arbitrary precision BigDecimal.
 * A {@code Num} consists of a {@code BigDecimal} with arbitrary {@link MathContext} (precision and rounding mode).
 *
 * @see BigDecimal
 * @see MathContext
 * @see RoundingMode
 * @see Num
 *
 */
public final class PrecisionNum implements Num {

    private static final long serialVersionUID = 785564782721079992L;

    private static final int DEFAULT_PRECISION = 32;

    private final MathContext mathContext;

    private final BigDecimal delegate;

    private static final Logger log = LoggerFactory.getLogger(PrecisionNum.class);

    @Override
    public Function<Number, Num> function() {
        return (number -> PrecisionNum.valueOf(number.toString(), mathContext.getPrecision()));
    }

    /**
     * Constructor.
     * @param val the string representation of the Num value
     */
    private PrecisionNum(String val) {
        delegate = new BigDecimal(val);
        int precision = Math.max(delegate.precision(), DEFAULT_PRECISION);
        mathContext = new MathContext(precision, RoundingMode.HALF_UP);
    }

    /**
     * Constructor. Above double precision, only String parameters can represent the value.
     *
     * @param val the string representation of the Num value
     * @param precision the int precision of the Num value
     */
    private PrecisionNum(String val, int precision) {
        mathContext = new MathContext(precision, RoundingMode.HALF_UP);
        delegate = new BigDecimal(val, new MathContext(precision, RoundingMode.HALF_UP));
    }

    private PrecisionNum(short val) {
        mathContext = new MathContext(DEFAULT_PRECISION, RoundingMode.HALF_UP);
        delegate = new BigDecimal(val, mathContext);
    }

    private PrecisionNum(int val) {
        mathContext = new MathContext(DEFAULT_PRECISION, RoundingMode.HALF_UP);
        delegate = BigDecimal.valueOf(val);
    }

    private PrecisionNum(long val) {
        mathContext = new MathContext(DEFAULT_PRECISION, RoundingMode.HALF_UP);
        delegate = BigDecimal.valueOf(val);
    }

    private PrecisionNum(float val) {
        mathContext = new MathContext(DEFAULT_PRECISION, RoundingMode.HALF_UP);
        delegate = new BigDecimal(val, mathContext);
    }

    private PrecisionNum(double val) {
        mathContext = new MathContext(DEFAULT_PRECISION, RoundingMode.HALF_UP);
        delegate = BigDecimal.valueOf(val);
    }

    private PrecisionNum(BigDecimal val, int precision) {
        mathContext = new MathContext(precision, RoundingMode.HALF_UP);
        delegate = Objects.requireNonNull(val);
    }

    /**
     * Returns the underlying {@link BigDecimal} delegate
     * @return BigDecimal delegate instance of this instance
     */
    @Override
    public Number getDelegate() {
        return delegate;
    }

    /**
     * Returns the underlying {@link MathContext} mathContext
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
        BigDecimal bigDecimal = ((PrecisionNum) augend).delegate;
        int precision = mathContext.getPrecision();
        BigDecimal result = delegate.add(bigDecimal, mathContext);
        return new PrecisionNum(result, precision);
    }

    /**
     * Returns a {@code Num} whose value is {@code (this - augend)},
     * with rounding according to the context settings.
     * @param subtrahend value to be subtracted from this {@code Num}.
     * @return {@code this - subtrahend}, rounded as necessary
     * @see BigDecimal#subtract(java.math.BigDecimal, java.math.MathContext)
     */
    @Override
    public Num minus(Num subtrahend) {
        if (subtrahend.isNaN()) {
            return NaN;
        }
        BigDecimal bigDecimal = ((PrecisionNum) subtrahend).delegate;
        int precision = mathContext.getPrecision();
        BigDecimal result = delegate.subtract(bigDecimal, mathContext);
        return new PrecisionNum(result, precision);
    }

    /**
     * Returns a {@code Num} whose value is {@code this * multiplicand},
     * with rounding according to the context settings.
     * @param multiplicand value to be multiplied by this {@code Num}.
     * @return {@code this * multiplicand}, rounded as necessary
     * @see BigDecimal#multiply(java.math.BigDecimal, java.math.MathContext)
     */
    @Override
    public Num multipliedBy(Num multiplicand) {
        if (multiplicand.isNaN()) {
            return NaN;
        }
        BigDecimal bigDecimal = ((PrecisionNum) multiplicand).delegate;
        int precision = mathContext.getPrecision();
        BigDecimal result = delegate.multiply(bigDecimal, new MathContext(precision, RoundingMode.HALF_UP));
        return new PrecisionNum(result, precision);
    }

    /**
     * Returns a {@code Num} whose value is {@code (this / divisor)},
     * with rounding according to the context settings.
     * @param divisor value by which this {@code Num} is to be divided.
     * @return {@code this / divisor}, rounded as necessary
     * @see BigDecimal#divide(java.math.BigDecimal, java.math.MathContext)
     */
    @Override
    public Num dividedBy(Num divisor) {
        if (divisor.isNaN() || divisor.isZero()) {
            return NaN;
        }
        BigDecimal bigDecimal = ((PrecisionNum) divisor).delegate;
        int precision = mathContext.getPrecision();
        BigDecimal result = delegate.divide(bigDecimal, new MathContext(precision, RoundingMode.HALF_UP));
        return new PrecisionNum(result, precision);
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
        BigDecimal bigDecimal = ((PrecisionNum) divisor).delegate;
        int precision = mathContext.getPrecision();
        BigDecimal result = delegate.remainder(bigDecimal, new MathContext(precision, RoundingMode.HALF_UP));
        return new PrecisionNum(result, precision);
    }

    /**
     * Returns a {@code Num} whose value is rounded down to the nearest whole number.
     * @return <tt>this<sup>n</sup></tt>
     */
    public Num floor() {
        int precision = Math.max(mathContext.getPrecision(), DEFAULT_PRECISION);
        return new PrecisionNum(delegate.setScale(0, RoundingMode.FLOOR), precision);
    }

    /**
     * Returns a {@code Num} whose value is rounded up to the nearest whole number.
     * @return <tt>this<sup>n</sup></tt>
     */
    public Num ceil() {
        int precision = Math.max(mathContext.getPrecision(), DEFAULT_PRECISION);
        return new PrecisionNum(delegate.setScale(0, RoundingMode.CEILING), precision);
    }

    /**
     * Returns a {@code Num} whose value is <tt>(this<sup>n</sup>)</tt>.
     * @param n power to raise this {@code Num} to.
     * @return <tt>this<sup>n</sup></tt>
     * @see BigDecimal#pow(int, java.math.MathContext)
     */
    @Override
    public Num pow(int n) {
        int precision = mathContext.getPrecision();
        BigDecimal result = delegate.pow(n, new MathContext(precision, RoundingMode.HALF_UP));
        return new PrecisionNum(result, precision);
    }

    /**
     * Returns the correctly rounded positive square root of this {@code Num}.
     * /!\ Warning! Uses DEFAULT_PRECISION.
     * @return the positive square root of {@code this}
     * @see PrecisionNum#sqrt(int)
     */
    public Num sqrt() {
        return sqrt(DEFAULT_PRECISION);
    }

    /**
     * Returns a {@code num} whose value is <tt>√(this)</tt>.
     * @param precision to calculate.
     * @return <tt>this<sup>n</sup></tt>
     */
    @Override
    public Num sqrt(int precision) {
        log.trace("delegate {}", delegate);
        int comparedToZero = delegate.compareTo(BigDecimal.ZERO);
        switch (comparedToZero) {
        case -1:
            return NaN;

        case 0:
            return PrecisionNum.valueOf(0);
        }

        // Direct implementation of the example in:
        // https://en.wikipedia.org/wiki/Methods_of_computing_square_roots#Babylonian_method
        MathContext precisionContext = new MathContext(precision, RoundingMode.HALF_UP);
        BigDecimal estimate = new BigDecimal(delegate.toString(), precisionContext);
        String string = String.format(Locale.ROOT,"%1.1e", estimate);
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
            BigDecimal estimatedMantissa = mantissa.compareTo(BigDecimal.TEN) < 0 ? new BigDecimal(2) : new BigDecimal(6);
            BigDecimal estimatedExponent = exponent.divide(new BigDecimal(2));
            String estimateString = String.format("%sE%s", estimatedMantissa, estimatedExponent);
            log.trace("x[0] =~ sqrt({}...*10^{}) =~ {}", mantissa, exponent, estimateString);
            DecimalFormat format = new DecimalFormat();
            format.setParseBigDecimal(true);
            try {
                estimate = (BigDecimal) format.parse(estimateString);
            } catch (ParseException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        BigDecimal delta = null;
        BigDecimal test = null;
        BigDecimal sum = null;
        BigDecimal newEstimate = null;
        BigDecimal two = new BigDecimal(2);
        String estimateString = null;
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
                log.trace("x[{}] = {}..{}, delta = {}",
                        i,
                        estimateString.substring(0, frontEndIndex),
                        estimateString.substring(backStartIndex, endIndex),
                        String.format("%1.1e", delta));
                i++;
            }
        } while (delta.compareTo(BigDecimal.ZERO) > 0);
        return PrecisionNum.valueOf(estimate, precision);
    }

    /**
     * Returns a {@code Num} whose value is the absolute value
     * of this {@code Num}.
     * @return {@code abs(this)}
     */
    @Override
    public Num abs() {
        return new PrecisionNum(delegate.abs(), mathContext.getPrecision());
    }

    /**
     * Checks if the value is zero.
     * @return true if the value is zero, false otherwise
     */
    @Override
    public boolean isZero() {
        return compareTo(PrecisionNum.valueOf(0)) == 0;
    }

    /**
     * Checks if the value is greater than zero.
     * @return true if the value is greater than zero, false otherwise
     */
    @Override
    public boolean isPositive() {
        return compareTo(PrecisionNum.valueOf(0)) > 0;
    }

    /**
     * Checks if the value is zero or greater.
     * @return true if the value is zero or greater, false otherwise
     */
    @Override
    public boolean isPositiveOrZero() {
        return compareTo(PrecisionNum.valueOf(0)) >= 0;
    }

    /**
     * Checks if the value is less than zero.
     * @return true if the value is less than zero, false otherwise
     */
    @Override
    public boolean isNegative() {
        return compareTo(function().apply(0)) < 0;
    }

    /**
     * Checks if the value is zero or less.
     * @return true if the value is zero or less, false otherwise
     */
    @Override
    public boolean isNegativeOrZero() {
        return compareTo(PrecisionNum.valueOf(0)) <= 0;
    }

    /**
     * Checks if this value is equal to another.
     * @param other the other value, not null
     * @return true is this is greater than the specified value, false otherwise
     */
    @Override
    public boolean isEqual(Num other) {
        return !other.isNaN() && compareTo(other) == 0;
    }

    /**
     * Checks if this value matches another to a precision.
     *
     * @param other the other value, not null
     * @param precision the int precision
     * @return true is this matches the specified value to a precision, false otherwise
     */
    public boolean matches(Num other, int precision) {
        Num otherNum = PrecisionNum.valueOf(other.toString(), precision);
        Num thisNum = PrecisionNum.valueOf(this.toString(), precision);
        if (thisNum.toString().equals(otherNum.toString())) {
            return true;
        }
        log.debug("{} from {} does not match", thisNum, this);
        log.debug("{} from {} to precision {}", otherNum, other, precision);
        return false;
    }

    /**
     * Checks if this value matches another within an offset.
     *
     * @param other the other value, not null
     * @param delta the {@link Num} offset
     * @return true is this matches the specified value within an offset, false otherwise
     */
    public boolean matches(Num other, Num delta) {
        Num result = this.minus(other);
        if (!result.isGreaterThan(delta)) {
            return true;
        }
        log.debug("{} does not match", this);
        log.debug("{} within offset {}", other, delta);
        return false;
    }

    /**
     * Checks if this value is greater than another.
     * @param other the other value, not null
     * @return true is this is greater than the specified value, false otherwise
     */
    @Override
    public boolean isGreaterThan(Num other) {
        return !other.isNaN() && compareTo(other) > 0;
    }

    /**
     * Checks if this value is greater than or equal to another.
     * @param other the other value, not null
     * @return true is this is greater than or equal to the specified value, false otherwise
     */
    @Override
    public boolean isGreaterThanOrEqual(Num other) {
        return !other.isNaN() && compareTo(other) > -1;
    }

    /**
     * Checks if this value is less than another.
     * @param other the other value, not null
     * @return true is this is less than the specified value, false otherwise
     */
    @Override
    public boolean isLessThan(Num other) {
        return !other.isNaN() && compareTo(other) < 0;
    }

    @Override
    public boolean isLessThanOrEqual(Num other) {
        return !other.isNaN() && delegate.compareTo(((PrecisionNum) other).delegate) < 1;
    }

    @Override
    public int compareTo(Num other) {
        return other.isNaN() ? 0 : delegate.compareTo(((PrecisionNum) other).delegate);
    }

    /**
     * Returns the minimum of this {@code Num} and {@code other}.
     * @param other value with which the minimum is to be computed
     * @return the {@code Num} whose value is the lesser of this
     *         {@code Num} and {@code other}.  If they are equal,
     *         as defined by the {@link #compareTo(Num) compareTo}
     *         method, {@code this} is returned.
     */
    @Override
    public Num min(Num other) {
        return other.isNaN() ? NaN : (compareTo(other) <= 0 ? this : other);
    }

    /**
     * Returns the maximum of this {@code Num} and {@code other}.
     * @param  other value with which the maximum is to be computed
     * @return the {@code Num} whose value is the greater of this
     *         {@code Num} and {@code other}.  If they are equal,
     *         as defined by the {@link #compareTo(Num) compareTo}
     *         method, {@code this} is returned.
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
     * {@inheritDoc}
     * Warning: This method returns true if `this` and `obj` are both NaN.NaN.
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Num)) {
            return false;
        }
        return !((Num) obj).isNaN() && this.delegate.compareTo(((PrecisionNum) obj).delegate) == 0;
    }

    /**
     * Returns a {@code Num} version of the given {@code String}.
     * @param val the number
     * @return the {@code Num}
     */
    public static Num valueOf(String val) {
        return val.toUpperCase().equals("NAN") ? NaN : new PrecisionNum(val);
    }

    /**
     * Returns a {@code Num) version of the given {@code String} with a precision.
     * @param val the number
     * @param precision the precision
     * @return the {@code Num}
     */
    public static Num valueOf(String val, int precision) {
        return val.toUpperCase().equals("NAN") ? NaN : new PrecisionNum(val, precision);
    }

    /**
     * Returns a {@code Num} version of the given {@code short}.
     * @param val the number
     * @return the {@code Num}
     */
    public static Num valueOf(short val) {
        return new PrecisionNum(val);
    }

    /**
     * Returns a {@code Num} version of the given {@code int}.
     * @param val the number
     * @return the {@code Num}
     */
    public static Num valueOf(int val) {
        return new PrecisionNum(val);
    }

    /**
     * Returns a {@code Num} version of the given {@code long}.
     * @param val the number
     * @return the {@code Num}
     */
    public static Num valueOf(long val) {
        return new PrecisionNum(val);
    }

    /**
     * Returns a {@code Num} version of the given {@code float}.
     * @param val the number
     * @return the {@code Num}
     */
    public static Num valueOf(float val) {
        return val == Float.NaN ? NaN : new PrecisionNum(val);
    }

    public static PrecisionNum valueOf(BigDecimal val) {
        return new PrecisionNum(val, val.precision());
    }

    public static PrecisionNum valueOf(BigDecimal val, int precision) {
        return new PrecisionNum(val, precision);
    }

    /**
     * Returns a {@code Num} version of the given {@code double}.
     * @param val the number
     * @return the {@code Num}
     */
    public static Num valueOf(double val) {
        return val == Double.NaN ? NaN : new PrecisionNum(val);
    }

    /**
     * Returns a {@code Num} version of the given {@code Num}.
     * @param val the number
     * @return the {@code Num}
     */
    public static PrecisionNum valueOf(PrecisionNum val) {
        return val;
    }

    /**
     * Returns a {@code Num} version of the given {@code Number}.
     * Warning: This method turns the number into a string first
     * @param val the number
     * @return the {@code Num}
     */
    public static PrecisionNum valueOf(Number val) {
        return new PrecisionNum(val.toString());
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public Num pow(Num n) {
        // There is no BigDecimal.pow(BigDecimal).  We could do:
        //   double Math.pow(double delegate.doubleValue(), double n)
        // But that could overflow any of the three doubles.
        // Instead perform:
        //   x^(a+b) = x^a * x^b
        // Where:
        //   n = a+b
        //   a is a whole number (make sure it doesn't overflow int)
        //   remainder 0 <= b < 1
        // So:
        //   x^a uses   PrecisionNum ((PrecisionNum) x).pow(int a)  cannot overflow Num
        //   x^b uses   double Math.pow(double x, double b)         cannot overflow double because b < 1.
        // As suggested: https://stackoverflow.com/a/3590314

        // get n = a+b, same precision as n
        BigDecimal aplusb = (((PrecisionNum) n).delegate);
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
        return new PrecisionNum(result.toString());
    }

}
