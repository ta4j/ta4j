/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

class OrderBookLevelTest {
    @Test
    void allowsSignedPricesAndZeroDeletionButRejectsInvalidValues() {
        NumFactory factory = DoubleNumFactory.getInstance();
        assertEquals(factory.zero(), new OrderBookLevel(factory.minusOne(), factory.zero()).amount());
        for (Num invalid : new Num[] { null, NaN.NaN, factory.numOf(Double.NaN),
                factory.numOf(Double.POSITIVE_INFINITY), factory.numOf(Double.NEGATIVE_INFINITY) }) {
            assertThrows(IllegalArgumentException.class, () -> new OrderBookLevel(invalid, factory.one()));
            assertThrows(IllegalArgumentException.class, () -> new OrderBookLevel(factory.one(), invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> new OrderBookLevel(factory.zero(), factory.minusOne()));
    }

    @Test
    void acceptsFiniteDecimalsBeyondDoubleRangeWithoutPrecisionLoss() {
        NumFactory factory = DecimalNumFactory.getInstance();
        Num huge = factory.numOf("1E+10000");
        OrderBookLevel level = new OrderBookLevel(huge.negate(), huge);
        assertEquals(huge.negate(), level.price());
        assertEquals(huge, level.amount());
        assertTrue(Double.isInfinite(huge.doubleValue()));
    }
}
