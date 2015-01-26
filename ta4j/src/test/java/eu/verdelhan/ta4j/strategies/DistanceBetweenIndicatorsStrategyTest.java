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

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.mocks.MockDecimalIndicator;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class DistanceBetweenIndicatorsStrategyTest {
    private MockDecimalIndicator upper;

    private MockDecimalIndicator lower;

    private AbstractStrategy distanceEnter;

    @Before
    public void setUp() {
        upper = new MockDecimalIndicator(30d, 32d, 33d, 32d, 35d, 33d, 32d);
        lower = new MockDecimalIndicator(10d, 10d, 10d, 12d, 14d, 15d, 15d);
        distanceEnter = new DistanceBetweenIndicatorsStrategy(upper, lower, 20, 0.1);
    }

    @Test
    public void strategyIsBuyingCorrectly() {
        Trade trade = new Trade();

        assertFalse(distanceEnter.shouldOperate(trade, 0));
        assertTrue(distanceEnter.shouldOperate(trade, 1));
        trade = new Trade();
        assertTrue(distanceEnter.shouldOperate(trade, 2));
        assertFalse(distanceEnter.shouldOperate(trade, 3));
        assertFalse(distanceEnter.shouldOperate(trade, 4));
    }

    @Test
    public void strategyIsSellingCorrectly() {
        Trade trade = new Trade();
        trade.operate(2);

        assertFalse(distanceEnter.shouldOperate(trade, 0));
        assertTrue(distanceEnter.shouldOperate(trade, 5));

        trade = new Trade();
        trade.operate(2);

        assertTrue(distanceEnter.shouldOperate(trade, 6));
        assertFalse(distanceEnter.shouldOperate(trade, 3));
        assertFalse(distanceEnter.shouldOperate(trade, 4));
    }

    @Test
    public void distanceBetweenIndicatorAndConstant() {
        MockDecimalIndicator indicator = new MockDecimalIndicator(4d, 10d, 10d, 12d, 14d, 15d, 18d);
        distanceEnter = new DistanceBetweenIndicatorsStrategy(indicator, Decimal.valueOf(9), 4, 0.3);

        Trade trade = new Trade();
        assertFalse(distanceEnter.shouldOperate(trade, 4));
        assertTrue(distanceEnter.shouldOperate(trade, 5));

        trade.operate(2);
        assertTrue(distanceEnter.shouldOperate(trade, 2));
        assertFalse(distanceEnter.shouldOperate(trade, 3));
    }
}
