/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2015 Marc de Verdelhan & respective authors
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
import eu.verdelhan.ta4j.Operation.OperationType;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.mocks.MockDecimalIndicator;
import eu.verdelhan.ta4j.mocks.MockStrategy;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class StopLossStrategyTest {

    private MockDecimalIndicator indicator;

    @Before
    public void setUp() {
        indicator = new MockDecimalIndicator(100d, 100d, 96d, 95d, 94d);
    }

    @Test
    public void stopperShouldSell() {

        Strategy justBuy = new JustEnterOnceStrategy();
        Strategy stopper = new StopLossStrategy(indicator, justBuy, 5);

        Operation buy = Operation.buyAt(0);
        Operation sell = Operation.sellAt(4);

        Trade trade = new Trade();
        assertTrue(stopper.shouldOperate(trade, 0));
        trade.operate(0);
        assertEquals(buy, trade.getEntry());
        assertFalse(stopper.shouldOperate(trade, 1));
        assertFalse(stopper.shouldOperate(trade, 2));

        assertTrue(stopper.shouldOperate(trade, 4));
        trade.operate(4);
        assertEquals(sell, trade.getExit());
    }

    @Test
    public void stopperShouldSellIfStrategySays() {

        Operation[] enter = new Operation[] { Operation.buyAt(0), null, null, null, null };
        Operation[] exit = new Operation[] { null, Operation.sellAt(1), null, null, null };

        Strategy sell1 = new MockStrategy(enter, exit);

        Strategy stopper = new StopLossStrategy(indicator, sell1, 500);

        Operation buy = Operation.buyAt(0);
        Operation sell = Operation.sellAt(1);

        Trade trade = new Trade();
        assertTrue(stopper.shouldOperate(trade, 0));
        trade.operate(0);

        assertEquals(buy, trade.getEntry());

        assertTrue(stopper.shouldOperate(trade, 1));
        trade.operate(1);

        assertEquals(sell, trade.getExit());
    }

}
