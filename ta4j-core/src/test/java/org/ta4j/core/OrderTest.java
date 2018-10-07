package org.ta4j.core;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Order.OrderType;

import static org.junit.Assert.*;
import static org.ta4j.core.num.NaN.NaN;

public class OrderTest {

    Order opEquals1, opEquals2, opNotEquals1, opNotEquals2;

    @Before
    public void setUp() {
        opEquals1 = Order.buyAt(1, NaN, NaN);
        opEquals2 = Order.buyAt(1, NaN, NaN);

        opNotEquals1 = Order.sellAt(1, NaN, NaN);
        opNotEquals2 = Order.buyAt(2, NaN, NaN);
    }

    @Test
    public void type() {
        assertEquals(OrderType.SELL, opNotEquals1.getType());
        assertFalse(opNotEquals1.isBuy());
        assertTrue(opNotEquals1.isSell());
        assertEquals(OrderType.BUY, opNotEquals2.getType());
        assertTrue(opNotEquals2.isBuy());
        assertFalse(opNotEquals2.isSell());
    }

    @Test
    public void overrideToString() {
        assertEquals(opEquals1.toString(), opEquals2.toString());

        assertNotEquals(opEquals1.toString(), opNotEquals1.toString());
        assertNotEquals(opEquals1.toString(), opNotEquals2.toString());
    }
}
