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

import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Transform indicator.
 * 变换指标。
 * <p>
 * Transforms the Num of any indicator by using common math operations.
 * 使用常用数学运算转换任何指标的 Num。
 *
 * @apiNote Minimal deviations in last decimal places possible. During some  calculations this indicator converts {@link Num DecimalNum} to  {@link Double double}
 * * @apiNote 最后一位小数的偏差可能最小。 在某些计算过程中，该指标将 {@link Num DecimalNum} 转换为 {@link Double double}
 *
 *
 * "Transform indicator" 是一个广义的术语，通常用来描述一种将原始数据或已有指标进行转换或转化的操作。这个术语并不是指特定的技术指标，而是一种用于描述数据处理或分析方法的术语。
 *
 * 在技术分析中，"transform indicator" 可能指代各种不同的操作，包括但不限于以下几种：
 *
 * 1. **平滑处理**：将原始价格数据或指标进行平滑处理，以减少噪音和波动性，例如通过计算移动平均线或应用滤波器来平滑价格曲线。
 *
 * 2. **变换函数**：使用数学函数对原始数据进行变换，以获得新的指标或数据序列。例如，对价格进行对数变换或百分比变换。
 *
 * 3. **标准化或归一化**：将数据转换为具有特定统计特性的形式，例如将数据缩放到特定的范围或将数据转换为标准正态分布。
 *
 * 4. **差分或差异变换**：计算数据序列之间的差异或变化，例如计算价格的一阶或二阶差分，以提取趋势或周期性成分。
 *
 * 5. **逆转或反转**：对数据进行逆转或反转，例如将价格曲线从对数坐标转换为线性坐标，或将时间序列进行反转以进行反向分析。
 *
 * 总的来说，"transform indicator" 并不指代特定的技术指标，而是指代对原始数据或已有指标进行各种形式的转换或变换操作，以获取新的数据视角或提取有用的信息。在实际应用中，具体的转换方法会根据分析的目的和数据特性而有所不同。
 *
 */
public class TransformIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final UnaryOperator<Num> transformationFunction;

    /**
     * Constructor.
     * 构造函数
     * 
     * @param indicator      the indicator
     *                       指标
     * @param transformation a {@link Function} describing the transformation
     *                       描述转换的 {@link Function}
     */
    public TransformIndicator(Indicator<Num> indicator, UnaryOperator<Num> transformation) {
        super(indicator);
        this.indicator = indicator;
        this.transformationFunction = transformation;
    }

    @Override
    protected Num calculate(int index) {
        return transformationFunction.apply(indicator.getValue(index));
    }

    /**
     * Transforms the input indicator by indicator.plus(coefficient).
     * 通过 indicator.plus(coefficient) 转换输入指标。
     */
    public static TransformIndicator plus(Indicator<Num> indicator, Number coefficient) {
        Num numCoefficient = indicator.numOf(coefficient);
        return new TransformIndicator(indicator, val -> val.plus(numCoefficient));
    }

    /**
     * Transforms the input indicator by indicator.minus(coefficient).
     * 通过 indicator.minus(coefficient) 转换输入指标。
     */
    public static TransformIndicator minus(Indicator<Num> indicator, Number coefficient) {
        Num numCoefficient = indicator.numOf(coefficient);
        return new TransformIndicator(indicator, val -> val.minus(numCoefficient));
    }

    /**
     * Transforms the input indicator by indicator.dividedBy(coefficient).
     * 通过 indicator.divided By(coefficient) 转换输入指标。
     */
    public static TransformIndicator divide(Indicator<Num> indicator, Number coefficient) {
        Num numCoefficient = indicator.numOf(coefficient);
        return new TransformIndicator(indicator, val -> val.dividedBy(numCoefficient));
    }

    /**
     * Transforms the input indicator by indicator.multipliedBy(coefficient).
     * 通过 indicator.multipliedBy(coefficient) 转换输入指标。
     */
    public static TransformIndicator multiply(Indicator<Num> indicator, Number coefficient) {
        Num numCoefficient = indicator.numOf(coefficient);
        return new TransformIndicator(indicator, val -> val.multipliedBy(numCoefficient));
    }

    /**
     * Transforms the input indicator by indicator.max(coefficient).
     * 通过 indicator.max(coefficient) 转换输入指标。
     */
    public static TransformIndicator max(Indicator<Num> indicator, Number coefficient) {
        Num numCoefficient = indicator.numOf(coefficient);
        return new TransformIndicator(indicator, val -> val.max(numCoefficient));
    }

    /**
     * Transforms the input indicator by indicator.min(coefficient).
     * 通过 indicator.min(coefficient) 转换输入指标。
     */
    public static TransformIndicator min(Indicator<Num> indicator, Number coefficient) {
        Num numCoefficient = indicator.numOf(coefficient);
        return new TransformIndicator(indicator, val -> val.min(numCoefficient));
    }

    /**
     * Transforms the input indicator by indicator.abs().
     * 通过 indicator.abs() 转换输入指标。
     */

    public static TransformIndicator abs(Indicator<Num> indicator) {
        return new TransformIndicator(indicator, Num::abs);
    }

    /**
     * Transforms the input indicator by indicator.sqrt().
     * 通过 indicator.sqrt() 转换输入指标。
     */
    public static TransformIndicator sqrt(Indicator<Num> indicator) {
        return new TransformIndicator(indicator, Num::sqrt);
    }

    /**
     * Transforms the input indicator by indicator.log().
     * 通过 indicator.log() 转换输入指标。
     *
     * @apiNote precision may be lost, because this implementation is using the  underlying doubleValue method
     * * @apiNote 精度可能会丢失，因为这个实现是使用底层的 doubleValue 方法
     */
    public static TransformIndicator log(Indicator<Num> indicator) {
        return new TransformIndicator(indicator, val -> val.numOf(Math.log(val.doubleValue())));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
