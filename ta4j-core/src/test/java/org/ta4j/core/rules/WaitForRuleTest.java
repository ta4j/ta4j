/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

public class WaitForRuleTest {

    private TradingRecord tradingRecord;
    private WaitForRule rule;
    private BarSeries series;

    @Before
    public void setUp() {
        tradingRecord = new BaseTradingRecord();
        series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5).build();
    }

    @Test
    public void waitForSinceLastBuyRuleIsSatisfied() {
        // Waits for 3 bars since last buy trade
        rule = new WaitForRule(Trade.TradeType.BUY, 3);

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
        // Waits for 2 bars since last sell trade
        rule = new WaitForRule(Trade.TradeType.SELL, 2);

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

    @Test
    public void serializeAndDeserialize() {
        rule = new WaitForRule(Trade.TradeType.BUY, 2);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }
}
