/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class OpenedPositionMinimumBarCountRuleTest {

    @Test
    public void testAtLeastBarCountRuleForNegativeNumberShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new OpenedPositionMinimumBarCountRule(-1);
        });
    }

    @Test
    public void testAtLeastBarCountRuleForZeroShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new OpenedPositionMinimumBarCountRule(0);
        });
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
    public void testAtLeastBarCountRuleForNullTradingRecordShouldAlwaysReturnsFalse() {
        final var rule = new OpenedPositionMinimumBarCountRule(1);

        assertFalse(rule.isSatisfied(0, null));
        assertFalse(rule.isSatisfied(1, null));
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
