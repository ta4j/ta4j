/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan & respective authors
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

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class TradeTest {

    private Trade trade, uncoveredTrade, trEquals1, trEquals2, trNotEquals1, trNotEquals2;

    @Before
    public void setUp() {
        this.trade = new Trade();
        this.uncoveredTrade = new Trade(OperationType.SELL);

        trEquals1 = new Trade();
        trEquals1.operate(1);
        trEquals1.operate(2);

        trEquals2 = new Trade();
        trEquals2.operate(1);
        trEquals2.operate(2);

        trNotEquals1 = new Trade(OperationType.SELL);
        trNotEquals1.operate(1);
        trNotEquals1.operate(2);

        trNotEquals2 = new Trade(OperationType.SELL);
        trNotEquals2.operate(1);
        trNotEquals2.operate(2);
    }

    @Test
    public void whenNewShouldCreateBuyOperationWhenEntering() {
        trade.operate(0);
        assertEquals(new Operation(0, OperationType.BUY), trade.getEntry());
    }

    @Test
    public void whenNewShouldNotExit() {
        Trade trade = new Trade();
        assertFalse(trade.isOpened());
    }

    @Test
    public void whenOpenedShouldCreateSellOperationWhenExiting() {
        Trade trade = new Trade();
        trade.operate(0);
        trade.operate(1);

        assertEquals(new Operation(1, OperationType.SELL), trade.getExit());
    }

    @Test
    public void whenClosedShouldNotEnter() {
        Trade trade = new Trade();
        trade.operate(0);
        trade.operate(1);
        assertTrue(trade.isClosed());
        trade.operate(2);
        assertTrue(trade.isClosed());
    }

    @Test(expected = IllegalStateException.class)
    public void whenExitIndexIsLessThanEntryIndexShouldThrowException() {
        Trade trade = new Trade();
        trade.operate(3);
        trade.operate(1);
    }

    @Test
    public void shouldCloseTradeOnSameIndex() {
        Trade trade = new Trade();
        trade.operate(3);
        trade.operate(3);
        assertTrue(trade.isClosed());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowIllegalArgumentExceptionWhenOperationTypeIsNull() {
        Trade t = new Trade(null);
    }

    @Test
    public void whenNewShouldCreateSellOperationWhenEnteringUncovered() {
        uncoveredTrade.operate(0);

        assertEquals(new Operation(0, OperationType.SELL), uncoveredTrade.getEntry());
    }

    @Test
    public void whenOpenedShouldCreateBuyOperationWhenExitingUncovered() {
        uncoveredTrade.operate(0);
        uncoveredTrade.operate(1);

        assertEquals(new Operation(1, OperationType.BUY), uncoveredTrade.getExit());
    }

    @Test
    public void overrideToString() {
        assertEquals(trEquals1.toString(), trEquals2.toString());

        assertNotEquals(trEquals1.toString(), trNotEquals1.toString());
        assertNotEquals(trEquals1.toString(), trNotEquals2.toString());
    }
}
