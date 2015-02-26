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

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.Operation.OperationType;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.mocks.MockDecimalIndicator;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class IndicatorOverIndicatorStrategyTest {

    private Indicator<Decimal> first;

    private Indicator<Decimal> second;

    @Before
    public void setUp() {

        first = new MockDecimalIndicator(3d, 6d, 10d, 8d, 2d, 1d);
        second = new MockDecimalIndicator(4d, 7d, 9d, 6d, 3d, 2d);
    }

    @Test
    public void overIndicators() {
        Trade trade = new Trade();

        Strategy s = new IndicatorOverIndicatorStrategy(first, second);
        assertFalse(s.shouldOperate(trade, 0));
        assertFalse(s.shouldOperate(trade, 1));
        assertEquals(null, trade.getEntry());
        Operation buy = Operation.buyAt(2);
        assertTrue(s.shouldOperate(trade, 2));
        trade.operate(2);
        assertEquals(buy, trade.getEntry());
        trade = new Trade();
        buy = Operation.buyAt(3);
        assertTrue(s.shouldOperate(trade, 3));
        trade.operate(3);
        assertEquals(buy, trade.getEntry());

        assertFalse(s.shouldOperate(trade, 3));

        Operation sell = Operation.sellAt(4);
        assertTrue(s.shouldOperate(trade, 4));
        trade.operate(4);
        assertEquals(sell, trade.getExit());

    }
}
