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
package eu.verdelhan.ta4j.strategies;

import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.Trade;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class JustBuyOnceStrategyTest {

    private Strategy strategy;

    private Trade trade;

    @Before
    public void setUp() {
        this.strategy = new JustBuyOnceStrategy();
        this.trade = new Trade();
    }

    @Test
    public void shouldBuyTradeOnce() {
        Operation buy = new Operation(0, OperationType.BUY);

        assertTrue(strategy.shouldOperate(trade, 0));
        trade.operate(0);
        assertEquals(buy, trade.getEntry());
        assertFalse(strategy.shouldOperate(trade, 1));
        assertFalse(strategy.shouldOperate(trade, 6));

    }

    @Test
    public void sameIndexShouldResultSameAnswer() {
        Operation buy = new Operation(0, OperationType.BUY);

        assertTrue(strategy.shouldOperate(trade, 0));
        trade.operate(0);
        assertEquals(buy, trade.getEntry());
        Trade trade2 = new Trade();
        assertFalse(strategy.shouldOperate(trade2, 0));
        trade2.operate(0);
        assertEquals(buy, trade2.getEntry());
    }

}
