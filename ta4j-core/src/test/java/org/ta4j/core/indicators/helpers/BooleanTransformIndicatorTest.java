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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.BooleanTransformIndicator.BooleanTransformSimpleType;
import org.ta4j.core.indicators.helpers.BooleanTransformIndicator.BooleanTransformType;
import org.ta4j.core.num.Num;

public class BooleanTransformIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    private BooleanTransformIndicator transEquals;
    private BooleanTransformIndicator transIsGreaterThan;
    private BooleanTransformIndicator transIsGreaterThanOrEqual;
    private BooleanTransformIndicator transIsLessThan;
    private BooleanTransformIndicator transIsLessThanOrEqual;

    private BooleanTransformIndicator transIsNaN;
    private BooleanTransformIndicator transIsNegative;
    private BooleanTransformIndicator transIsNegativeOrZero;
    private BooleanTransformIndicator transIsPositive;
    private BooleanTransformIndicator transIsPositiveOrZero;
    private BooleanTransformIndicator transIsZero;

    public BooleanTransformIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        Num FOUR = numFunction.apply(4);
        Num minusFOUR = numFunction.apply(-4);
        BarSeries series = new BaseBarSeries();
        ConstantIndicator<Num> constantIndicator = new ConstantIndicator<Num>(series, FOUR);

        transEquals = new BooleanTransformIndicator(constantIndicator, FOUR, BooleanTransformType.equals);
        transIsGreaterThan = new BooleanTransformIndicator(constantIndicator, numFunction.apply(3),
                BooleanTransformType.isGreaterThan);
        transIsGreaterThanOrEqual = new BooleanTransformIndicator(constantIndicator, FOUR,
                BooleanTransformType.isGreaterThanOrEqual);
        transIsLessThan = new BooleanTransformIndicator(constantIndicator, numFunction.apply(10),
                BooleanTransformType.isLessThan);
        transIsLessThanOrEqual = new BooleanTransformIndicator(constantIndicator, FOUR,
                BooleanTransformType.isLessThanOrEqual);

        transIsNaN = new BooleanTransformIndicator(constantIndicator, BooleanTransformSimpleType.isNaN);
        transIsNegative = new BooleanTransformIndicator(new ConstantIndicator<Num>(series, minusFOUR),
                BooleanTransformSimpleType.isNegative);
        transIsNegativeOrZero = new BooleanTransformIndicator(constantIndicator,
                BooleanTransformSimpleType.isNegativeOrZero);
        transIsPositive = new BooleanTransformIndicator(constantIndicator, BooleanTransformSimpleType.isPositive);
        transIsPositiveOrZero = new BooleanTransformIndicator(constantIndicator,
                BooleanTransformSimpleType.isPositiveOrZero);
        transIsZero = new BooleanTransformIndicator(new ConstantIndicator<Num>(series, numFunction.apply(0)),
                BooleanTransformSimpleType.isZero);
    }

    @Test
    public void getValue() {
        assertTrue(transEquals.getValue(0));
        assertTrue(transIsGreaterThan.getValue(0));
        assertTrue(transIsGreaterThanOrEqual.getValue(0));
        assertTrue(transIsLessThan.getValue(0));
        assertTrue(transIsLessThanOrEqual.getValue(0));

        assertFalse(transIsNaN.getValue(0));
        assertTrue(transIsNegative.getValue(0));
        assertFalse(transIsNegativeOrZero.getValue(0));
        assertTrue(transIsPositive.getValue(0));
        assertTrue(transIsPositiveOrZero.getValue(0));
        assertTrue(transIsZero.getValue(0));
    }
}
