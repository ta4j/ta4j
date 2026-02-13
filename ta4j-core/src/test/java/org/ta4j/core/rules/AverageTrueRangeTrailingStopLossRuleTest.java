/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AverageTrueRangeTrailingStopLossRuleTest {
    private BarSeries series;

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withName("Test Series").build();

        series.barBuilder()
                .endTime(Instant.now())
                .openPrice(10)
                .highPrice(12)
                .lowPrice(8)
                .closePrice(11)
                .volume(1000)
                .add();
        series.barBuilder()
                .endTime(series.getLastBar().getEndTime().plus(Duration.ofDays(1)))
                .openPrice(11)
                .highPrice(13)
                .lowPrice(9)
                .closePrice(12)
                .volume(1000)
                .add();
        series.barBuilder()
                .endTime(series.getLastBar().getEndTime().plus(Duration.ofDays(1)))
                .openPrice(12)
                .highPrice(14)
                .lowPrice(10)
                .closePrice(13)
                .volume(1000)
                .add();
        series.barBuilder()
                .endTime(series.getLastBar().getEndTime().plus(Duration.ofDays(1)))
                .openPrice(13)
                .highPrice(15)
                .lowPrice(11)
                .closePrice(14)
                .volume(1000)
                .add();
        series.barBuilder()
                .endTime(series.getLastBar().getEndTime().plus(Duration.ofDays(1)))
                .openPrice(14)
                .highPrice(16)
                .lowPrice(12)
                .closePrice(15)
                .volume(1000)
                .add();
    }

    @Test
    public void testStopLossTriggeredOnLongPosition() {
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());

        var rules = constructRules(3, 1.0);

        for (var rule : rules) {
            assertFalse(rule.isSatisfied(1, tradingRecord)); // Price is still above stop loss
            assertFalse(rule.isSatisfied(2, tradingRecord)); // Price is still above stop loss
        }

        // Simulate a price drop to trigger stop loss
        series.barBuilder()
                .endTime(series.getLastBar().getEndTime().plus(Duration.ofDays(1)))
                .openPrice(11)
                .highPrice(12)
                .lowPrice(9)
                .closePrice(10)
                .volume(1000)
                .add();

        for (var rule : rules) {
            assertTrue(rule.isSatisfied(5, tradingRecord)); // Stop loss should trigger now
        }
    }

    @Test
    public void testStopLossTriggeredOnShortPosition() {
        var tradingRecord = new BaseTradingRecord(Trade.TradeType.SELL);
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());

        var rules = constructRules(3, 1.0);

        for (var rule : rules) {
            assertFalse(rule.isSatisfied(1, tradingRecord)); // Price is still below stop loss
            assertFalse(rule.isSatisfied(2, tradingRecord)); // Price is still below stop loss
        }
        // Simulate a price increase to trigger stop loss
        series.barBuilder()
                .endTime(series.getLastBar().getEndTime().plus(Duration.ofDays(1)))
                .openPrice(15)
                .highPrice(16)
                .lowPrice(14)
                .closePrice(15)
                .volume(1000)
                .add();

        for (var rule : rules) {
            assertTrue(rule.isSatisfied(5, tradingRecord)); // Stop loss should trigger now
        }
    }

    @Test
    public void testStopLossNotTriggered() {
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());

        var rules = constructRules(3, 1.0);

        for (var rule : rules) {
            assertFalse(rule.isSatisfied(1, tradingRecord));
            assertFalse(rule.isSatisfied(2, tradingRecord));
            assertFalse(rule.isSatisfied(3, tradingRecord));
        }
    }

    @Test
    public void testCustomReferencePrice() {
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());

        var rules = constructRules(3, 1.0);

        for (var rule : rules) {
            assertFalse(rule.isSatisfied(1, tradingRecord));
            assertFalse(rule.isSatisfied(2, tradingRecord));
        }

        // Simulate a price drop to trigger stop loss
        series.barBuilder()
                .endTime(series.getLastBar().getEndTime().plus(Duration.ofDays(1)))
                .openPrice(11)
                .highPrice(12)
                .lowPrice(9)
                .closePrice(10)
                .volume(1000)
                .add();

        for (var rule : rules) {
            assertTrue(rule.isSatisfied(5, tradingRecord));
        }
    }

    @Test
    public void testEdgeCaseNoTrade() {
        var tradingRecord = new BaseTradingRecord();

        var rules = constructRules(3, 1.0);

        for (var rule : rules) {
            // No trade, so the rule should never be satisfied
            assertFalse(rule.isSatisfied(0, tradingRecord));
            assertFalse(rule.isSatisfied(1, tradingRecord));
            assertFalse(rule.isSatisfied(2, tradingRecord));
        }
    }

    @Test
    public void serializeAndDeserialize() {
        var rules = constructRules(4, 1.5);

        for (var rule : rules) {
            RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
            RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
        }
    }

    @Test
    public void serializeAndDeserializeWithCustomReference() {
        Num baseline = series.numFactory().numOf(50);
        FixedIndicator<Num> referencePrice = new FixedIndicator<>(series, baseline, baseline, baseline, baseline,
                baseline);

        var rules = Arrays.asList(new AverageTrueRangeTrailingStopLossRule(series, referencePrice, 5, 2.0),
                new AverageTrueRangeTrailingStopLossRule(referencePrice, new ATRIndicator(series, 5), 2.0));

        for (var rule : rules) {
            RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
            RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
        }
    }

    private List<AverageTrueRangeTrailingStopLossRule> constructRules(int atrBarCount, Number atrCoefficient) {
        return Arrays.asList(new AverageTrueRangeTrailingStopLossRule(series, atrBarCount, atrCoefficient),
                new AverageTrueRangeTrailingStopLossRule(series, new ClosePriceIndicator(series), atrBarCount,
                        atrCoefficient),
                new AverageTrueRangeTrailingStopLossRule(new ClosePriceIndicator(series),
                        new ATRIndicator(series, atrBarCount), atrCoefficient));
    }
}
