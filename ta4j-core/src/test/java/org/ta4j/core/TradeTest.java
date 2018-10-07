package org.ta4j.core;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Order.OrderType;

import static org.junit.Assert.*;
import static org.ta4j.core.num.NaN.NaN;

public class TradeTest {

    private Trade newTrade, uncoveredTrade, trEquals1, trEquals2, trNotEquals1, trNotEquals2;

    @Before
    public void setUp() {
        this.newTrade = new Trade();
        this.uncoveredTrade = new Trade(OrderType.SELL);

        trEquals1 = new Trade();
        trEquals1.operate(1);
        trEquals1.operate(2);

        trEquals2 = new Trade();
        trEquals2.operate(1);
        trEquals2.operate(2);

        trNotEquals1 = new Trade(OrderType.SELL);
        trNotEquals1.operate(1);
        trNotEquals1.operate(2);

        trNotEquals2 = new Trade(OrderType.SELL);
        trNotEquals2.operate(1);
        trNotEquals2.operate(2);
    }

    @Test
    public void whenNewShouldCreateBuyOrderWhenEntering() {
        newTrade.operate(0);
        assertEquals(Order.buyAt(0, NaN, NaN), newTrade.getEntry());
    }

    @Test
    public void whenNewShouldNotExit() {
        assertFalse(newTrade.isOpened());
    }

    @Test
    public void whenOpenedShouldCreateSellOrderWhenExiting() {
        newTrade.operate(0);
        newTrade.operate(1);
        assertEquals(Order.sellAt(1, NaN, NaN), newTrade.getExit());
    }

    @Test
    public void whenClosedShouldNotEnter() {
        newTrade.operate(0);
        newTrade.operate(1);
        assertTrue(newTrade.isClosed());
        newTrade.operate(2);
        assertTrue(newTrade.isClosed());
    }

    @Test(expected = IllegalStateException.class)
    public void whenExitIndexIsLessThanEntryIndexShouldThrowException() {
        newTrade.operate(3);
        newTrade.operate(1);
    }

    @Test
    public void shouldCloseTradeOnSameIndex() {
        newTrade.operate(3);
        newTrade.operate(3);
        assertTrue(newTrade.isClosed());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowIllegalArgumentExceptionWhenOrderTypeIsNull() {
        new Trade(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowIllegalArgumentExceptionWhenOrdersHaveSameType() {
        new Trade(Order.buyAt(0,NaN,NaN), Order.buyAt(1,NaN,NaN));
    }

    @Test
    public void whenNewShouldCreateSellOrderWhenEnteringUncovered() {
        uncoveredTrade.operate(0);
        assertEquals(Order.sellAt(0,NaN,NaN), uncoveredTrade.getEntry());
    }

    @Test
    public void whenOpenedShouldCreateBuyOrderWhenExitingUncovered() {
        uncoveredTrade.operate(0);
        uncoveredTrade.operate(1);
        assertEquals(Order.buyAt(1,NaN,NaN), uncoveredTrade.getExit());
    }

    @Test
    public void overrideToString() {
        assertEquals(trEquals1.toString(), trEquals2.toString());
        assertNotEquals(trEquals1.toString(), trNotEquals1.toString());
        assertNotEquals(trEquals1.toString(), trNotEquals2.toString());
    }

    @Test
    public void testEqualsForNewTrades() {
    	assertEquals(newTrade, new Trade());
    	assertNotEquals(newTrade, new Object());
    	assertNotEquals(newTrade, null);
    }

    @Test
    public void testEqualsForEntryOrders() {
    	Trade trLeft = newTrade;
    	Trade trRightEquals = new Trade();
    	Trade trRightNotEquals = new Trade();

    	assertEquals(OrderType.BUY, trRightNotEquals.operate(2).getType());
    	assertNotEquals(trLeft, trRightNotEquals);

    	assertEquals(OrderType.BUY, trLeft.operate(1).getType());
    	assertEquals(OrderType.BUY, trRightEquals.operate(1).getType());
    	assertEquals(trLeft, trRightEquals);

    	assertNotEquals(trLeft, trRightNotEquals);
    }

    @Test
    public void testEqualsForExitOrders() {
    	Trade trLeft = newTrade;
    	Trade trRightEquals = new Trade();
    	Trade trRightNotEquals = new Trade();

    	assertEquals(OrderType.BUY, trLeft.operate(1).getType());
    	assertEquals(OrderType.BUY, trRightEquals.operate(1).getType());
    	assertEquals(OrderType.BUY, trRightNotEquals.operate(1).getType());
    	
    	assertEquals(OrderType.SELL, trRightNotEquals.operate(3).getType());
    	assertNotEquals(trLeft, trRightNotEquals);

    	assertEquals(OrderType.SELL, trLeft.operate(2).getType());
    	assertEquals(OrderType.SELL, trRightEquals.operate(2).getType());
    	assertEquals(trLeft, trRightEquals);

    	assertNotEquals(trLeft, trRightNotEquals);
    }
}
