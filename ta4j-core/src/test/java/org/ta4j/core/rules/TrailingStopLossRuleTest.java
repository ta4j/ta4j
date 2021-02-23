/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
 * authors (see AUTHORS)
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
/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.rules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.function.Function;

import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

public class TrailingStopLossRuleTest extends AbstractIndicatorTest<Object, Object> {

    public TrailingStopLossRuleTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Test
    public void isSatisfiedForBuy() {
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(
                new MockBarSeries(numFunction, 100, 110, 120, 130, 117.00, 130, 116.99));

        // 10% trailing-stop-loss
        TrailingStopLossRule rule = new TrailingStopLossRule(closePrice, numOf(10));

        assertFalse(rule.isSatisfied(0, null));
        assertNull(rule.getCurrentStopLossLimitActivation());

        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertNull(rule.getCurrentStopLossLimitActivation());

        // Enter at 114
        tradingRecord.enter(2, numOf(114), numOf(1));
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertEquals(numOf(120).multipliedBy(numOf(0.9)), rule.getCurrentStopLossLimitActivation());

        assertFalse(rule.isSatisfied(3, tradingRecord));
        assertEquals(numOf(130).multipliedBy(numOf(0.9)), rule.getCurrentStopLossLimitActivation());

        assertTrue(rule.isSatisfied(4, tradingRecord));
        assertEquals(numOf(130).multipliedBy(numOf(0.9)), rule.getCurrentStopLossLimitActivation());
        // Exit
        tradingRecord.exit(5);

        // Enter at 128
        tradingRecord.enter(5, numOf(128), numOf(1));
        assertFalse(rule.isSatisfied(5, tradingRecord));
        assertEquals(numOf(130).multipliedBy(numOf(0.9)), rule.getCurrentStopLossLimitActivation());
        assertTrue(rule.isSatisfied(6, tradingRecord));
        assertEquals(numOf(130).multipliedBy(numOf(0.9)), rule.getCurrentStopLossLimitActivation());
    }

    @Test
    public void isSatisfiedForBuyForBarCount() {
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(
                new MockBarSeries(numFunction, 100, 110, 120, 130, 120, 117.00, 117.00, 130, 116.99));

        // 10% trailing-stop-loss
        TrailingStopLossRule rule = new TrailingStopLossRule(closePrice, numOf(10), 3);

        assertFalse(rule.isSatisfied(0, null));
        assertFalse(rule.isSatisfied(1, tradingRecord));

        // Enter at 114
        tradingRecord.enter(2, numOf(114), numOf(1));
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(3, tradingRecord));
        assertFalse(rule.isSatisfied(4, tradingRecord));
        assertTrue(rule.isSatisfied(5, tradingRecord));
        assertFalse(rule.isSatisfied(6, tradingRecord));
        // Exit
        tradingRecord.exit(7);

        // Enter at 128
        tradingRecord.enter(7, numOf(128), numOf(1));
        assertFalse(rule.isSatisfied(7, tradingRecord));
        assertTrue(rule.isSatisfied(8, tradingRecord));
    }

    @Test
    public void isSatisfiedForSell() {
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.SELL);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(
                new MockBarSeries(numFunction, 100, 90, 80, 70, 77.00, 120, 132.01));

        // 10% trailing-stop-loss
        TrailingStopLossRule rule = new TrailingStopLossRule(closePrice, numOf(10));

        assertFalse(rule.isSatisfied(0, null));
        assertNull(rule.getCurrentStopLossLimitActivation());

        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertNull(rule.getCurrentStopLossLimitActivation());

        // Enter at 84
        tradingRecord.enter(2, numOf(84), numOf(1));
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertEquals(numOf(80).multipliedBy(numOf(1.1)), rule.getCurrentStopLossLimitActivation());

        assertFalse(rule.isSatisfied(3, tradingRecord));
        assertEquals(numOf(70).multipliedBy(numOf(1.1)), rule.getCurrentStopLossLimitActivation());

        assertTrue(rule.isSatisfied(4, tradingRecord));
        assertEquals(numOf(70).multipliedBy(numOf(1.1)), rule.getCurrentStopLossLimitActivation());
        // Exit
        tradingRecord.exit(5);

        // Enter at 128
        tradingRecord.enter(5, numOf(128), numOf(1));
        assertFalse(rule.isSatisfied(5, tradingRecord));
        assertEquals(numOf(120).multipliedBy(numOf(1.1)), rule.getCurrentStopLossLimitActivation());
        assertTrue(rule.isSatisfied(6, tradingRecord));
        assertEquals(numOf(120).multipliedBy(numOf(1.1)), rule.getCurrentStopLossLimitActivation());
    }

    @Test
    public void isSatisfiedForSellForBarCount() {
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.SELL);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(
                new MockBarSeries(numFunction, 100, 90, 80, 70, 70, 73, 77.00, 90, 120, 132.01));

        // 10% trailing-stop-loss and 2 bars back
        TrailingStopLossRule rule = new TrailingStopLossRule(closePrice, numOf(10), 3);

        assertFalse(rule.isSatisfied(0, null));
        assertFalse(rule.isSatisfied(1, tradingRecord));

        // Enter at 84
        tradingRecord.enter(2, numOf(84), numOf(1));
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(3, tradingRecord));
        assertFalse(rule.isSatisfied(4, tradingRecord));
        assertFalse(rule.isSatisfied(5, tradingRecord));
        assertTrue(rule.isSatisfied(6, tradingRecord));
        // Exit
        tradingRecord.exit(7);

        // Enter at 128
        tradingRecord.enter(7, numOf(91), numOf(1));
        assertFalse(rule.isSatisfied(7, tradingRecord));
        assertTrue(rule.isSatisfied(8, tradingRecord));
    }
}