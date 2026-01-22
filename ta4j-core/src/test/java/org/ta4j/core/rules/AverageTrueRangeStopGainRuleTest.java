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
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;

public class AverageTrueRangeStopGainRuleTest {

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
    public void testStopGainTriggeredOnLongPosition() {
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());

        var rule = new AverageTrueRangeStopGainRule(series, 3, 2.0);

        assertFalse(rule.isSatisfied(1, tradingRecord)); // Price is still below stop gain
        assertFalse(rule.isSatisfied(2, tradingRecord)); // Price is still below stop gain

        // Simulate a price rise to trigger stop gain
        series.barBuilder()
                .endTime(series.getLastBar().getEndTime().plus(Duration.ofDays(1)))
                .openPrice(16)
                .closePrice(19)
                .lowPrice(15)
                .highPrice(19)
                .volume(1000)
                .add();
        assertTrue(rule.isSatisfied(5, tradingRecord)); // Stop gain should trigger now
    }

    @Test
    public void testStopGainTriggeredOnShortPosition() {
        var tradingRecord = new BaseTradingRecord(Trade.TradeType.SELL);
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), series.numFactory().minusOne());

        var rule = new AverageTrueRangeStopGainRule(series, 3, 1);

        assertFalse(rule.isSatisfied(1, tradingRecord)); // Price is still above stop gain
        assertFalse(rule.isSatisfied(2, tradingRecord)); // Price is still above stop gain

        // Simulate a price drop to trigger stop gain
        series.barBuilder()
                .endTime(series.getLastBar().getEndTime().plus(Duration.ofDays(1)))
                .openPrice(7)
                .highPrice(8)
                .lowPrice(4)
                .closePrice(4)
                .volume(1000)
                .add();
        assertTrue(rule.isSatisfied(5, tradingRecord)); // Stop gain should trigger now
    }

    @Test
    public void testStopGainNotTriggered() {
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());

        var rule = new AverageTrueRangeStopGainRule(series, 3, 2.0);

        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(3, tradingRecord));
    }

    @Test
    public void testEdgeCaseNoTrade() {
        var rule = new AverageTrueRangeStopGainRule(series, 3, 2.0);

        var tradingRecord = new BaseTradingRecord();

        // No trade, so the rule should never be satisfied
        assertFalse(rule.isSatisfied(0, tradingRecord));
        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertFalse(rule.isSatisfied(2, tradingRecord));
    }

    @Test
    public void serializeAndDeserialize() {
        var rule = new AverageTrueRangeStopGainRule(series, 3, 1.5);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }

    @Test
    public void serializeAndDeserializeWithCustomReference() {
        Num constant = series.numFactory().numOf(20);
        FixedIndicator<Num> reference = new FixedIndicator<>(series, constant, constant, constant, constant, constant);
        var rule = new AverageTrueRangeStopGainRule(series, reference, 4, 2.25);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }
}
