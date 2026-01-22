/*
 * SPDX-License-Identifier: MIT
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
