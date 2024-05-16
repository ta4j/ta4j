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
 * Simple boolean transform indicator.
 * 简单的布尔变换指標。
 *
 * Transforms any decimal indicator to a boolean indicator by using common logical operators.
 * 使用通用逻辑运算符将任何十进制指示符转换为布尔指示符。
 *
 * Simple Boolean Transform（简单布尔变换）是一种技术分析指标，用于识别价格走势的趋势性质。它基于布尔值逻辑，通过对价格序列的变换来生成信号。这个指标通常用于股票、期货、外汇等交易市场中。
 *
 * ### 计算方法
 * Simple Boolean Transform 指标的计算步骤如下：
 * 1. 将价格序列（如收盘价）按照一定周期（通常是一个固定的时间段）分成若干组。
 * 2. 对每组价格数据进行布尔变换：
 *    - 如果当前价格高于前一个周期的价格，则变换值为1。
 *    - 如果当前价格低于前一个周期的价格，则变换值为0。
 * 3. 对所有变换后的值进行求和，得到一个总的变换值。
 * 4. 将总的变换值进行平滑处理，常用的平滑方法包括移动平均线或加权移动平均线。
 *
 * ### 意义
 * Simple Boolean Transform 指标的主要意义在于：
 * - 提供了一种简单的方法来衡量价格序列的趋势性质，即当前价格相对于前一个周期的价格是上升还是下降。
 * - 可以作为其他技术指标的辅助，用于确认价格趋势或生成交易信号。
 *
 * ### 应用
 * Simple Boolean Transform 指标的应用包括但不限于以下方面：
 * - **趋势确认**：当布尔变换值连续上升时，表明价格处于上升趋势；当布尔变换值连续下降时，表明价格处于下降趋势。
 * - **信号生成**：当布尔变换值发生反转时，可能产生买入或卖出信号，例如，当布尔变换值从下降转为上升时，可能产生买入信号。
 * - **过滤器**：可作为其他指标的过滤器，用于排除某些无效的信号，提高交易策略的准确性。
 *
 * ### 注意事项
 * - Simple Boolean Transform 指标是一种相对简单的指标，其信号可能存在滞后性，并且对噪声敏感，因此在使用时需要慎重考虑。
 * - 建议与其他技术指标或价格分析方法结合使用，以提高交易决策的准确性。
 * - 交易者应该根据具体市场和交易策略来调整 Simple Boolean Transform 指标的参数，以获得最佳效果。
 *
 * ### 总结
 * Simple Boolean Transform 指标是一种用于识别价格走势趋势性质的技术分析指标，基于布尔值逻辑对价格序列进行变换和处理。它可以作为趋势确认、信号生成和过滤器等方面的辅助工具，但在使用时需要结合其他技术指标和价格分析方法，并根据具体情况进行调整和确认。
 *
 */
public class BooleanTransformIndicator extends CachedIndicator<Boolean> {

    /**
     * Select the type for transformation.
     * 选择转换类型。
     */
    public enum BooleanTransformType {

        /**
         * Transforms the decimal indicator to a boolean indicator by indicator.equals(coefficient).
         * 通过 indicator.equals(coefficient) 将小数指标转换为布尔指标。
         */
        equals,

        /**
         * Transforms the decimal indicator to a boolean indicator by indicator.isGreaterThan(coefficient).
         * 通过 indicator.isGreaterThan(coefficient) 将小数指标转换为布尔指标。
         */
        isGreaterThan,

        /**
         * Transforms the decimal indicator to a boolean indicator by indicator.isGreaterThanOrEqual(coefficient).
         * 通过 indicator.isGreaterThanOrEqual(coefficient) 将小数指标转换为布尔指标。
         */
        isGreaterThanOrEqual,

        /**
         * Transforms the decimal indicator to a boolean indicator by indicator.isLessThan(coefficient).
         * 通过 indicator.isLessThan(coefficient) 将小数指标转换为布尔指标。
         */
        isLessThan,

        /**
         * Transforms the decimal indicator to a boolean indicator by indicator.isLessThanOrEqual(coefficient).
         * 通过 indicator.isLessThanOrEqual(coefficient) 将十进制指标转换为布尔指标。
         */
        isLessThanOrEqual
    }

    /**
     * Select the type for transformation.
     * 选择转换类型。
     */
    public enum BooleanTransformSimpleType {
        /**
         * Transforms the decimal indicator to a boolean indicator by indicator.isNaN().
         * 通过 indicator.isNaN() 将十进制指标转换为布尔指标。
         */
        isNaN,

        /**
         * Transforms the decimal indicator to a boolean indicator by indicator.isNegative().
         * 通过indicator.isNegative() 将十进制指标转换为布尔指标。
         */
        isNegative,

        /**
         * Transforms the decimal indicator to a boolean indicator by indicator.isNegativeOrZero().
         * 通过indicator.isNegativeOrZero() 将十进制指标转换为布尔指标。
         */
        isNegativeOrZero,

        /**
         * Transforms the decimal indicator to a boolean indicator by indicator.isPositive().
         * 通过 indicator.isPositive() 将小数指标转换为布尔指标。
         */
        isPositive,

        /**
         * Transforms the decimal indicator to a boolean indicator by indicator.isPositiveOrZero().
         * 通过indicator.isPositiveOrZero() 将十进制指标转换为布尔指标。
         */
        isPositiveOrZero,

        /**
         * Transforms the decimal indicator to a boolean indicator by indicator.isZero().
         * 通过indicator.isZero() 将十进制指标转换为布尔指标。
         */
        isZero
    }

    private Indicator<Num> indicator;
    private Num coefficient;
    private BooleanTransformType type;
    private BooleanTransformSimpleType simpleType;

    /**
     * Constructor.
     * 
     * @param indicator   the indicator
     *                    指标
     * @param coefficient the value for transformation
     *                    转型价值
     * @param type        the type of the transformation
     *                    转换的类型
     */
    public BooleanTransformIndicator(Indicator<Num> indicator, Num coefficient, BooleanTransformType type) {
        super(indicator);
        this.indicator = indicator;
        this.coefficient = coefficient;
        this.type = type;
    }

    /**
     * Constructor.
     * 
     * @param indicator the indicator
     *                  指标
     * @param type      the type of the transformation
     *                  转换的类型
     */
    public BooleanTransformIndicator(Indicator<Num> indicator, BooleanTransformSimpleType type) {
        super(indicator);
        this.indicator = indicator;
        this.simpleType = type;
    }

    @Override
    protected Boolean calculate(int index) {

        Num val = indicator.getValue(index);

        if (type != null) {
            switch (type) {
            case equals:
                return val.equals(coefficient);
            case isGreaterThan:
                return val.isGreaterThan(coefficient);
            case isGreaterThanOrEqual:
                return val.isGreaterThanOrEqual(coefficient);
            case isLessThan:
                return val.isLessThan(coefficient);
            case isLessThanOrEqual:
                return val.isLessThanOrEqual(coefficient);
            default:
                break;
            }
        }

        else if (simpleType != null) {
            switch (simpleType) {
            case isNaN:
                return val.isNaN();
            case isNegative:
                return val.isNegative();
            case isNegativeOrZero:
                return val.isNegativeOrZero();
            case isPositive:
                return val.isPositive();
            case isPositiveOrZero:
                return val.isPositiveOrZero();
            case isZero:
                return val.isZero();
            default:
                break;
            }
        }

        return false;
    }

    @Override
    public String toString() {
        if (type != null) {
            return getClass().getSimpleName() + " Coefficient 系数: " + coefficient + " Transform 转换(" + type.name() + ")";
        }
        return getClass().getSimpleName() + "Transform 转换(" + simpleType.name() + ")";
    }
}
