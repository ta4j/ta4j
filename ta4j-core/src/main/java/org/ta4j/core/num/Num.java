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
package org.ta4j.core.num;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.function.Function;

/**
 * Ta4js definition of operations that must be fulfilled by an object that should be used as base for calculations
 * * Ta4js 定义的操作必须由应用作计算基础的对象完成
 *
 * @see Num
 * @see Num#function()
 * @see DoubleNum
 * @see DecimalNum
 * 
 */
public interface Num extends Comparable<Num>, Serializable {

    /**
     * @return the delegate used from this <code>Num</code> implementation
     *      * @return the delegate used from this <code>Num</code> implementation
     */
    Number getDelegate();

    /**
     * Returns the name/description of this Num implementation
     * * 返回此 Num 实现的名称/描述
     * 
     * @return the name/description
     * * @return 名称/描述
     */
    String getName();

    /**
     * Returns a {@code num} whose value is {@code (this + augend)},
     * * 返回一个 {@code num}，其值为 {@code (this + augend)}，
     * 
     * @param augend value to be added to this {@code num}.
     *               要添加到此 {@code number} 的值。
     *
     * @return {@code this + augend}, rounded as necessary
     * @return {@code this + augend}，根据需要四舍五入
     */
    Num plus(Num augend);

    /**
     * Returns a {@code num} whose value is {@code (this - augend)},
     * * 返回一个 {@code num}，其值为 {@code (this - augend)}，
     * 
     * @param subtrahend value to be subtracted from this {@code num}.
     *                   要从此 {@code num} 中减去的 @param 减数值。
     *
     * @return {@code this - subtrahend}, rounded as necessary
     * @return {@code this - subtrahend}，根据需要四舍五入
     */
    Num minus(Num subtrahend);

    /**
     * Returns a {@code num} whose value is {@code this * multiplicand},
     * * 返回一个 {@code num}，其值为 {@code this * multiplicand}，
     * 
     * @param multiplicand value to be multiplied by this {@code num}.
     *                     * @param 被乘以这个 {@code number} 的值。
     *
     * @return {@code this * multiplicand}, rounded as necessary
     * * @return {@code this * multipliand}，根据需要四舍五入
     */
    Num multipliedBy(Num multiplicand);

    /**
     * Returns a {@code num} whose value is {@code (this / divisor)},
     * * 返回一个 {@code num}，其值为 {@code (this / divisor)}，
     * 
     * @param divisor value by which this {@code num} is to be divided.
     *                * @param 除数值，此 {@code num} 将被除数。
     *
     * @return {@code this / divisor}, rounded as necessary
     * * @return {@code this / divisor}，根据需要四舍五入
     */
    Num dividedBy(Num divisor);

    /**
     * Returns a {@code num} whose value is {@code (this % divisor)},
     * * 返回一个 {@code num}，其值为 {@code (this % divisor)}，
     * 
     * @param divisor value by which this {@code num} is to be divided.
     *                * @param 除数值，此 {@code num} 将被除数。
     *
     * @return {@code this % divisor}, rounded as necessary.
     * * @return {@code this % divisor}，根据需要四舍五入。
     */
    Num remainder(Num divisor);

    /**
     * Returns a {@code Num} whose value is rounded down to the nearest whole number.
     * * 返回一个 {@code Num}，其值向下舍入到最接近的整数。
     *
     * @return <code>this</code> to whole Num rounded down
     * @return <code>this</code> 为向下舍入的整数
     */
    Num floor();

    /**
     * Returns a {@code Num} whose value is rounded up to the nearest whole number.
     * * 返回一个 {@code Num} ，其值四舍五入到最接近的整数。
     * 
     * @return <code>this</code> to whole Num rounded up
     * @return <code>this</code> 为四舍五入的整数
     */
    Num ceil();

    /**
     * Returns a {@code num} whose value is <code>(this<sup>n</sup>)</code>.
     * * 返回一个 {@code num}，其值为 <code>(this<sup>n</sup>)</code>。
     * 
     * @param n power to raise this {@code num} to.
     *          @param n 将这个 {@code num} 提升到的权力。
     *
     * @return <code>this<sup>n</sup></code>
     * @return <code>这个<sup>n</sup></code>
     */
    Num pow(int n);

    /**
     * Returns a {@code num} whose value is <code>(this<sup>n</sup>)</code>.
     * * 返回一个 {@code num}，其值为 <code>(this<sup>n</sup>)</code>。
     * 
     * @param n power to raise this {@code num} to.
     *          * @param n 将这个 {@code num} 提升到的权力。
     *
     * @return <code>this<sup>n</sup></code>
     * @return <code>这个<sup>n</sup></code>
     */
    Num pow(Num n);

    /**
     * Returns a {@code num} whose value is <code>ln(this)</code>.
     * * 返回一个 {@code number}，其值为 <code>in(this)</code>。
     * 
     * @return <code>this<sup>n</sup></code>
     * @return <code>这个<sup>n</sup></code>
     */
    Num log();

    /**
     * Returns a {@code num} whose value is <code>√(this)</code>.
     * * 返回一个 {@code num}，其值为 <code>√(this)</code>。
     * 
     * @return <code>this<sup>n</sup></code>
     * @return <code>这个<sup>n</sup></code>
     */
    Num sqrt();

    /**
     * Returns a {@code num} whose value is <code>√(this)</code>.
     * * 返回一个 {@code num}，其值为 <code>√(this)</code>。
     * 
     * @param precision to calculate.
     *                  计算精度。
     * @return <code>this<sup>n</sup></code>
     */
    Num sqrt(int precision);

    /**
     * Returns a {@code num} whose value is the absolute value of this {@code num}.
     * * 返回一个 {@code num}，其值为此 {@code num} 的绝对值。
     *
     * @return {@code abs(this) 绝对（这个）}
     */
    Num abs();

    /**
     * Returns a {@code num} whose value is (-this), and whose scale is this.scale().
     * * 返回一个 {@code num}，其值为 (-this)，其刻度为 this.scale()。
     * 
     * @return {@code negate(this) 否定（这个）}
     */
    Num negate();

    /**
     * Checks if the value is zero.
     * 检查值是否为零。
     * 
     * @return true if the value is zero, false otherwise
     * * @return 如果值为零，则返回 true，否则返回 false
     */
    boolean isZero();

    /**
     * Checks if the value is greater than zero.
     * 检查值是否大于零。
     * 
     * @return true if the value is greater than zero, false otherwise
     * * @return 如果值大于零，则返回 true，否则返回 false
     */
    boolean isPositive();

    /**
     * Checks if the value is zero or greater.
     * * 检查值是否为零或更大。
     * 
     * @return true if the value is zero or greater, false otherwise
     * * @return 如果值为零或更大，则返回 true，否则返回 false
     */
    boolean isPositiveOrZero();

    /**
     * Checks if the value is less than zero.
     * 检查值是否小于零。
     * 
     * @return true if the value is less than zero, false otherwise
     * * @return 如果值小于零，则返回 true，否则返回 false
     */
    boolean isNegative();

    /**
     * Checks if the value is zero or less.
     * * 检查值是否为零或更小。
     * 
     * @return true if the value is zero or less, false otherwise
     * * @return 如果值为零或更小，则返回 true，否则返回 false
     */
    boolean isNegativeOrZero();

    /**
     * Checks if this value is equal to another.
     * 检查此值是否等于另一个值。
     * 
     * @param other the other value, not null
     *              * @param other 其他值，不为空
     *
     * @return true if this is greater than the specified value, false otherwise
     * * @return 如果大于指定值则返回 true，否则返回 false
     */
    boolean isEqual(Num other);

    /**
     * Checks if this value is greater than another.
     * 检查此值是否大于另一个值。
     * 
     * @param other the other value, not null
     *              * @param other 其他值，不为空
     *
     * @return true if this is greater than the specified value, false otherwise
     * * @return 如果大于指定值则返回 true，否则返回 false
     */
    boolean isGreaterThan(Num other);

    /**
     * Checks if this value is greater than or equal to another.
     * 检查此值是否大于或等于另一个值。
     *
     * @param other the other value, not null
     *              另一个值，不为空
     *
     * @return true if this is greater than or equal to the specified value, false  otherwise
     * * @return 如果大于或等于指定值，则返回 true，否则返回 false
     */
    boolean isGreaterThanOrEqual(Num other);

    /**
     * Checks if this value is less than another.
     * 检查此值是否小于另一个值。
     * 
     * @param other the other value, not null
     *              另一个值，不为空
     *
     * @return true if this is less than the specified value, false otherwise
     * * @return 如果小于指定值则返回 true，否则返回 false
     */
    boolean isLessThan(Num other);

    /**
     * Checks if this value is less than another.
     * * 检查这个值是否小于另一个。
     * 
     * @param other the other value, not null
     *              另一个值，不为空
     *
     * @return true if this is less than or equal the specified value, false  otherwise
     * * @return 如果小于或等于指定值，则返回 true，否则返回 false
     */
    boolean isLessThanOrEqual(Num other);

    /**
     * Returns the minimum of this {@code num} and {@code other}.
     *  * 返回此 {@code num} 和 {@code other} 的最小值。
     * 
     * @param other value with which the minimum is to be computed
     *              * @param 计算最小值的其他值
     *
     * @return the {@code num} whose value is the lesser of this {@code num} and   {@code other}. If they are equal, method, {@code this} is returned.
     *  * @return {@code num} 的值是这个 {@code num} 和 {@code other} 中较小的一个。 如果它们相等，则返回方法 {@code this}。
     */
    Num min(Num other);

    /**
     * Returns the maximum of this {@code num} and {@code other}.
     * * 返回此 {@code num} 和 {@code other} 的最大值。
     * 
     * @param other value with which the maximum is to be computed
     *              * @param 计算最大值的其他值
     *
     * @return the {@code num} whose value is the greater of this {@code num} and
     * *     @return {@code num} 的值是这个 {@code num} 中较大的一个，并且
     *         {@code other}. If they are equal, method, {@code this} is returned.
     *         {@code 其他}。 如果它们相等，则返回方法 {@code this}。
     */
    Num max(Num other);

    /**
     * Returns the {@link Function} to convert a number instance into the corresponding Num instance
     *  * 返回{@link Function} 将数字实例转换为对应的Num实例
     * 
     * @return function which converts a number instance into the corresponding Num   instance
     *  * @return 函数将数字实例转换为相应的 Num 实例
     */
    Function<Number, Num> function();

    /**
     * Transforms a {@link Number} into a new Num instance of this <code>Num</code> implementation
     * * 将 {@link Number} 转换为此 <code>Num</code> 实现的新 Num 实例
     * 
     * @param value the Number to transform
     *              要转换的数字
     *
     * @return the corresponding Num implementation of the <code>value</code>
     *  * @return <code>value</code>对应的Num实现
     */
    default Num numOf(Number value) {
        return function().apply(value);
    }

    /**
     * Transforms a {@link String} into a new Num instance of this with a precision <code>Num</code> implementation
     * * 使用精确的 <code>Num</code> 实现将 {@link String} 转换为 this 的新 Num 实例
     * 
     * @param value     the String to transform
     *                  要转换的字符串
     *
     * @param precision the precision
     *                  精度
     *
     * @return the corresponding Num implementation of the <code>value</code>
     * @return <code>value</code>对应的Num实现
     */
    default Num numOf(String value, int precision) {
        MathContext mathContext = new MathContext(precision, RoundingMode.HALF_UP);
        return this.numOf(new BigDecimal(value, mathContext));
    }

    /**
     * Only for NaN this should be true
     * * 仅对于 NaN 这应该是真的
     * 
     * @return false if this implementation is not NaN
     * * @return false 如果这个实现不是 NaN
     */
    default boolean isNaN() {
        return false;
    }

    /**
     * Converts this {@code num} to a {@code double}.
     * * 将此 {@code num} 转换为 {@code double}。
     * 
     * @return this {@code num} converted to a {@code double}
     * * @return 这个 {@code num} 转换为 {@code double}
     */
    default double doubleValue() {
        return getDelegate().doubleValue();
    }

    default int intValue() {
        return getDelegate().intValue();
    }

    default long longValue() {
        return getDelegate().longValue();
    }

    default float floatValue() {
        return getDelegate().floatValue();
    }

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
