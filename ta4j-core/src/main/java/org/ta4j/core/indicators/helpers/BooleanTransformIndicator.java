/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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

import java.util.function.Predicate;

/**
 * Transforms any indicator to a boolean indicator.
 */
public class BooleanTransformIndicator<T> extends CachedIndicator<Boolean> {

    public static BooleanTransformIndicator<Num> equals(Indicator<Num> indicator, Num constant) {
        return new BooleanTransformIndicator<>(indicator, num -> num.equals(constant));
    }

    public static BooleanTransformIndicator<Num> notEquals(Indicator<Num> indicator, Num constant) {
        return new BooleanTransformIndicator<>(indicator, num -> !num.equals(constant));
    }

    public static BooleanTransformIndicator<Num> isEqual(Indicator<Num> indicator, Num constant) {
        return new BooleanTransformIndicator<>(indicator, num -> num.isEqual(constant));
    }

    public static BooleanTransformIndicator<Num> isNotEqual(Indicator<Num> indicator, Num constant) {
        return new BooleanTransformIndicator<>(indicator, num -> !num.isEqual(constant));
    }

    public static BooleanTransformIndicator<Num> isGreaterThan(Indicator<Num> indicator, Num constant) {
        return new BooleanTransformIndicator<>(indicator, num -> num.isGreaterThan(constant));
    }

    public static BooleanTransformIndicator<Num> isGreaterThanOrEqual(Indicator<Num> indicator, Num constant) {
        return new BooleanTransformIndicator<>(indicator, num -> num.isGreaterThanOrEqual(constant));
    }

    public static BooleanTransformIndicator<Num> isLessThan(Indicator<Num> indicator, Num constant) {
        return new BooleanTransformIndicator<>(indicator, num -> num.isLessThan(constant));
    }

    public static BooleanTransformIndicator<Num> isLessThanOrEqual(Indicator<Num> indicator, Num constant) {
        return new BooleanTransformIndicator<>(indicator, num -> num.isLessThanOrEqual(constant));
    }

    public static BooleanTransformIndicator<Num> isZero(Indicator<Num> indicator) {
        return new BooleanTransformIndicator<>(indicator, Num::isZero);
    }

    public static BooleanTransformIndicator<Num> isNaN(Indicator<Num> indicator) {
        return new BooleanTransformIndicator<>(indicator, Num::isNaN);
    }

    public static BooleanTransformIndicator<Num> isPositive(Indicator<Num> indicator) {
        return new BooleanTransformIndicator<>(indicator, Num::isPositive);
    }

    public static BooleanTransformIndicator<Num> isPositiveOrZero(Indicator<Num> indicator) {
        return new BooleanTransformIndicator<>(indicator, Num::isPositiveOrZero);
    }

    public static BooleanTransformIndicator<Num> isNegative(Indicator<Num> indicator) {
        return new BooleanTransformIndicator<>(indicator, Num::isNegative);
    }

    public static BooleanTransformIndicator<Num> isNegativeOrZero(Indicator<Num> indicator) {
        return new BooleanTransformIndicator<>(indicator, Num::isNegativeOrZero);
    }

    private final Indicator<T> indicator;
    private final Predicate<T> transform;

    /**
     * Constructor.
     *
     * @param indicator the indicator
     * @param transform the transform {@link Predicate} to apply
     */
    public BooleanTransformIndicator(Indicator<T> indicator, Predicate<T> transform) {
        super(indicator);
        this.indicator = indicator;
        this.transform = transform;
    }

    @Override
    protected Boolean calculate(int index) {
        return transform.test(indicator.getValue(index));
    }

    /**
     * @return {@code 0}
     */
    @Override
    public int getUnstableBars() {
        return 0;
    }
}
