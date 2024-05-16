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
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Returns the previous (n-th) value of an indicator
 * 返回指标的前一个（第 n 个）值
 *
 * 要获取指标的前 n 个值，你可以使用历史数据或指标本身的计算结果，具体取决于数据的可用性以及计算指标的方法。以下是一般的方法：
 *
 * 1. **使用历史数据**：如果你有历史数据，你可以直接查找 n 期前的指标值。例如，如果你想获取 10 期前的移动平均值，你可以在历史数据中查找移动平均值 10 期前的数值。
 *
 * 2. **使用指标计算**：如果你是通过编程计算指标或者有指标计算逻辑的访问权限，你可以直接从计算中获取指标的第 n 个值。例如，如果你正在计算移动平均线，你可以在计算过程中直接获取 n 期前的移动平均值。
 *
 * 以下是一个用 Python 代码示例的简单示例，演示了如何使用历史数据来获取指标的前 n 个值：
 *
 * ```python
 * # 假设你有一个包含指标历史值的列表或数组
 * indicator_values = [10, 15, 20, 25, 30, 35, 40, 45, 50, 55]
 *
 * # 函数用于获取指标的前 n 个值
 * def get_previous_value(indicator_values, n):
 *     if n >= len(indicator_values):
 *         return None  # 如果历史数据不足，则返回 None
 *     return indicator_values[-n]
 *
 * # 示例用法：获取 5 期前的指标值
 * n = 5
 * previous_value = get_previous_value(indicator_values, n)
 * print("指标", n, "期前的值是:", previous_value)
 * ```
 *
 * 用你的实际历史数据替换 `indicator_values`，并根据需要调整 `n` 的值，以获取你的指标的前 n 个值。
 *
 */
public class PreviousValueIndicator extends CachedIndicator<Num> {

    private final int n;
    private Indicator<Num> indicator;

    /**
     * Constructor.
     *
     * @param indicator the indicator of which the previous value should be   calculated
     *                       *
     */
    public PreviousValueIndicator(Indicator<Num> indicator) {
        this(indicator, 1);
    }

    /**
     * Constructor.
     * 构造函数
     *
     * @param indicator the indicator of which the previous value should be   calculated
     *                  应计算其先前值的指标
     * @param n         parameter defines the previous n-th value
     *                  参数定义前第 n 个值
     */
    public PreviousValueIndicator(Indicator<Num> indicator, int n) {
        super(indicator);
        if (n < 1) {
            throw new IllegalArgumentException("n must be positive number, but was n 必须是正数，但是是: " + n);
        }
        this.n = n;
        this.indicator = indicator;
    }

    protected Num calculate(int index) {
        int previousValue = Math.max(0, (index - n));
        return this.indicator.getValue(previousValue);
    }

    @Override
    public String toString() {
        final String nInfo = n == 1 ? "" : "(" + n + ")";
        return getClass().getSimpleName() + nInfo + "[" + this.indicator + "]";
    }
}