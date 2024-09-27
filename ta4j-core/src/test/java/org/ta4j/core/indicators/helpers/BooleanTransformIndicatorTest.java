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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class BooleanTransformIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    private BooleanTransformIndicator<Num> equals;
    private BooleanTransformIndicator<Num> notEquals;
    private BooleanTransformIndicator<Num> isEqual;
    private BooleanTransformIndicator<Num> isNotEqual;
    private BooleanTransformIndicator<Num> isGreaterThan;
    private BooleanTransformIndicator<Num> isGreaterThanOrEqual;
    private BooleanTransformIndicator<Num> isLessThan;
    private BooleanTransformIndicator<Num> isLessThanOrEqual;
    private BooleanTransformIndicator<Num> isZero;
    private BooleanTransformIndicator<Num> isNaN;
    private BooleanTransformIndicator<Num> isPositive;
    private BooleanTransformIndicator<Num> isPositiveOrZero;
    private BooleanTransformIndicator<Num> isNegative;
    private BooleanTransformIndicator<Num> isNegativeOrZero;

    public BooleanTransformIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        final Num four = this.numFactory.numOf(4);
        final ConstantIndicator<Num> constantIndicator = new ConstantIndicator<>(new MockBarSeriesBuilder().build(),
                four);

        equals = BooleanTransformIndicator.equals(constantIndicator, four);
        notEquals = BooleanTransformIndicator.notEquals(constantIndicator, four);
        isEqual = BooleanTransformIndicator.isEqual(constantIndicator, four);
        isNotEqual = BooleanTransformIndicator.isNotEqual(constantIndicator, four);
        isGreaterThan = BooleanTransformIndicator.isGreaterThan(constantIndicator, four);
        isGreaterThanOrEqual = BooleanTransformIndicator.isGreaterThanOrEqual(constantIndicator, four);
        isLessThan = BooleanTransformIndicator.isLessThan(constantIndicator, four);
        isLessThanOrEqual = BooleanTransformIndicator.isLessThanOrEqual(constantIndicator, four);
        isZero = BooleanTransformIndicator.isZero(constantIndicator);
        isNaN = BooleanTransformIndicator.isNaN(constantIndicator);
        isPositive = BooleanTransformIndicator.isPositive(constantIndicator);
        isPositiveOrZero = BooleanTransformIndicator.isPositiveOrZero(constantIndicator);
        isNegative = BooleanTransformIndicator.isNegative(constantIndicator);
        isNegativeOrZero = BooleanTransformIndicator.isNegativeOrZero(constantIndicator);
    }

    @Test
    public void getValue() {
        assertTrue(equals.getValue(0));
        assertFalse(notEquals.getValue(0));
        assertTrue(isEqual.getValue(0));
        assertFalse(isNotEqual.getValue(0));
        assertFalse(isGreaterThan.getValue(0));
        assertTrue(isGreaterThanOrEqual.getValue(0));
        assertFalse(isLessThan.getValue(0));
        assertTrue(isLessThanOrEqual.getValue(0));
        assertFalse(isZero.getValue(0));
        assertFalse(isNaN.getValue(0));
        assertTrue(isPositive.getValue(0));
        assertTrue(isPositiveOrZero.getValue(0));
        assertFalse(isNegative.getValue(0));
        assertFalse(isNegativeOrZero.getValue(0));
    }
}
