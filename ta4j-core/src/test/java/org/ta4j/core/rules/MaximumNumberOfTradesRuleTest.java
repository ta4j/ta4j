/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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
package org.ta4j.core.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

public class MaximumNumberOfTradesRuleTest {

    @Test
    public void isSatisfied() {
        var series = new MockBarSeriesBuilder().build();
        final var tradingRecord = new BaseTradingRecord(Trade.TradeType.BUY);
        final var tradedAmount = series.numFactory().numOf(1);

        // tradingRecord may contain a maximum of zero trades
        var rule = new MaximumNumberOfTradesRule(TradeType.BUY, 0);

        // tradingRecord may contain a maximum of one trade
        rule = new MaximumNumberOfTradesRule(TradeType.BUY, 1);

        // tradingRecord has no trades
        assertTrue(rule.isSatisfied(0, null));
        assertTrue(rule.isSatisfied(1, tradingRecord));

        // tradingRecord has one trade
        tradingRecord.enter(2, series.numFactory().numOf(102), tradedAmount);
        assertTrue(rule.isSatisfied(1, tradingRecord));
        assertTrue(rule.isSatisfied(2, tradingRecord));
        assertTrue(rule.isSatisfied(3, tradingRecord));
        tradingRecord.exit(5);

        // tradingRecord may contain a maximum of two trade
        rule = new MaximumNumberOfTradesRule(TradeType.BUY, 2);

        // tradingRecord has two trades
        tradingRecord.enter(6, series.numFactory().numOf(105), tradedAmount);
        assertTrue(rule.isSatisfied(1, tradingRecord));
        assertTrue(rule.isSatisfied(5, tradingRecord));
        assertTrue(rule.isSatisfied(6, tradingRecord));
        assertTrue(rule.isSatisfied(7, tradingRecord));
        tradingRecord.exit(10);

        // tradingRecord may contain a maximum of zero trades
        rule = new MaximumNumberOfTradesRule(TradeType.BUY, 0);
        assertTrue(rule.isSatisfied(1, tradingRecord));
        assertFalse(rule.isSatisfied(9, tradingRecord));

    }

}
