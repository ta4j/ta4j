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
 * * 使用通用逻辑运算符将任何十进制指标转换为布尔指标。
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
