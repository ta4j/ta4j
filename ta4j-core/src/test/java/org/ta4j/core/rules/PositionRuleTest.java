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

import java.math.BigDecimal;

import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.rules.PositionRule.PositionAggregationType;
import org.ta4j.core.rules.PositionRule.PositionFilter;

public class PositionRuleTest {

    @Test
    public void testEmptyTradingRecord() {

        var tradingRecord = new BaseTradingRecord(Trade.TradeType.BUY);
        var rule = new PositionRule(PositionFilter.ALL, PositionAggregationType.NUMBER_OF_POSITIONS, BigDecimal.ZERO,
                BigDecimal.ZERO);

        // tradingRecord has no trades
        assertTrue(rule.isSatisfied(0, null));
        assertTrue(rule.isSatisfied(1, tradingRecord));

        rule = new PositionRule(PositionFilter.ALL, PositionAggregationType.NUMBER_OF_POSITIONS, BigDecimal.ONE,
                BigDecimal.TEN);

        // tradingRecord has no trades, but a minimum of 1 is required
        assertFalse(rule.isSatisfied(0, null));
        assertFalse(rule.isSatisfied(1, tradingRecord));

    }

    @Test
    public void testPositionFilter() {
        var series = new MockBarSeriesBuilder().build();
        var tradingRecord = new BaseTradingRecord(Trade.TradeType.BUY);
        var numFactory = series.numFactory();
        var one = numFactory.one();
        var tradedAmount = one;

        // tradingRecord has one closed and one opened position
        tradingRecord.enter(2, one, tradedAmount);
        tradingRecord.exit(3, one, tradedAmount);
        tradingRecord.enter(4, one, tradedAmount);

        var rule = new PositionRule(PositionFilter.ALL, PositionAggregationType.NUMBER_OF_POSITIONS, BigDecimal.ONE,
                BigDecimal.TWO);

        // test PositionFilter.ALL
        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertTrue(rule.isSatisfied(2, tradingRecord));
        assertTrue(rule.isSatisfied(4, tradingRecord));

        tradingRecord.exit(5, one, tradedAmount);
        tradingRecord.enter(6, one, tradedAmount);
        rule = new PositionRule(PositionFilter.CLOSED, PositionAggregationType.NUMBER_OF_POSITIONS, BigDecimal.ZERO,
                BigDecimal.ONE);

        // test PositionFilter.CLOSED
        assertTrue(rule.isSatisfied(1, tradingRecord));
        assertTrue(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(6, tradingRecord));

        tradingRecord.exit(7, one, tradedAmount);
        tradingRecord.enter(8, one, tradedAmount);
        rule = new PositionRule(PositionFilter.OPEN, PositionAggregationType.NUMBER_OF_POSITIONS, BigDecimal.ONE,
                BigDecimal.ONE);

        // test PositionFilter.OPEN
        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(6, tradingRecord));
        assertTrue(rule.isSatisfied(8, tradingRecord));

    }

    @Test
    public void testPositionAggregationType() {
        var series = new MockBarSeriesBuilder().build();
        var tradingRecord = new BaseTradingRecord(Trade.TradeType.BUY);
        var numFactory = series.numFactory();
        var one = numFactory.one();
        var two = numFactory.two();
        var hundred = numFactory.hundred();

        // tradingRecord with 3 closed and 1 open position
        tradingRecord.enter(2, one, one);
        tradingRecord.exit(3, one, one);
        tradingRecord.enter(4, one, one);
        tradingRecord.exit(5, two, one);
        tradingRecord.enter(6, one, two);
        tradingRecord.exit(7, two, two);
        tradingRecord.enter(8, one, hundred);

        // test NUMBER_OF_POSITIONS
        var rule = new PositionRule(PositionFilter.ALL, PositionAggregationType.NUMBER_OF_POSITIONS, BigDecimal.ONE,
                BigDecimal.TWO);
        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertTrue(rule.isSatisfied(2, tradingRecord));
        assertTrue(rule.isSatisfied(4, tradingRecord));
        assertFalse(rule.isSatisfied(9, tradingRecord));

        // test NUMBER_OF_ENTRY_TRADES
        rule = new PositionRule(PositionFilter.ALL, PositionAggregationType.NUMBER_OF_ENTRY_TRADES, null,
                BigDecimal.ZERO);
        assertTrue(rule.isSatisfied(1, tradingRecord));
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(4, tradingRecord));
        assertFalse(rule.isSatisfied(9, tradingRecord));

        // test NUMBER_OF_EXIT_TRADES
        rule = new PositionRule(PositionFilter.ALL, PositionAggregationType.NUMBER_OF_EXIT_TRADES, null,
                BigDecimal.TWO);
        assertTrue(rule.isSatisfied(1, tradingRecord));
        assertTrue(rule.isSatisfied(2, tradingRecord));
        assertTrue(rule.isSatisfied(4, tradingRecord));
        assertFalse(rule.isSatisfied(9, tradingRecord));

        // test AMOUNT
        rule = new PositionRule(PositionFilter.ALL, PositionAggregationType.AMOUNT, BigDecimal.ONE, BigDecimal.TEN);
        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertTrue(rule.isSatisfied(4, tradingRecord));
        assertFalse(rule.isSatisfied(8, tradingRecord));

        // test VALUE
        rule = new PositionRule(PositionFilter.ALL, PositionAggregationType.VALUE, BigDecimal.ONE, BigDecimal.TEN);
        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertTrue(rule.isSatisfied(4, tradingRecord));
        assertFalse(rule.isSatisfied(8, tradingRecord));

        // test NET_PROFIT
        rule = new PositionRule(PositionFilter.ALL, PositionAggregationType.NET_PROFIT, BigDecimal.ONE, null);
        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(4, tradingRecord));
        assertTrue(rule.isSatisfied(8, tradingRecord));

        // test GROSS_PROFIT
        rule = new PositionRule(PositionFilter.ALL, PositionAggregationType.GROSS_PROFIT, BigDecimal.ONE, null);
        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(4, tradingRecord));
        assertTrue(rule.isSatisfied(8, tradingRecord));

    }

}
