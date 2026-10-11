/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.num;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.jupiter.api.Test;

class DecimalNumFactoryTest {

    private static final NumFactory decimalFactory = DecimalNumFactory.getInstance();

    @Test
    final void testDefaultNum() {
        assertNumEquals(-1, decimalFactory.minusOne());

        assertNumEquals(-0, decimalFactory.zero());
        assertNumEquals(0, decimalFactory.zero());
        assertNumEquals(+0, decimalFactory.zero());

        assertNumEquals(1, decimalFactory.one());
        assertNumEquals(2, decimalFactory.two());
        assertNumEquals(3, decimalFactory.three());
        assertNumEquals(100, decimalFactory.hundred());
        assertNumEquals(1000, decimalFactory.thousand());
    }

    @Test
    final void testProduces() {
        var doubleFactory = DoubleNumFactory.getInstance();

        assertTrue(decimalFactory.produces(null));
        assertTrue(decimalFactory.produces(NaN.NaN));
        assertTrue(decimalFactory.produces(decimalFactory.one()));
        assertFalse(decimalFactory.produces(doubleFactory.one()));
    }

    @Test
    void shouldComputeExpUsingConfiguredPrecision() {
        NumFactory highPrecisionFactory = DecimalNumFactory.getInstance(40);
        Num one = highPrecisionFactory.one();
        Num e = one.exp();

        assertNumEquals("2.718281828459045235360287471352662497761", e);
    }

}
