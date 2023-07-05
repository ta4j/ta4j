/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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
 *
 * <p>
 * Transforms any decimal indicator to a boolean indicator by using common
 * logical operators.
 */
public class BooleanTransformIndicator extends CachedIndicator<Boolean> {

    /**
     * Select the type for transformation.
     */
    public enum BooleanTransformType {

        /**
         * Transforms the decimal indicator to a boolean indicator by
         * indicator.equals(coefficient).
         */
        equals,

        /**
         * Transforms the decimal indicator to a boolean indicator by
         * indicator.isGreaterThan(coefficient).
         */
        isGreaterThan,

        /**
         * Transforms the decimal indicator to a boolean indicator by
         * indicator.isGreaterThanOrEqual(coefficient).
         */
        isGreaterThanOrEqual,

        /**
         * Transforms the decimal indicator to a boolean indicator by
         * indicator.isLessThan(coefficient).
         */
        isLessThan,

        /**
         * Transforms the decimal indicator to a boolean indicator by
         * indicator.isLessThanOrEqual(coefficient).
         */
        isLessThanOrEqual
    }

    /**
     * Select the type for transformation.
     */
    public enum BooleanTransformSimpleType {
        /**
         * Transforms the decimal indicator to a boolean indicator by indicator.isNaN().
         */
        isNaN,

        /**
         * Transforms the decimal indicator to a boolean indicator by
         * indicator.isNegative().
         */
        isNegative,

        /**
         * Transforms the decimal indicator to a boolean indicator by
         * indicator.isNegativeOrZero().
         */
        isNegativeOrZero,

        /**
         * Transforms the decimal indicator to a boolean indicator by
         * indicator.isPositive().
         */
        isPositive,

        /**
         * Transforms the decimal indicator to a boolean indicator by
         * indicator.isPositiveOrZero().
         */
        isPositiveOrZero,

        /**
         * Transforms the decimal indicator to a boolean indicator by
         * indicator.isZero().
         */
        isZero
    }

    private final Indicator<Num> indicator;
    private final Num coefficient;
    private final BooleanTransformType type;
    private final BooleanTransformSimpleType simpleType;

    /**
     * Constructor.
     *
     * @param indicator   the indicator
     * @param coefficient the value for transformation
     * @param type        the type of the transformation
     */
    public BooleanTransformIndicator(Indicator<Num> indicator, Num coefficient, BooleanTransformType type) {
        super(indicator);
        this.indicator = indicator;
        this.coefficient = coefficient;
        this.type = type;
        this.simpleType = null;
    }

    /**
     * Constructor.
     *
     * @param indicator the indicator
     * @param type      the type of the transformation
     */
    public BooleanTransformIndicator(Indicator<Num> indicator, BooleanTransformSimpleType type) {
        super(indicator);
        this.indicator = indicator;
        this.simpleType = type;
        this.coefficient = null;
        this.type = null;
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
    public int getUnstableBars() {
        return 0;
    }

    @Override
    public String toString() {
        if (type != null) {
            return getClass().getSimpleName() + " Coefficient: " + coefficient + " Transform(" + type.name() + ")";
        }
        return getClass().getSimpleName() + "Transform(" + simpleType.name() + ")";
    }
}
