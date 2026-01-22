/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

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

        var rule = new AverageTrueRangeTrailingStopLossRule(series, 3, 1.0);

        assertFalse(rule.isSatisfied(1, tradingRecord)); // Price is still above stop loss
        assertFalse(rule.isSatisfied(2, tradingRecord)); // Price is still above stop loss

        // Simulate a price drop to trigger stop loss
        series.barBuilder()
                .endTime(series.getLastBar().getEndTime().plus(Duration.ofDays(1)))
                .openPrice(11)
                .highPrice(12)
                .lowPrice(9)
                .closePrice(10)
                .volume(1000)
                .add();
        assertTrue(rule.isSatisfied(5, tradingRecord)); // Stop loss should trigger now
    }

    @Test
    public void testStopLossTriggeredOnShortPosition() {
        var tradingRecord = new BaseTradingRecord(Trade.TradeType.SELL);
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());

        var rule = new AverageTrueRangeTrailingStopLossRule(series, 3, 1.0);

        assertFalse(rule.isSatisfied(1, tradingRecord)); // Price is still below stop loss
        assertFalse(rule.isSatisfied(2, tradingRecord)); // Price is still below stop loss

        // Simulate a price increase to trigger stop loss
        series.barBuilder()
                .endTime(series.getLastBar().getEndTime().plus(Duration.ofDays(1)))
                .openPrice(15)
                .highPrice(16)
                .lowPrice(14)
                .closePrice(15)
                .volume(1000)
                .add();
        assertTrue(rule.isSatisfied(5, tradingRecord)); // Stop loss should trigger now
    }

    @Test
    public void testStopLossNotTriggered() {
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());

        var rule = new AverageTrueRangeTrailingStopLossRule(series, 3, 1.0);

        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(3, tradingRecord));
    }

    @Test
    public void testCustomReferencePrice() {
        var customReferencePrice = new ClosePriceIndicator(series);
        var rule = new AverageTrueRangeTrailingStopLossRule(series, customReferencePrice, 3, 1.0);

        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());

        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertFalse(rule.isSatisfied(2, tradingRecord));

        // Simulate a price drop to trigger stop loss
        series.barBuilder()
                .endTime(series.getLastBar().getEndTime().plus(Duration.ofDays(1)))
                .openPrice(11)
                .highPrice(12)
                .lowPrice(9)
                .closePrice(10)
                .volume(1000)
                .add();
        assertTrue(rule.isSatisfied(5, tradingRecord));
    }

    @Test
    public void testEdgeCaseNoTrade() {
        var rule = new AverageTrueRangeTrailingStopLossRule(series, 3, 1.0);

        var tradingRecord = new BaseTradingRecord();

        // No trade, so the rule should never be satisfied
        assertFalse(rule.isSatisfied(0, tradingRecord));
        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertFalse(rule.isSatisfied(2, tradingRecord));
    }

    @Test
    public void serializeAndDeserialize() {
        var rule = new AverageTrueRangeTrailingStopLossRule(series, 4, 1.5);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }

    @Test
    public void serializeAndDeserializeWithCustomReference() {
        Num baseline = series.numFactory().numOf(50);
        FixedIndicator<Num> referencePrice = new FixedIndicator<>(series, baseline, baseline, baseline, baseline,
                baseline);
        var rule = new AverageTrueRangeTrailingStopLossRule(series, referencePrice, 5, 2.0);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }
}
