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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicator;

/**
 * A fixed indicator.
 * 一个固定的指标。
 *
 * 固定指标可能指的是一种在技术分析中使用的指标，其输出值是根据固定规则或者固定参数计算得出的，不随市场价格或其他因素的变化而改变。这些指标通常用于识别市场趋势、价格变动、交易信号等，并且在运用时不需要根据市场情况进行调整。
 * 一种常见的固定指标是移动平均线（Moving Average），它根据固定的时间周期（如20天、50天、200天等）计算平均价格，并且输出的结果是一个固定的数值序列。移动平均线常被用于识别价格趋势的方向以及支撑和阻力水平。
 * 另一个例子是布林带（Bollinger Bands），它也是根据固定的参数计算得出的，包括移动平均线和价格波动的标准差。布林带通常用于识别价格波动的强度和方向，并且提供了价格在一个固定范围内的上限和下限。
 * 这些固定指标在技术分析中起着重要的作用，因为它们提供了一种基于固定规则的分析方法，可以帮助交易者制定交易策略并做出决策。然而，需要注意的是，虽然这些指标的计算是固定的，但在实际应用中仍然需要结合其他因素进行综合分析。
 * 
 * @param <T> the type of returned value (Double, Boolean, etc.)
 *           返回值的类型（Double、Boolean 等）
 */
public class FixedIndicator<T> extends AbstractIndicator<T> {

    private final List<T> values = new ArrayList<>();

    /**
     * Constructor.
     * 构造函数
     * 
     * @param values the values to be returned by this indicator
     *               该指标要返回的值
     */
    @SafeVarargs
    public FixedIndicator(BarSeries series, T... values) {
        super(series);
        this.values.addAll(Arrays.asList(values));
    }

    public void addValue(T value) {
        this.values.add(value);
    }

    @Override
    public T getValue(int index) {
        return values.get(index);
    }

}
