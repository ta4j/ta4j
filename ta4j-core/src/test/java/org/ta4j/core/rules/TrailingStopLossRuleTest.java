/*
 * SPDX-License-Identifier: MIT
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TraceTestLogger;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

public class TrailingStopLossRuleTest extends AbstractIndicatorTest<Object, Object> {

    public TrailingStopLossRuleTest(NumFactory numFactory) {
        super(numFactory);
    }

    private TraceTestLogger ruleTraceTestLogger;

    @Before
    public void setUpLogger() {
        ruleTraceTestLogger = new TraceTestLogger();
        ruleTraceTestLogger.open();
    }

    @After
    public void tearDownLogger() {
        ruleTraceTestLogger.close();
    }

    @Test
    public void isSatisfiedForBuy() {
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 110, 120, 130, 117.00, 130, 116.99)
                .build());

        // 10% trailing-stop-loss
        TrailingStopLossRule rule = new TrailingStopLossRule(closePrice, numOf(10));
        assertFalse(rule.isSatisfied(0, null));
        assertFalse(rule.isSatisfied(1, tradingRecord));

        // Enter at 114
        tradingRecord.enter(2, numOf(114), numOf(1));
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(3, tradingRecord));
        assertTrue(rule.isSatisfied(4, tradingRecord));

        // Exit
        tradingRecord.exit(5);

        // Enter at 128
        tradingRecord.enter(5, numOf(128), numOf(1));
        assertFalse(rule.isSatisfied(5, tradingRecord));
        assertTrue(rule.isSatisfied(6, tradingRecord));
    }

    @Test
    public void traceLoggingUsesCustomNameForTrailingStopLossRule() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(
                new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 120, 130, 117.00).build());
        TrailingStopLossRule rule = new TrailingStopLossRule(closePrice, numOf(10));
        rule.setName("5min Trailing Stop");
        ruleTraceTestLogger.clear();

        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);
        tradingRecord.enter(2, numOf(114), numOf(1));
        rule.isSatisfied(4, tradingRecord);

        String logContent = ruleTraceTestLogger.getLogOutput();
        assertTrue("TrailingStopLossRule trace log should contain custom name when set",
                logContent.contains("5min Trailing Stop#isSatisfied"));
        assertFalse("TrailingStopLossRule trace log should not contain class name when custom name is set",
                logContent.contains("TrailingStopLossRule#isSatisfied"));
    }

    @Test
    public void traceLoggingUsesClassNameForTrailingStopLossRuleWhenNoCustomName() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(
                new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 120, 130, 117.00).build());
        TrailingStopLossRule rule = new TrailingStopLossRule(closePrice, numOf(10));
        ruleTraceTestLogger.clear();

        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);
        tradingRecord.enter(2, numOf(114), numOf(1));
        rule.isSatisfied(4, tradingRecord);

        String logContent = ruleTraceTestLogger.getLogOutput();
        assertTrue("TrailingStopLossRule trace log should contain class name when no custom name is set",
                logContent.contains("TrailingStopLossRule#isSatisfied"));
    }

    @Test
    public void traceLoggingIncludesAdditionalInfoForTrailingStopLossRule() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(
                new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 120, 130, 117.00).build());
        TrailingStopLossRule rule = new TrailingStopLossRule(closePrice, numOf(10));
        rule.setName("Custom Stop Loss");
        ruleTraceTestLogger.clear();

        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);
        tradingRecord.enter(2, numOf(114), numOf(1));
        rule.isSatisfied(4, tradingRecord);

        String logContent = ruleTraceTestLogger.getLogOutput();
        assertTrue("TrailingStopLossRule trace log should include custom name",
                logContent.contains("Custom Stop Loss#isSatisfied"));
        assertTrue("TrailingStopLossRule trace log should include current price",
                logContent.contains("currentPrice=117"));
        assertTrue("TrailingStopLossRule trace log should include stop price", logContent.contains("stopPrice=117"));
        assertTrue("TrailingStopLossRule trace log should include trade side", logContent.contains("side=BUY"));
        assertTrue("TrailingStopLossRule trace log should include trailing high",
                logContent.contains("highestPrice=130"));
        assertTrue("TrailingStopLossRule trace log should include configured percentage",
                logContent.contains("lossPercentage=10"));
        assertFalse("TrailingStopLossRule trace log should keep structured key=value formatting without legacy suffix",
                logContent.contains("Current price:"));
        assertTrue("TrailingStopLossRule trace log should include rule type",
                logContent.contains("ruleType=TrailingStopLossRule"));
        assertTrue("TrailingStopLossRule trace log should include active trace mode",
                logContent.contains("mode=VERBOSE"));
        assertTrue("TrailingStopLossRule trace log should include root path", logContent.contains("path=root"));
        assertTrue("TrailingStopLossRule trace log should include root depth", logContent.contains("depth=0"));
    }

    @Test
    public void returnsFalseForIndexBeforeEntry() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(
                new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 120).build());
        TrailingStopLossRule rule = new TrailingStopLossRule(closePrice, numOf(10));
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);
        tradingRecord.enter(2, numOf(114), numFactory.one());

        ruleTraceTestLogger.clear();
        assertFalse(rule.isSatisfied(1, tradingRecord));

        assertTrue("Pre-entry evaluation should be traced with a clear reason",
                ruleTraceTestLogger.getLogOutput().contains("reason=indexBeforeEntry"));
    }

    @Test
    public void isSatisfiedForBuyForBarCount() {
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 110, 120, 130, 120, 117.00, 117.00, 130, 116.99)
                .build());

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
        ClosePriceIndicator closePrice = new ClosePriceIndicator(new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 90, 80, 70, 77.00, 120, 132.01)
                .build());

        // 10% trailing-stop-loss
        TrailingStopLossRule rule = new TrailingStopLossRule(closePrice, numOf(10));
        assertFalse(rule.isSatisfied(0, null));
        assertFalse(rule.isSatisfied(1, tradingRecord));

        // Enter at 84
        tradingRecord.enter(2, numOf(84), numOf(1));

        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(3, tradingRecord));
        assertTrue(rule.isSatisfied(4, tradingRecord));

        // Exit
        tradingRecord.exit(5);

        // Enter at 128
        tradingRecord.enter(5, numOf(128), numOf(1));
        assertFalse(rule.isSatisfied(5, tradingRecord));
        assertTrue(rule.isSatisfied(6, tradingRecord));
    }

    @Test
    public void isSatisfiedForSellForBarCount() {
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.SELL);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 90, 80, 70, 70, 73, 77.00, 90, 120, 132.01)
                .build());

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

    @Test
    public void serializeAndDeserialize() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(
                new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 90, 95, 105).build());
        TrailingStopLossRule rule = new TrailingStopLossRule(closePrice, numOf(7), 2);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(closePrice.getBarSeries(), rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(closePrice.getBarSeries(), rule);
    }
}
