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

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicator;

/**
 * Constant indicator.
 * 恒定指标。
 *
 * "Constant indicator"（常数指标）并不是一个常见的技术分析指标，因为它不随市场价格的波动而变化。相反，它是一个静态值，通常在技术分析中用于设置阈值、参数或者作为其他指标的一部分。这种指标在定义时通常不依赖于价格数据，而是被固定在某个数值上。
 *
 * 常数指标可能会在技术分析的各个方面中发挥作用，包括：
 * - **阈值设定**：常数指标可以用于设定过滤信号的阈值，例如，设定买入信号的阈值为一个固定的价格水平。
 * - **参数设定**：在一些技术指标中，常数值可能作为参数，用于调整指标的计算方式，例如，移动平均线中的周期参数。
 * - **范围设定**：在某些情况下，常数指标可能用于定义价格波动的范围，例如，波动率指标中的固定波动范围。
 *
 * 尽管常数指标本身并不提供价格或趋势方向的信息，但它们在技术分析中仍然是非常重要的，因为它们可以用于设定规则、参数和条件，从而帮助确定交易策略的执行条件和行为。
 *
 *
 */
public class ConstantIndicator<T> extends AbstractIndicator<T> {

    private final T value;

    public ConstantIndicator(BarSeries series, T t) {
        super(series);
        this.value = t;
    }

    @Override
    public T getValue(int index) {
        return value;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " Value: " + value;
    }
}
