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

public class SupportStrategyTest {

    private MockDecimalIndicator indicator;

    @Before
    public void setUp() {
        indicator = new MockDecimalIndicator(96d, 90d, 94d, 97d, 95d, 110d);
    }

    @Test
    public void supportShouldBuy() {
        Operation[] enter = new Operation[] { null, null, null, null, null, null };

        Strategy neverBuy = new MockStrategy(enter, enter);

        Strategy support = new SupportStrategy(indicator, neverBuy, 95);

        Trade trade = new Trade();

        assertFalse(support.shouldOperate(trade, 0));
        assertTrue(support.shouldOperate(trade, 1));
        trade.operate(1);
        assertEquals(new Operation(1, OperationType.BUY), trade.getEntry());
        trade = new Trade();
        assertTrue(support.shouldOperate(trade, 2));
        trade.operate(2);
        assertEquals(new Operation(2, OperationType.BUY), trade.getEntry());
        trade = new Trade();
        assertFalse(support.shouldOperate(trade, 3));
        assertTrue(support.shouldOperate(trade, 4));
        trade.operate(4);
        assertEquals(new Operation(4, OperationType.BUY), trade.getEntry());
        assertFalse(support.shouldOperate(trade, 5));
    }
}
