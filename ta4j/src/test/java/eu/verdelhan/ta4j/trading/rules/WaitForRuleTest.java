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
package eu.verdelhan.ta4j.trading.rules;

import eu.verdelhan.ta4j.BaseTradingRecord;
import eu.verdelhan.ta4j.Order;
import eu.verdelhan.ta4j.TradingRecord;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class WaitForRuleTest {

    private TradingRecord tradingRecord;
    private WaitForRule rule;
    
    @Before
    public void setUp() {
        tradingRecord = new BaseTradingRecord();
    }
    
    @Test
    public void waitForSinceLastBuyRuleIsSatisfied() {
        // Waits for 3 ticks since last buy order
        rule = new WaitForRule(Order.OrderType.BUY, 3);
        
        assertFalse(rule.isSatisfied(0, null));
        assertFalse(rule.isSatisfied(1, tradingRecord));
        
        tradingRecord.enter(10);
        assertFalse(rule.isSatisfied(10, tradingRecord));
        assertFalse(rule.isSatisfied(11, tradingRecord));
        assertFalse(rule.isSatisfied(12, tradingRecord));
        assertTrue(rule.isSatisfied(13, tradingRecord));
        assertTrue(rule.isSatisfied(14, tradingRecord));
        
        tradingRecord.exit(15);
        assertTrue(rule.isSatisfied(15, tradingRecord));
        assertTrue(rule.isSatisfied(16, tradingRecord));
        
        tradingRecord.enter(17);
        assertFalse(rule.isSatisfied(17, tradingRecord));
        assertFalse(rule.isSatisfied(18, tradingRecord));
        assertFalse(rule.isSatisfied(19, tradingRecord));
        assertTrue(rule.isSatisfied(20, tradingRecord));
    }
    
    @Test
    public void waitForSinceLastSellRuleIsSatisfied() {
        // Waits for 2 ticks since last sell order
        rule = new WaitForRule(Order.OrderType.SELL, 2);
        
        assertFalse(rule.isSatisfied(0, null));
        assertFalse(rule.isSatisfied(1, tradingRecord));
        
        tradingRecord.enter(10);
        assertFalse(rule.isSatisfied(10, tradingRecord));
        assertFalse(rule.isSatisfied(11, tradingRecord));
        assertFalse(rule.isSatisfied(12, tradingRecord));
        assertFalse(rule.isSatisfied(13, tradingRecord));
        
        tradingRecord.exit(15);
        assertFalse(rule.isSatisfied(15, tradingRecord));
        assertFalse(rule.isSatisfied(16, tradingRecord));
        assertTrue(rule.isSatisfied(17, tradingRecord));
        
        tradingRecord.enter(17);
        assertTrue(rule.isSatisfied(17, tradingRecord));
        assertTrue(rule.isSatisfied(18, tradingRecord));
        
        tradingRecord.exit(20);
        assertFalse(rule.isSatisfied(20, tradingRecord));
        assertFalse(rule.isSatisfied(21, tradingRecord));
        assertTrue(rule.isSatisfied(22, tradingRecord));
    }
}
        