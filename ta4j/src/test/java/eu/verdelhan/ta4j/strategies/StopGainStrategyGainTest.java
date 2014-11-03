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
import eu.verdelhan.ta4j.mocks.MockDecimalIndicator;
import eu.verdelhan.ta4j.mocks.MockStrategy;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class StopGainStrategyGainTest {

    private MockDecimalIndicator indicator;

    @Before
    public void setUp() {
        indicator = new MockDecimalIndicator(100d, 98d, 103d, 115d, 107d);
    }

    @Test
    public void stopperShouldSell() {

        Strategy justBuy = new JustBuyOnceStrategy();
        Strategy stopper = new StopGainStrategy(indicator, justBuy, 4);

        Operation buy = new Operation(0, OperationType.BUY);
        Operation sell = new Operation(3, OperationType.SELL);

        Trade trade = new Trade();
        assertTrue(stopper.shouldOperate(trade, 0));
        trade.operate(0);
        assertEquals(buy, trade.getEntry());
        assertFalse(stopper.shouldOperate(trade, 1));
        assertFalse(stopper.shouldOperate(trade, 2));

        assertTrue(stopper.shouldOperate(trade, 3));
        trade.operate(3);
        assertEquals(sell, trade.getExit());
    }

    @Test
    public void stopperShouldSellIfStrategySays() {

        Operation[] enter = new Operation[] { new Operation(0, OperationType.BUY), null, new Operation(2, OperationType.BUY), null, null };
        Operation[] exit = new Operation[] { null, new Operation(1, OperationType.SELL), null, null, new Operation(4, OperationType.SELL) };

        Strategy sell1 = new MockStrategy(enter, exit);

        Strategy stopper = new StopGainStrategy(indicator, sell1, 5);

        Operation buy = new Operation(0, OperationType.BUY);
        Operation sell = new Operation(1, OperationType.SELL);

        Trade trade = new Trade();
        assertTrue(stopper.shouldOperate(trade, 0));
        trade.operate(0);

        assertEquals(buy, trade.getEntry());

        assertTrue(stopper.shouldOperate(trade, 1));
        trade.operate(1);

        assertEquals(sell, trade.getExit());
        
        trade = new Trade();
        buy = new Operation(2, OperationType.BUY);
        sell = new Operation(3, OperationType.SELL);

        assertTrue(stopper.shouldOperate(trade, 2));
        trade.operate(2);

        assertEquals(buy, trade.getEntry());

        assertTrue(stopper.shouldOperate(trade, 3));
        trade.operate(3);

        assertEquals(sell, trade.getExit());
    }

}
