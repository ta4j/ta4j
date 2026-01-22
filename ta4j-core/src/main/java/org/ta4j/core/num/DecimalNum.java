/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.num;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Representation of arbitrary precision {@link BigDecimal}. A {@code Num}
 * consists of a {@code BigDecimal} with arbitrary {@link MathContext}
 * (precision and rounding mode).
 *
 * <p>
 * It uses a precision of up to {@value #DEFAULT_PRECISION} decimal places.
 *
 * @see BigDecimal
 * @see MathContext
 * @see RoundingMode
 * @see Num
 */
public final class DecimalNum implements Num {

    static final int DEFAULT_PRECISION = 16;
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(DecimalNum.class);
    private static final RoundingMode DEFAULT_ROUNDING_MODE = RoundingMode.HALF_UP;
    private static final AtomicReference<MathContext> DEFAULT_MATH_CONTEXT = new AtomicReference<>(
            new MathContext(DEFAULT_PRECISION, DEFAULT_ROUNDING_MODE));

    private final MathContext mathContext;
    private final BigDecimal delegate;

    /**
     * Constructor.
     *
     * <p>
     * Constructs the most precise {@code Num}, because it converts a {@code String}
     * to a {@code Num} with a precision of at least {@link #DEFAULT_PRECISION};
     * only a string parameter can accurately represent a value.
     *
     * @param val the string representation of the Num value
     * @deprecated This constructor leaks higher precisions into overall
     *             calculations. Use {@link DecimalNum(String, MathContext)}
     *             instead. {@link DecimalNumFactory#numOf(String)} does.
     */
    @Deprecated(since = "0.18", forRemoval = true)
    private DecimalNum(final String val) {
        this.delegate = new BigDecimal(val);
        final var defaultContext = getDefaultMathContext();
        final int defaultPrecision = defaultContext.getPrecision();
        final int precision = Math.max(this.delegate.precision(), defaultPrecision);
        this.mathContext = precision == defaultPrecision ? defaultContext
                : new MathContext(precision, defaultContext.getRoundingMode());
    }

    /**
     * Constructor.
     *
     * <p>
     * Constructs a more precise {@code Num} than from {@code double}, because it
     * converts a {@code String} to a {@code Num} with a precision of
     * {@code precision}; only a string parameter can accurately represent a value.
     *
     * @param val         the string representation of the Num value
     * @param mathContext the precision of the Num value
     */
    private DecimalNum(final String val, final MathContext mathContext) {
        this.mathContext = mathContext;
        this.delegate = new BigDecimal(val, mathContext);
    }

    private DecimalNum(final int val, final MathContext mathContext) {
        this.mathContext = mathContext;
        this.delegate = BigDecimal.valueOf(val);
    }

    private DecimalNum(final long val, final MathContext mathContext) {
        this.mathContext = mathContext;
        this.delegate = BigDecimal.valueOf(val);
    }

    private DecimalNum(final short val, final MathContext mathContext) {
        this.mathContext = mathContext;
        this.delegate = new BigDecimal(val, mathContext);
    }

    private DecimalNum(final float val, final MathContext mathContext) {
        this.mathContext = mathContext;
        this.delegate = BigDecimal.valueOf(val);
    }

    private DecimalNum(final double val, final MathContext mathContext) {
        this.mathContext = mathContext;
        this.delegate = BigDecimal.valueOf(val);
    }

    private DecimalNum(final BigDecimal val, final MathContext mathContext) {
        this.mathContext = mathContext;
        this.delegate = Objects.requireNonNull(val);
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
    public static DecimalNum valueOf(final String val) {
        if (val.equalsIgnoreCase("NAN")) {
            throw new NumberFormatException();
        }
        return new DecimalNum(val);
    }

    /**
     * Returns a {@code Num} version of the given {@code String} with a precision of
     * {@code precision}.
     *
     * @param val         the number
     * @param mathContext with the precision
     * @return the {@code Num} with a precision of {@code precision}
     * @throws NumberFormatException if {@code val} is {@code "NaN"}
     */
    public static DecimalNum valueOf(final String val, final MathContext mathContext) {
        if (val.equalsIgnoreCase("NAN")) {
            throw new NumberFormatException();
        }
        return new DecimalNum(val, mathContext);
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
    public static DecimalNum valueOf(final Number val) {
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
    public static DecimalNum valueOf(final Num val) {
        return valueOf(val.bigDecimalValue());
    }

    /**
     * Returns the default {@link MathContext} used when no precision is specified.
     *
     * @return default math context
     */
    public static MathContext getDefaultMathContext() {
        return DEFAULT_MATH_CONTEXT.get();
    }

    /**
     * Returns the default precision used when no precision is specified.
     *
     * @return default precision
     */
    public static int getDefaultPrecision() {
        return getDefaultMathContext().getPrecision();
    }

    /**
     * Configures the default {@link MathContext} used by {@link DecimalNum}.
     *
     * @param mathContext new default math context
     * @throws NullPointerException     if {@code mathContext} is {@code null}
     * @throws IllegalArgumentException if {@code mathContext#getPrecision()} is not
     *                                  positive
     * @since 0.19
     */
    public static void configureDefaultMathContext(final MathContext mathContext) {
        Objects.requireNonNull(mathContext, "mathContext");
        if (mathContext.getPrecision() <= 0) {
            throw new IllegalArgumentException("Precision must be greater than zero");
        }
        DEFAULT_MATH_CONTEXT.set(mathContext);
    }

    /**
     * Configures the default precision while preserving the current rounding mode.
     *
     * @param precision new default precision (> 0)
     * @since 0.19
     */
    public static void configureDefaultPrecision(final int precision) {
        final var current = getDefaultMathContext();
        configureDefaultMathContext(new MathContext(precision, current.getRoundingMode()));
    }

    /**
     * Resets the default precision and rounding mode to the library defaults.
     *
     * @since 0.19
     */
    public static void resetDefaultPrecision() {
        DEFAULT_MATH_CONTEXT.set(new MathContext(DEFAULT_PRECISION, DEFAULT_ROUNDING_MODE));
    }

    /**
     * Returns a {@code Num} version of the given {@code int}.
     *
     * @param val the number
     * @return the {@code Num}
     */
    public static DecimalNum valueOf(final int val, final MathContext mathContext) {
        return new DecimalNum(val, mathContext);
    }

    /**
     * Returns a {@code Num} version of the given {@code long}.
     *
     * @param val the number
     * @return the {@code Num}
     */
    public static DecimalNum valueOf(final long val, final MathContext mathContext) {
        return new DecimalNum(val, mathContext);
    }

    /**
     * Returns a {@code Num} version of the given {@code short}.
     *
     * @param val the number
     * @return the {@code Num}
     */
    public static DecimalNum valueOf(final short val, final MathContext mathContext) {
        return new DecimalNum(val, mathContext);
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
    public static DecimalNum valueOf(final float val, final MathContext mathContext) {
        if (Float.isNaN(val)) {
            throw new NumberFormatException();
        }
        return new DecimalNum(val, mathContext);
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
    public static DecimalNum valueOf(final double val, final MathContext mathContext) {
        if (Double.isNaN(val)) {
            throw new NumberFormatException();
        }
        return new DecimalNum(val, mathContext);
    }

    /**
     * Returns a {@code Num} version of the given {@code BigDecimal} with a
     * precision of {@code precision}.
     *
     * @param val         the number
     * @param mathContext the precision
     * @return the {@code Num}
     */
    public static DecimalNum valueOf(final BigDecimal val, final MathContext mathContext) {
        return new DecimalNum(val, mathContext);
    }

    /**
     * If there are operations between constant that have precision 0 and other
     * number we need to preserve bigger precision.
     * <p>
     * If we do not provide math context that sets upper bound, BigDecimal chooses
     * "infinity" precision, that may be too much.
     *
     * @param first  decimal num
     * @param second decimal num
     * @return math context with bigger precision
     */
    private static MathContext chooseMathContextWithGreaterPrecision(final DecimalNum first, final DecimalNum second) {
        final var firstMathContext = first.getMathContext();
        final var secondMathContext = second.getMathContext();
        return firstMathContext.getPrecision() > secondMathContext.getPrecision() ? firstMathContext
                : secondMathContext;
    }

    /**
     * Returns the underlying {@link BigDecimal} delegate.
     *
     * @return BigDecimal delegate instance of this instance
     */
    @Override
    public BigDecimal getDelegate() {
        return this.delegate;
    }

    @Override
    public NumFactory getNumFactory() {
        return DecimalNumFactory.getInstance(this.mathContext.getPrecision());
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * Returns the underlying {@link MathContext} mathContext.
     *
     * @return MathContext of this instance
     */
    public MathContext getMathContext() {
        return this.mathContext;
    }

    @Override
    public BigDecimal bigDecimalValue() {
        return this.delegate;
    }

    @Override
    public Num plus(final Num augend) {
        if (augend.isNaN()) {
            return NaN;
        }
        final var decimalNum = (DecimalNum) augend;
        final var sumContext = chooseMathContextWithGreaterPrecision(decimalNum, this);
        final var result = this.delegate.add(decimalNum.delegate, sumContext);
        return new DecimalNum(result, sumContext);
    }

    /**
     * Returns a {@code Num} whose value is {@code (this - augend)}, with rounding
     * according to the context settings.
     *
     * @see BigDecimal#subtract(java.math.BigDecimal, java.math.MathContext)
     */
    @Override
    public Num minus(final Num subtrahend) {
        if (subtrahend.isNaN()) {
            return NaN;
        }
        final var decimalNum = (DecimalNum) subtrahend;
        final var subContext = chooseMathContextWithGreaterPrecision(decimalNum, this);
        final var result = this.delegate.subtract(decimalNum.delegate, subContext);
        return new DecimalNum(result, subContext);
    }

    /**
     * Returns a {@code Num} whose value is {@code this * multiplicand}, with
     * rounding according to the context settings.
     *
     * @see BigDecimal#multiply(java.math.BigDecimal, java.math.MathContext)
     */
    @Override
    public Num multipliedBy(final Num multiplicand) {
        if (multiplicand.isNaN()) {
            return NaN;
        }
        final var decimalNum = (DecimalNum) multiplicand;
        final var multiplicationContext = chooseMathContextWithGreaterPrecision(decimalNum, this);
        final var result = this.delegate.multiply(decimalNum.delegate, multiplicationContext);
        return new DecimalNum(result, multiplicationContext);
    }

    /**
     * Returns a {@code Num} whose value is {@code (this / divisor)}, with rounding
     * according to the context settings.
     *
     * @see BigDecimal#divide(java.math.BigDecimal, java.math.MathContext)
     */
    @Override
    public Num dividedBy(final Num divisor) {
        if (divisor.isNaN() || divisor.isZero()) {
            return NaN;
        }
        final var decimalNum = (DecimalNum) divisor;
        final var divisionMathContext = chooseMathContextWithGreaterPrecision(decimalNum, this);
        final var result = this.delegate.divide(decimalNum.delegate, divisionMathContext);
        return new DecimalNum(result, divisionMathContext);
    }

    /**
     * Returns a {@code Num} whose value is {@code (this % divisor)}, with rounding
     * according to the context settings.
     *
     * @see BigDecimal#remainder(java.math.BigDecimal, java.math.MathContext)
     */
    @Override
    public Num remainder(final Num divisor) {
        if (divisor.isNaN()) {
            return NaN;
        }
        final var decimalNum = (DecimalNum) divisor;
        final var moduloContext = chooseMathContextWithGreaterPrecision(decimalNum, this);
        final var result = this.delegate.remainder(decimalNum.delegate, moduloContext);
        return new DecimalNum(result, moduloContext);
    }

    @Override
    public Num floor() {
        return new DecimalNum(this.delegate.setScale(0, RoundingMode.FLOOR), this.mathContext);
    }

    @Override
    public Num ceil() {
        return new DecimalNum(this.delegate.setScale(0, RoundingMode.CEILING), this.mathContext);
    }

    /**
     * @see BigDecimal#pow(int, java.math.MathContext)
     */
    @Override
    public Num pow(final int n) {
        final BigDecimal result = this.delegate.pow(n, this.mathContext);
        return new DecimalNum(result, this.mathContext);
    }

    /**
     * Returns a {@code Num} whose value is {@code √(this)} with {@code precision} =
     * {@link #DEFAULT_PRECISION}.
     *
     * @see DecimalNum#sqrt(MathContext)
     */
    @Override
    public Num sqrt() {
        return sqrt(this.mathContext);
    }

    @Override
    public Num sqrt(final MathContext precisionContext) {
        log.trace("delegate {}", this.delegate);
        final int comparedToZero = this.delegate.compareTo(BigDecimal.ZERO);
        switch (comparedToZero) {
        case -1:
            return NaN;
        case 0:
            return DecimalNumFactory.getInstance().zero();
        }

        // Direct implementation of the example in:
        // https://en.wikipedia.org/wiki/Methods_of_computing_square_roots#Babylonian_method
        BigDecimal estimate = new BigDecimal(this.delegate.toString(), precisionContext);
        final String string = String.format(Locale.ROOT, "%1.1e", estimate);
        log.trace("scientific notation {}", string);
        if (string.contains("e")) {
            final String[] parts = string.split("e");
            BigDecimal mantissa = new BigDecimal(parts[0]);
            BigDecimal exponent = new BigDecimal(parts[1]);
            if (exponent.remainder(new BigDecimal(2)).compareTo(BigDecimal.ZERO) > 0) {
                exponent = exponent.subtract(BigDecimal.ONE);
                mantissa = mantissa.multiply(BigDecimal.TEN);
                log.trace("modified notatation {}e{}", mantissa, exponent);
            }
            final BigDecimal estimatedMantissa = mantissa.compareTo(BigDecimal.TEN) < 0 ? new BigDecimal(2)
                    : new BigDecimal(6);
            final BigDecimal estimatedExponent = exponent.divide(new BigDecimal(2));
            final String estimateString = String.format("%sE%s", estimatedMantissa, estimatedExponent);
            if (log.isTraceEnabled()) {
                log.trace("x[0] =~ sqrt({}...*10^{}) =~ {}", mantissa, exponent, estimateString);
            }
            final DecimalFormat format = new DecimalFormat();
            format.setParseBigDecimal(true);
            try {
                estimate = (BigDecimal) format.parse(estimateString);
            } catch (final ParseException e) {
                log.error("PrecicionNum ParseException:", e);
            }
        }
        BigDecimal delta;
        BigDecimal test;
        BigDecimal sum;
        BigDecimal newEstimate;
        final BigDecimal two = BigDecimal.TWO;
        String estimateString;
        int endIndex;
        int frontEndIndex;
        int backStartIndex;
        int i = 1;
        do {
            test = this.delegate.divide(estimate, precisionContext);
            sum = estimate.add(test);
            newEstimate = sum.divide(two, precisionContext);
            delta = newEstimate.subtract(estimate).abs();
            estimate = newEstimate;
            if (log.isTraceEnabled()) {
                estimateString = String.format("%1." + precisionContext.getPrecision() + "e", estimate);
                endIndex = estimateString.length();
                frontEndIndex = 20 > endIndex ? endIndex : 20;
                backStartIndex = 20 > endIndex ? 0 : endIndex - 20;
                log.trace("x[{}] = {}..{}, delta = {}", i, estimateString.substring(0, frontEndIndex),
                        estimateString.substring(backStartIndex, endIndex), String.format("%1.1e", delta));
                i++;
            }
        } while (delta.compareTo(BigDecimal.ZERO) > 0);
        return DecimalNum.valueOf(estimate, precisionContext);
    }

    @Override
    public Num log() {
        // Algorithm: http://functions.wolfram.com/ElementaryFunctions/Log/10/
        // https://stackoverflow.com/a/6169691/6444586
        final Num logx;
        if (isNegativeOrZero()) {
            return NaN;
        }

        if (this.delegate.equals(BigDecimal.ONE)) {
            logx = DecimalNum.valueOf(BigDecimal.ZERO, this.mathContext);
        } else {
            final long ITER = 1000;
            final BigDecimal x = this.delegate.subtract(BigDecimal.ONE);
            BigDecimal ret = new BigDecimal(ITER + 1);
            for (long i = ITER; i >= 0; i--) {
                BigDecimal N = new BigDecimal(i / 2 + 1).pow(2);
                N = N.multiply(x, this.mathContext);
                ret = N.divide(ret, this.mathContext);

                N = new BigDecimal(i + 1);
                ret = ret.add(N, this.mathContext);

            }
            ret = x.divide(ret, this.mathContext);

            logx = DecimalNum.valueOf(ret, this.mathContext);
        }
        return logx;
    }

    @Override
    public Num exp() {
        BigDecimal term = BigDecimal.ONE;
        BigDecimal sum = BigDecimal.ONE;
        final BigDecimal exponent = this.delegate;

        int i = 1;
        while (term.signum() != 0) {
            term = term.multiply(exponent, this.mathContext).divide(BigDecimal.valueOf(i), this.mathContext);
            final BigDecimal next = sum.add(term, this.mathContext);
            if (next.compareTo(sum) == 0) {
                break;
            }
            sum = next;
            i++;
        }

        return DecimalNum.valueOf(sum, this.mathContext);
    }

    @Override
    public Num abs() {
        return new DecimalNum(this.delegate.abs(), this.mathContext);
    }

    @Override
    public Num negate() {
        return new DecimalNum(this.delegate.negate(), this.mathContext);
    }

    @Override
    public boolean isZero() {
        return this.delegate.signum() == 0;
    }

    @Override
    public boolean isPositive() {
        return this.delegate.signum() > 0;
    }

    @Override
    public boolean isPositiveOrZero() {
        return this.delegate.signum() >= 0;
    }

    @Override
    public boolean isNegative() {
        return this.delegate.signum() < 0;
    }

    @Override
    public boolean isNegativeOrZero() {
        return this.delegate.signum() <= 0;
    }

    @Override
    public boolean isEqual(final Num other) {
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
    public boolean matches(final Num other, final int precision) {
        final Num otherNum = DecimalNum.valueOf(other.toString(), this.mathContext);
        final Num thisNum = DecimalNum.valueOf(this.toString(), this.mathContext);
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
    public boolean matches(final Num other, final Num delta) {
        final Num result = this.minus(other);
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
        return !other.isNaN() && this.delegate.compareTo(((DecimalNum) other).delegate) < 1;
    }

    /**
     * @return the {@code Num} whose value is the smaller of this {@code Num} and
     *         {@code other}. If they are equal, as defined by the
     *         {@link #compareTo(Num) compareTo} method, {@code this} is returned.
     */
    @Override
    public Num min(final Num other) {
        return other.isNaN() ? NaN : (compareTo(other) <= 0 ? this : other);
    }

    /**
     * @return the {@code Num} whose value is the greater of this {@code Num} and
     *         {@code other}. If they are equal, as defined by the
     *         {@link #compareTo(Num) compareTo} method, {@code this} is returned.
     */
    @Override
    public Num max(final Num other) {
        return other.isNaN() ? NaN : (compareTo(other) >= 0 ? this : other);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.delegate);
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
    public boolean equals(final Object obj) {
        if (!(obj instanceof DecimalNum)) {
            return false;
        }
        return this.delegate.compareTo(((DecimalNum) obj).delegate) == 0;
    }

    @Override
    public int compareTo(final Num other) {
        return other.isNaN() ? 0 : this.delegate.compareTo(((DecimalNum) other).delegate);
    }

    @Override
    public String toString() {
        return this.delegate.toString();
    }

    /***
     * TODO: DecimalNum throws NumberFormatException when Math.pow returns
     * NaN/Infinity This is also an edge case behavior that should be documented or
     * handled properly.
     */
    @Override
    public Num pow(final Num n) {
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
        final BigDecimal aplusb = (((DecimalNum) n).delegate);
        // get the remainder 0 <= b < 1, looses precision as double
        final BigDecimal b = aplusb.remainder(BigDecimal.ONE);
        // bDouble looses precision
        final double bDouble = b.doubleValue();
        // get the whole number a
        final BigDecimal a = aplusb.subtract(b);
        // convert a to an int, fails on overflow
        final int aInt = a.intValueExact();
        // use BigDecimal pow(int)
        final BigDecimal xpowa = this.delegate.pow(aInt);
        // use double pow(double, double)
        final double xpowb = Math.pow(this.delegate.doubleValue(), bDouble);
        // use PrecisionNum.multiply(PrecisionNum)
        final BigDecimal result = xpowa.multiply(BigDecimal.valueOf(xpowb));
        return new DecimalNum(result.toString(), this.mathContext);
    }

}
