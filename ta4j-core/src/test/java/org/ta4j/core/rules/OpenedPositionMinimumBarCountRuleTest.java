/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;

public class OpenedPositionMinimumBarCountRuleTest {

    @Test(expected = IllegalArgumentException.class)
    public void testAtLeastBarCountRuleForNegativeNumberShouldThrowException() {
        new OpenedPositionMinimumBarCountRule(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAtLeastBarCountRuleForZeroShouldThrowException() {
        new OpenedPositionMinimumBarCountRule(0);
    }

    @Test
    public void testAtLeastOneBarRuleForOpenedTrade() {
        final var rule = new OpenedPositionMinimumBarCountRule(1);
        final var series = new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance())
                .withData(1, 2, 3, 4)
                .build();
        final var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series));

        assertFalse(rule.isSatisfied(0, tradingRecord));
        assertTrue(rule.isSatisfied(1, tradingRecord));
        assertTrue(rule.isSatisfied(2, tradingRecord));
        assertTrue(rule.isSatisfied(3, tradingRecord));
    }

    @Test
    public void testAtLeastMoreThanOneBarRuleForOpenedTrade() {
        final var rule = new OpenedPositionMinimumBarCountRule(2);
        final var series = new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance())
                .withData(1, 2, 3, 4)
                .build();
        final var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series));

        assertFalse(rule.isSatisfied(0, tradingRecord));
        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertTrue(rule.isSatisfied(2, tradingRecord));
        assertTrue(rule.isSatisfied(3, tradingRecord));
    }

    @Test
    public void testAtLeastBarCountRuleForClosedTradeShouldAlwaysReturnsFalse() {
        final var rule = new OpenedPositionMinimumBarCountRule(1);
        final var series = new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance())
                .withData(1, 2, 3, 4)
                .build();
        final var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series));

        assertFalse(rule.isSatisfied(0, tradingRecord));
        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(3, tradingRecord));
    }

    @Test
    public void testAtLeastBarCountRuleForEmptyTradingRecordShouldAlwaysReturnsFalse() {
        final var rule = new OpenedPositionMinimumBarCountRule(1);
        final var tradingRecord = new BaseTradingRecord();

        assertFalse(rule.isSatisfied(0, tradingRecord));
        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(3, tradingRecord));
    }

    @Test
    public void serializeAndDeserialize() {
        final var series = new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance())
                .withData(1, 2, 3)
                .build();
        final var rule = new OpenedPositionMinimumBarCountRule(2);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }
}
