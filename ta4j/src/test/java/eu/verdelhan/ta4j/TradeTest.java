/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
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
package eu.verdelhan.ta4j;

import eu.verdelhan.ta4j.Order.OrderType;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

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
        assertEquals(Order.buyAt(0), newTrade.getEntry());
    }

    @Test
    public void whenNewShouldNotExit() {
        assertFalse(newTrade.isOpened());
    }

    @Test
    public void whenOpenedShouldCreateSellOrderWhenExiting() {
        newTrade.operate(0);
        newTrade.operate(1);
        assertEquals(Order.sellAt(1), newTrade.getExit());
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
        Trade t = new Trade(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowIllegalArgumentExceptionWhenOrdersHaveSameType() {
        Trade t = new Trade(Order.buyAt(0), Order.buyAt(1));
    }

    @Test
    public void whenNewShouldCreateSellOrderWhenEnteringUncovered() {
        uncoveredTrade.operate(0);
        assertEquals(Order.sellAt(0), uncoveredTrade.getEntry());
    }

    @Test
    public void whenOpenedShouldCreateBuyOrderWhenExitingUncovered() {
        uncoveredTrade.operate(0);
        uncoveredTrade.operate(1);
        assertEquals(Order.buyAt(1), uncoveredTrade.getExit());
    }

    @Test
    public void overrideToString() {
        assertEquals(trEquals1.toString(), trEquals2.toString());
        assertNotEquals(trEquals1.toString(), trNotEquals1.toString());
        assertNotEquals(trEquals1.toString(), trNotEquals2.toString());
    }
}
