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
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.TADecimal;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.mocks.MockDecimalIndicator;
import static org.junit.Assert.*;
import org.junit.Test;

public class IndicatorCrossedIndicatorStrategyTest {

    @Test
    public void crossedIndicatorShouldBuyIndex2SellIndex4() {
        Indicator<TADecimal> first = new MockDecimalIndicator(4d, 7d, 9d, 6d, 3d, 2d);
        Indicator<TADecimal> second = new MockDecimalIndicator(3d, 6d, 10d, 8d, 2d, 1d);

        Strategy s = new IndicatorCrossedIndicatorStrategy(first, second);
        Trade trade = new Trade();
        assertFalse(s.shouldOperate(trade, 0));
        assertFalse(s.shouldOperate(trade, 1));

        assertTrue(s.shouldOperate(trade, 2));
        trade.operate(2);
        Operation enter = new Operation(2, OperationType.BUY);
        assertEquals(enter, trade.getEntry());

        assertFalse(s.shouldOperate(trade, 3));

        assertTrue(s.shouldOperate(trade, 4));
        trade.operate(4);
        Operation exit = new Operation(4, OperationType.SELL);
        assertEquals(exit, trade.getExit());

        assertFalse(s.shouldOperate(trade, 5));
    }

    @Test
    public void crossedIndicatorShouldNotEnterWhenIndicatorsAreEquals() {
        Indicator<TADecimal> first = new MockDecimalIndicator(2d, 3d, 4d, 5d, 6d, 7d);
        Trade trade = new Trade();

        Strategy s = new IndicatorCrossedIndicatorStrategy(first, first);

        for (int i = 0; i < 6; i++) {
            assertFalse(s.shouldOperate(trade, i));
        }
    }

    @Test
    public void crossedIndicatorShouldNotExitWhenIndicatorsBecameEquals() {
        Indicator<TADecimal> first = new MockDecimalIndicator(4d, 7d, 9d, 6d, 3d, 2d);
        Indicator<TADecimal> second = new MockDecimalIndicator(3d, 6d, 10d, 6d, 3d, 2d);
        Trade trade = new Trade();

        Strategy s = new IndicatorCrossedIndicatorStrategy(first, second);

        Operation enter = new Operation(2, OperationType.BUY);
        assertTrue(s.shouldOperate(trade, 2));
        trade.operate(2);
        assertEquals(enter, trade.getEntry());

        for (int i = 3; i < 6; i++) {
            assertFalse(s.shouldOperate(trade, i));
        }
    }

    @Test
    public void equalIndicatorsShouldNotExitWhenIndicatorsBecameEquals() {
        Indicator<TADecimal> firstEqual = new MockDecimalIndicator(2d, 1d, 4d, 5d, 6d, 7d);
        Indicator<TADecimal> secondEqual = new MockDecimalIndicator(1d, 3d, 4d, 5d, 6d, 7d);
        Strategy s = new IndicatorCrossedIndicatorStrategy(firstEqual, secondEqual);
        Trade trade = new Trade();

        assertTrue(s.shouldOperate(trade, 1));
        trade.operate(1);
        Operation enter = trade.getEntry();

        assertNotNull(enter);

        assertTrue(enter.isBuy());

        for (int i = 2; i < 6; i++) {
            assertFalse(s.shouldOperate(trade, i));
        }
    }

    @Test
    public void shouldNotSellWhileIndicatorAreEquals() {
        Indicator<TADecimal> firstEqual = new MockDecimalIndicator(2d, 1d, 4d, 5d, 6d, 7d, 10d);
        Indicator<TADecimal> secondEqual = new MockDecimalIndicator(1d, 3d, 4d, 5d, 6d, 7d, 9d);
        Strategy s = new IndicatorCrossedIndicatorStrategy(firstEqual, secondEqual);
        Trade trade = new Trade();

        Operation enter = new Operation(1, OperationType.BUY);

        assertTrue(s.shouldOperate(trade, 1));
        trade.operate(1);
        assertEquals(enter, trade.getEntry());

        for (int i = 2; i < 6; i++) {
            assertFalse(s.shouldOperate(trade, i));
        }

        Operation exit = new Operation(6, OperationType.SELL);
        assertTrue(s.shouldOperate(trade, 6));
        trade.operate(6);
        assertEquals(exit, trade.getExit());
    }

    @Test
    public void crossShouldNotReturnNullOperations() {
        Indicator<TADecimal> firstEqual = new MockDecimalIndicator(2d, 3d, 4d, 5d, 6d, 7d, 10d);
        Indicator<TADecimal> secondEqual = new MockDecimalIndicator(1d, 3d, 4d, 5d, 6d, 7d, 9d);
        Strategy s = new IndicatorCrossedIndicatorStrategy(firstEqual, secondEqual);
        Trade trade = new Trade();

        for (int i = 0; i < 7; i++) {
            assertFalse(s.shouldOperate(trade, i));
        }
    }
}
