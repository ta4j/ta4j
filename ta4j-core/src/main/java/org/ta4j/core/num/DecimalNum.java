/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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
 * Representation of arbitrary precision BigDecimal. A {@code Num} consists of a {@code BigDecimal} with arbitrary {@link MathContext} (precision and rounding mode).
 * * 任意精度 BigDecimal 的表示。 {@code Num} 由具有任意 {@link MathContext}（精度和舍入模式）的 {@code BigDecimal} 组成。
 *
 * @see BigDecimal
 * @see MathContext
 * @see RoundingMode
 * @see Num
 */
public final class DecimalNum implements Num {

    private static final long serialVersionUID = 785564782721079992L;

    private static final int DEFAULT_PRECISION = 32;
    private static final Logger log = LoggerFactory.getLogger(DecimalNum.class);
    private final MathContext mathContext;
    private final BigDecimal delegate;

    /**
     * Constructor.
     *
     * @param val the string representation of the Num value
     *            Num 值的字符串表示形式
     */
    private DecimalNum(String val) {
        delegate = new BigDecimal(val);
        int precision = Math.max(delegate.precision(), DEFAULT_PRECISION);
        mathContext = new MathContext(precision, RoundingMode.HALF_UP);
    }

    /**
     * Constructor. Above double precision, only String parameters can represent the value.
     * * 构造函数。 双精度以上，只有String参数可以表示值。
     *
     * @param val       the string representation of the Num value
     *                  Num 值的字符串表示形式
     * @param precision the int precision of the Num value
     *                  Num 值的 int 精度
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
     * 返回给定 {@code String} 的 {@code Num} 版本。
     *
     * @param val the number  号码
     * @return the {@code Num}  {@code 编号}
     */
    public static DecimalNum valueOf(String val) {
        if (val.equalsIgnoreCase("NAN")) {
            throw new NumberFormatException();
        }
        return new DecimalNum(val);
    }

    /**
     * Returns a {@code Num) version of the given {@code String} with a precision.
     * 以精度返回给定 {@code String} 的 {@code Num) 版本。
     *
     * @param val the number 號碼
     * 
     * @param precision the precision
     * * @param precision 精度
     *
     * @return the {@code Num}
     * * @return {@code 编号}
     */
    public static DecimalNum valueOf(String val, int precision) {
        if (val.equalsIgnoreCase("NAN")) {
            throw new NumberFormatException();
        }
        return new DecimalNum(val, precision);
    }

    /**
     * Returns a {@code Num} version of the given {@code short}.
     * * 返回给定 {@code short} 的 {@code Num} 版本。
     *
     * @param val the number 號碼
     * @return the {@code Num} code 號碼
     */
    public static DecimalNum valueOf(short val) {
        return new DecimalNum(val);
    }

    /**
     * Returns a {@code Num} version of the given {@code int}.
     * * 返回给定 {@code int} 的 {@code Num} 版本。
     *
     * @param val the number 号码
     * @return the {@code Num}  {@code 编号}
     */
    public static DecimalNum valueOf(int val) {
        return new DecimalNum(val);
    }

    /**
     * Returns a {@code Num} version of the given {@code long}.
     * * 返回给定 {@code long} 的 {@code Num} 版本。
     *
     * @param val the number 號碼
     * @return the {@code Num} {@code 编号}
     */
    public static DecimalNum valueOf(long val) {
        return new DecimalNum(val);
    }

    /**
     * Returns a {@code Num} version of the given {@code float}. Using the float version could introduce inaccuracies.
     * * 返回给定 {@code float} 的 {@code Num} 版本。 使用浮动版本可能会引入不准确性。
     *
     * @param val the number
     *            号码
     * @return the {@code Num}
     *              {@code 编号}
     */
    public static DecimalNum valueOf(float val) {
        if (Float.isNaN(val)) {
            throw new NumberFormatException();
        }
        return new DecimalNum(val);
    }

    public static DecimalNum valueOf(BigDecimal val) {
        return new DecimalNum(val, val.precision());
    }

    public static DecimalNum valueOf(BigDecimal val, int precision) {
        return new DecimalNum(val, precision);
    }

    /**
     * Returns a {@code Num} version of the given {@code double}. Using the double version could introduce inaccuracies.
     * * 返回给定 {@code double} 的 {@code Num} 版本。 使用双重版本可能会导致不准确。
     *
     * @param val the number
     *            号码
     * @return the {@code Num}
     *              {@code 编号}
     */
    public static DecimalNum valueOf(double val) {
        if (Double.isNaN(val)) {
            throw new NumberFormatException();
        }
        return new DecimalNum(val);
    }

    /**
     * Returns a {@code Num} version of the given {@code Num}.
     * 返回给定 {@code Num} 的 {@code Num} 版本。
     *
     * @param val the number  号码
     * @return the {@code Num}  {@code 编号}
     */
    public static DecimalNum valueOf(DecimalNum val) {
        return val;
    }

    /**
     * Returns a {@code Num} version of the given {@code Number}. Warning: This method turns the number into a string first
     * * 返回给定 {@code Number} 的 {@code Num} 版本。 警告：此方法首先将数字转换为字符串
     *
     * @param val the number
     *            号码
     * @return the {@code Num}
     *          {@code 编号}
     */
    public static DecimalNum valueOf(Number val) {
        return new DecimalNum(val.toString());
    }

    @Override
    public Function<Number, Num> function() {
        return (number -> DecimalNum.valueOf(number.toString(), mathContext.getPrecision()));
    }

    /**
     * Returns the underlying {@link BigDecimal} delegate
     *  * 返回底层 {@link BigDecimal} 委托
     *
     * @return BigDecimal delegate instance of this instance
     *  @return 此实例的 BigDecimal 委托实例
     */
    @Override
    public BigDecimal getDelegate() {
        return delegate;
    }

    /**
     * Returns the underlying {@link MathContext} mathContext
     *  * 返回底层的 {@link MathContext} mathContext
     *
     * @return MathContext of this instance
     *  @return 此实例的 MathContext
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
     * Returns a {@code Num} whose value is {@code (this - augend)}, with rounding according to the context settings.
     * * 返回一个 {@code Num}，其值为 {@code (this - augend)}，根据上下文设置进行四舍五入。
     *
     * @param subtrahend value to be subtracted from this {@code Num}.
     *                   * 要从此 {@code Number} 中减去的 @param 减数值。
     *
     * @return {@code this - subtrahend}, rounded as necessary
     * * @return {@code this - subtrahend}，根据需要四舍五入
     *
     * @see BigDecimal#subtract(java.math.BigDecimal, java.math.MathContext)
     * * @see BigDecimal#subtract(java.math.BigDecimal, java.math.MathContext)
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
     * Returns a {@code Num} whose value is {@code this * multiplicand}, with rounding according to the context settings.
     *      * Returns a {@code Num} whose value is {@code this * multiplicand}, with rounding according to the context settings.
     *
     * @param multiplicand value to be multiplied by this {@code Num}.
     *                     要乘以此 {@code Number} 的值。
     * @return {@code this * multiplicand}, rounded as necessary @see BigDecimal#multiply(java.math.BigDecimal, java.math.MathContext)
     * * @return {@code this * multiplicand}，根据需要四舍五入 @see BigDecimal#multiply(java.math.BigDecimal, java.math.MathContext)
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
     * Returns a {@code Num} whose value is {@code (this / divisor)}, with rounding according to the context settings.
     ** 返回一个 {@code Num}，其值为 {@code (this / divisor)}，根据上下文设置进行四舍五入。
     *
     * @param divisor value by which this {@code Num} is to be divided.
     *                * @param 除数值，此 {@code Num} 将被除以。
     *
     * @return {@code this / divisor}, rounded as necessary @see BigDecimal#divide(java.math.BigDecimal, java.math.MathContext)
     * * @return {@code this / divisor}，根据需要四舍五入 @see BigDecimal#divide(java.math.BigDecimal, java.math.MathContext)
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
     * Returns a {@code Num} whose value is {@code (this % divisor)}, with rounding according to the context settings.
     * * 返回一个 {@code Num}，其值为 {@code (this % divisor)}，根据上下文设置进行四舍五入。
     *
     * @param divisor value by which this {@code Num} is to be divided.
     *                * @param 除数值，此 {@code Num} 将被除以。
     *
     * @return {@code this % divisor}, rounded as necessary.
     * * @return {@code this % divisor}，根据需要四舍五入。
     *
     * @see BigDecimal#remainder(java.math.BigDecimal, java.math.MathContext)
     *
     */
    @Override
    public Num remainder(Num divisor) {
        BigDecimal bigDecimal = ((DecimalNum) divisor).delegate;
        int precision = mathContext.getPrecision();
        BigDecimal result = delegate.remainder(bigDecimal, new MathContext(precision, RoundingMode.HALF_UP));
        return new DecimalNum(result, precision);
    }

    /**
     * Returns a {@code Num} whose value is rounded down to the nearest whole number.
     * * 返回一个 {@code Num}，其值向下舍入到最接近的整数。
     *
     * @return <code>this</code> to whole Num rounded down
     * @return <code>this</code> 为向下舍入的整数
     */
    @Override
    public Num floor() {
        int precision = Math.max(mathContext.getPrecision(), DEFAULT_PRECISION);
        return new DecimalNum(delegate.setScale(0, RoundingMode.FLOOR), precision);
    }

    /**
     * Returns a {@code Num} whose value is rounded up to the nearest whole number.
     * * 返回一个 {@code Num} ，其值四舍五入到最接近的整数。
     *
     * @return <code>this</code> to whole Num rounded up
     *          <code>this</code> 为四舍五入的整数
     */
    @Override
    public Num ceil() {
        int precision = Math.max(mathContext.getPrecision(), DEFAULT_PRECISION);
        return new DecimalNum(delegate.setScale(0, RoundingMode.CEILING), precision);
    }

    /**
     * Returns a {@code Num} whose value is <code>(this<sup>n</sup>)</code>.
     *  * 返回一个 {@code Num}，其值为 <code>(this<sup>n</sup>)</code>。
     *
     * @param n power to raise this {@code Num} to.
     *          * @param n 将此 {@code Num} 提高到的权力。
     *
     * @return <code>this<sup>n</sup></code>
     * * @return <code>这个<sup>n</sup></code>
     * @see BigDecimal#pow(int, java.math.MathContext)
     */
    @Override
    public Num pow(int n) {
        int precision = mathContext.getPrecision();
        BigDecimal result = delegate.pow(n, new MathContext(precision, RoundingMode.HALF_UP));
        return new DecimalNum(result, precision);
    }

    /**
     * Returns the correctly rounded positive square root of this {@code Num}. /!\ Warning! Uses DEFAULT_PRECISION.
     * * 返回此 {@code Num} 的正确舍入正平方根。 /！\ 警告！ 使用 DEFAULT_PRECISION。
     *
     * @return the positive square root of {@code this}
     * * @return {@code this} 的正平方根
     * @see DecimalNum#sqrt(int)
     */
    public Num sqrt() {
        return sqrt(DEFAULT_PRECISION);
    }

    /**
     *
     * Returns a {@code num} whose value is <code>√(this)</code>.
     * * 返回一个 {@code num}，其值为 <code>√(this)</code>。
     *
     * @param precision to calculate.
     *                  计算。
     *
     * @return <code>this<sup>n</sup></code>
     *      <code>这个<sup>n</sup></code>
     */
    @Override
    public Num sqrt(int precision) {
        log.trace("delegate 代表 {}", delegate);
        int comparedToZero = delegate.compareTo(BigDecimal.ZERO);
        switch (comparedToZero) {
        case -1:
            return NaN;

        case 0:
            return DecimalNum.valueOf(0);
        }

        // Direct implementation of the example in:
        // 直接实现示例中：
        // https://en.wikipedia.org/wiki/Methods_of_computing_square_roots#Babylonian_method
        MathContext precisionContext = new MathContext(precision, RoundingMode.HALF_UP);
        BigDecimal estimate = new BigDecimal(delegate.toString(), precisionContext);
        String string = String.format(Locale.ROOT, "%1.1e", estimate);
        log.trace("scientific notation 科学计数法 {}", string);
        if (string.contains("e")) {
            String[] parts = string.split("e");
            BigDecimal mantissa = new BigDecimal(parts[0]);
            BigDecimal exponent = new BigDecimal(parts[1]);
            if (exponent.remainder(new BigDecimal(2)).compareTo(BigDecimal.ZERO) > 0) {
                exponent = exponent.subtract(BigDecimal.ONE);
                mantissa = mantissa.multiply(BigDecimal.TEN);
                log.trace("modified notatation 修改符号 {}e{}", mantissa, exponent);
            }
            BigDecimal estimatedMantissa = mantissa.compareTo(BigDecimal.TEN) < 0 ? new BigDecimal(2)
                    : new BigDecimal(6);
            BigDecimal estimatedExponent = exponent.divide(new BigDecimal(2));
            String estimateString = String.format("%sE%s", estimatedMantissa, estimatedExponent);
            log.trace("x[0] =~ sqrt({}...*10^{}) =~ {}", mantissa, exponent, estimateString);
            DecimalFormat format = new DecimalFormat();
            format.setParseBigDecimal(true);
            try {
                estimate = (BigDecimal) format.parse(estimateString);
            } catch (ParseException e) {
                log.error("PrecicionNum ParseException 精度数值解析异常:", e);
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

    /**
     * Returns a {@code Num} whose value is the natural logarithm of this {@code Num}.
     * * 返回一个 {@code Num}，其值为此 {@code Num} 的自然对数。
     *
     * @return {@code log(this)}
     */
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

    /**
     * Returns a {@code Num} whose value is the absolute value of this {@code Num}.
     * * 返回一个 {@code Num}，其值为此 {@code Num} 的绝对值。
     *
     * @return {@code abs(this)}
     */
    @Override
    public Num abs() {
        return new DecimalNum(delegate.abs(), mathContext.getPrecision());
    }

    /**
     * Returns a {@code num} whose value is (-this), and whose scale is this.scale().
     * * 返回一个 {@code num}，其值为 (-this)，其刻度为 this.scale()。
     * 
     * @return {@code negate(this)}
     * {@code 否定 (this) }
     */
    @Override
    public Num negate() {
        return new DecimalNum(delegate.negate(), mathContext.getPrecision());
    }

    /**
     * Checks if the value is zero.
     * * 检查值是否为零。
     *
     * @return true if the value is zero, false otherwise
     * * @return 如果值为零，则返回 true，否则返回 false
     */
    @Override
    public boolean isZero() {
        return delegate.signum() == 0;
    }

    /**
     * Checks if the value is greater than zero.
     * * 检查值是否大于零。
     *
     * @return true if the value is greater than zero, false otherwise
     * * @return 如果值大于零，则返回 true，否则返回 false
     */
    @Override
    public boolean isPositive() {
        return delegate.signum() > 0;
    }

    /**
     * Checks if the value is zero or greater.
     * * 检查值是否为零或更大。
     *
     * @return true if the value is zero or greater, false otherwise
     * * @return 如果值为零或更大，则返回 true，否则返回 false
     */
    @Override
    public boolean isPositiveOrZero() {
        return delegate.signum() >= 0;
    }

    /**
     * Checks if the value is less than zero.
     * 检查值是否小于零。
     *
     * @return true if the value is less than zero, false otherwise
     * * @return 如果值小于零，则返回 true，否则返回 false
     */
    @Override
    public boolean isNegative() {
        return delegate.signum() < 0;
    }

    /**
     * Checks if the value is zero or less.
     * 检查值是否为零或更小。
     *
     * @return true if the value is zero or less, false otherwise
     * * @return 如果值为零或更小，则返回 true，否则返回 false
     */
    @Override
    public boolean isNegativeOrZero() {
        return delegate.signum() <= 0;
    }

    /**
     * Checks if this value is equal to another.
     * * 检查此值是否等于另一个值。
     *
     * @param other the other value, not null
     *              * @param other 其他值，不为空
     * @return true is this is greater than the specified value, false otherwise
     *           @return true 是否大于指定值，否则为 false
     */
    @Override
    public boolean isEqual(Num other) {
        return !other.isNaN() && compareTo(other) == 0;
    }

    /**
     * Checks if this value matches another to a precision.
     * 检查此值是否与另一个精度匹配。
     *
     * @param other     the other value, not null
     *                  另一个值，不为空
     *
     * @param precision the int precision
     *                  int 精度
     *
     * @return true is this matches the specified value to a precision, false  otherwise
     * * @return true 是否将指定的值匹配到精度，否则为 false
     */
    public boolean matches(Num other, int precision) {
        Num otherNum = DecimalNum.valueOf(other.toString(), precision);
        Num thisNum = DecimalNum.valueOf(this.toString(), precision);
        if (thisNum.toString().equals(otherNum.toString())) {
            return true;
        }
        log.debug("{} from {} does not match 不匹配", thisNum, this);
        log.debug("{} from {} to precision 精确到 {}", otherNum, other, precision);
        return false;
    }

    /**
     * Checks if this value matches another within an offset.
     * * 检查此值是否与偏移量内的另一个值匹配。
     *
     * @param other the other value, not null
     *              另一个值，不为空
     * @param delta the {@link Num} offset
     *              {@link Num} 偏移量
     * @return true is this matches the specified value within an offset, false  otherwise
     * @return true 是否匹配偏移量内的指定值，否则为 false
     */
    public boolean matches(Num other, Num delta) {
        Num result = this.minus(other);
        if (!result.isGreaterThan(delta)) {
            return true;
        }
        log.debug("{} does not match 不匹配", this);
        log.debug("{} within offset 在偏移量内 {}", other, delta);
        return false;
    }

    /**
     * Checks if this value is greater than another.
     * 检查此值是否大于另一个值。
     *
     * @param other the other value, not null
     *              另一个值，不为空
     * @return true is this is greater than the specified value, false otherwise
     * * @return true 是否大于指定值，否则为 false
     */
    @Override
    public boolean isGreaterThan(Num other) {
        return !other.isNaN() && compareTo(other) > 0;
    }

    /**
     * Checks if this value is greater than or equal to another.
     * 检查此值是否大于或等于另一个值。
     *
     * @param other the other value, not null
     *              另一个值，不为空
     *
     * @return true is this is greater than or equal to the specified value, false   otherwise
     * * @return true 是否大于等于指定值，否则为 false
     */
    @Override
    public boolean isGreaterThanOrEqual(Num other) {
        return !other.isNaN() && compareTo(other) > -1;
    }

    /**
     * Checks if this value is less than another.
     * * 检查这个值是否小于另一个。
     *
     * @param other the other value, not null
     *              另一个值，不为空
     * @return true is this is less than the specified value, false otherwise
     * * @return true 是否小于指定值，否则为 false
     */
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
     * Returns the minimum of this {@code Num} and {@code other}.
     * * 返回此 {@code Num} 和 {@code other} 的最小值。
     *
     * @param other value with which the minimum is to be computed
     *              * @param 计算最小值的其他值
     * @return the {@code Num} whose value is the lesser of this {@code Num} and   {@code other}. If they are equal, as defined by the     {@link #compareTo(Num) compareTo} method, {@code this} is returned.
     * * @return {@code Num} 的值是这个 {@code Num} 和 {@code other} 中较小的一个。 如果它们相等，如 {@link #compareTo(Num) compareTo} 方法所定义，则返回 {@code this}。
     */
    @Override
    public Num min(Num other) {
        return other.isNaN() ? NaN : (compareTo(other) <= 0 ? this : other);
    }

    /**
     * Returns the maximum of this {@code Num} and {@code other}.
     * * 返回此 {@code Num} 和 {@code other} 的最大值。
     *
     * @param other value with which the maximum is to be computed
     *              计算最大值的值
     *
     * @return the {@code Num} whose value is the greater of this {@code Num} and  {@code other}. If they are equal, as defined by the {@link #compareTo(Num) compareTo} method, {@code this} is returned.
     * * @return {@code Num} 的值是这个 {@code Num} 和 {@code other} 中的较大者。 如果它们相等，如 {@link #compareTo(Num) compareTo} 方法所定义，则返回 {@code this}。
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
     * {@inheritDoc} Warning: This method returns true if `this` and `obj` are both NaN.NaN.
     * * {@inheritDoc} 警告：如果 `this` 和 `obj` 都是 NaN.NaN，则此方法返回 true。
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
        // x^a uses PrecisionNum ((PrecisionNum) x).pow(int a) cannot overflow Num
        // x^b uses double Math.pow(double x, double b) cannot overflow double because b
        // < 1.
        // As suggested: https://stackoverflow.com/a/3590314
        // 没有 BigDecimal.pow(BigDecimal)。 我们可以这样做：
        // double Math.pow(double delegate.doubleValue(), double n)
        // 但这可能会溢出三个双精度数中的任何一个。
        // 改为执行：
        // x^(a+b) = x^a * x^b
        // 在哪里：
        // n = a+b
        // a 是一个整数（确保它不会溢出 int）
        // 余数 0 <= b < 1
        // 所以：
        // x^a 使用 PrecisionNum ((PrecisionNum) x).pow(int a) 不能溢出 Num
        // x^b 使用 double Math.pow(double x, double b) 不能溢出 double 因为 b
        // < 1。
        // 如建议：https://stackoverflow.com/a/3590314

        // get n = a+b, same precision as n
        // 得到 n = a+b，与 n 相同的精度
        BigDecimal aplusb = (((DecimalNum) n).delegate);
        // get the remainder 0 <= b < 1, looses precision as double
        // 得到余数 0 <= b < 1，精度损失为 double
        BigDecimal b = aplusb.remainder(BigDecimal.ONE);
        // bDouble looses precision
        // b Double 失去精度
        double bDouble = b.doubleValue();
        // get the whole number a
        //获取整数a
        BigDecimal a = aplusb.subtract(b);
        // convert a to an int, fails on overflow
        // 将 a 转换为 int，溢出失败
        int aInt = a.intValueExact();
        // use BigDecimal pow(int)
        // 使用 BigDecimal pow(int)
        BigDecimal xpowa = delegate.pow(aInt);
        // use double pow(double, double)
        // 使用双 pow(double, double)
        double xpowb = Math.pow(delegate.doubleValue(), bDouble);
        // use PrecisionNum.multiply(PrecisionNum)
        // 使用 PrecisionNum.multiply(PrecisionNum)
        BigDecimal result = xpowa.multiply(new BigDecimal(xpowb));
        return new DecimalNum(result.toString());
    }

}
