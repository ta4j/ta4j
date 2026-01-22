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
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

public class AverageTrueRangeStopLossRuleTest {

    private BarSeries series;

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withName("AverageTrueRangeStopLossRuleTest").build();
    }

    @Test
    public void testLongPositionStopLoss() {
        var now = Instant.now();

        for (int i = 0; i < 10; i++) {
            series.barBuilder()
                    .endTime(now.plus(Duration.ofDays(i)))
                    .openPrice(100)
                    .highPrice(105)
                    .lowPrice(95)
                    .closePrice(100)
                    .add();
        }

        var rule = new AverageTrueRangeStopLossRule(series, 5, 1);

        // Enter long position
        var tradingRecord = new BaseTradingRecord(Trade.TradeType.BUY, new LinearTransactionCostModel(0.01),
                new ZeroCostModel());
        tradingRecord.enter(0, series.numFactory().hundred(), series.numFactory().one());

        // Price remains above stop loss
        series.barBuilder()
                .endTime(series.getLastBar().getEndTime().plus(Duration.ofDays(1)))
                .openPrice(90)
                .highPrice(95)
                .lowPrice(85)
                .closePrice(90)
                .add();
        assertFalse(rule.isSatisfied(series.getEndIndex(), tradingRecord));

        // Price drops below stop loss
        series.barBuilder()
                .endTime(series.getLastBar().getEndTime().plus(Duration.ofDays(1)))
                .openPrice(90)
                .highPrice(95)
                .lowPrice(85)
                .closePrice(89)
                .add();
        assertTrue(rule.isSatisfied(series.getEndIndex(), tradingRecord));
    }

    @Test
    public void testShortPositionStopLoss() {
        var now = Instant.now();

        for (int i = 0; i < 10; i++) {
            series.barBuilder()
                    .endTime(now.plus(Duration.ofDays(i)))
                    .openPrice(100)
                    .highPrice(105)
                    .lowPrice(95)
                    .closePrice(100)
                    .add();
        }

        var rule = new AverageTrueRangeStopLossRule(series, 5, 2);

        // Enter short position
        var tradingRecord = new BaseTradingRecord(Trade.TradeType.SELL, new FixedTransactionCostModel(1),
                new ZeroCostModel());
        tradingRecord.enter(0, series.numFactory().hundred(), series.numFactory().one());

        // Price below stop loss
        series.barBuilder()
                .endTime(series.getLastBar().getEndTime().plus(Duration.ofDays(1)))
                .openPrice(110)
                .highPrice(123)
                .lowPrice(113)
                .closePrice(123)
                .add();
        assertFalse(rule.isSatisfied(series.getEndIndex(), tradingRecord));

        // Price rises above stop loss
        series.barBuilder()
                .endTime(series.getLastBar().getEndTime().plus(Duration.ofDays(1)))
                .openPrice(110)
                .highPrice(127)
                .lowPrice(117)
                .closePrice(127)
                .add();
        assertTrue(rule.isSatisfied(series.getEndIndex(), tradingRecord));
    }

    @Test
    public void testNoStopLoss() {
        var now = Instant.now();

        for (int i = 0; i < 10; i++) {
            series.barBuilder()
                    .endTime(now.plus(Duration.ofDays(i)))
                    .openPrice(100)
                    .highPrice(105)
                    .lowPrice(95)
                    .closePrice(100)
                    .add();
        }

        var rule = new AverageTrueRangeStopLossRule(series, 5, 2);

        // Enter long position
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.numFactory().hundred(), series.numFactory().one());

        // Price stays within stop loss range
        series.barBuilder()
                .endTime(series.getLastBar().getEndTime().plus(Duration.ofDays(1)))
                .openPrice(98)
                .highPrice(102)
                .lowPrice(97)
                .closePrice(98)
                .add();

        assertFalse(rule.isSatisfied(10, tradingRecord));
    }

    @Test
    public void testCustomReferencePrice() {
        var now = Instant.now();

        for (int i = 0; i < 10; i++) {
            series.barBuilder()
                    .endTime(now.plus(Duration.ofDays(i)))
                    .openPrice(100)
                    .highPrice(105)
                    .lowPrice(95)
                    .closePrice(100)
                    .add();
        }

        var customReference = new ClosePriceIndicator(series);
        var rule = new AverageTrueRangeStopLossRule(series, customReference, 5, 2);

        // Enter long position
        TradingRecord tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.numFactory().hundred(), series.numFactory().one());

        // Price drops below stop loss
        series.barBuilder()
                .endTime(series.getLastBar().getEndTime().plus(Duration.ofDays(1)))
                .openPrice(90)
                .highPrice(90)
                .lowPrice(73)
                .closePrice(73)
                .add();
        assertTrue(rule.isSatisfied(series.getEndIndex(), tradingRecord));
    }

    @Test
    public void testNoTradingRecord() {
        var now = Instant.now();

        for (int i = 0; i < 10; i++) {
            series.barBuilder()
                    .endTime(now.plus(Duration.ofDays(i)))
                    .openPrice(100)
                    .highPrice(105)
                    .lowPrice(95)
                    .closePrice(100)
                    .add();
        }

        var rule = new AverageTrueRangeStopLossRule(series, 5, 2);

        assertFalse(rule.isSatisfied(9, null));
    }

    @Test
    public void testClosedPosition() {
        var now = Instant.now();

        for (int i = 0; i < 10; i++) {
            series.barBuilder()
                    .endTime(now.plus(Duration.ofDays(i)))
                    .openPrice(100)
                    .highPrice(105)
                    .lowPrice(95)
                    .closePrice(100)
                    .add();
        }

        var rule = new AverageTrueRangeStopLossRule(series, 5, 2);

        // Enter and exit position
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.numFactory().hundred(), series.numFactory().one());
        tradingRecord.exit(5, series.numFactory().hundred(), series.numFactory().one());

        assertFalse(rule.isSatisfied(9, tradingRecord));
    }

    @Test
    public void serializeAndDeserialize() {
        var now = Instant.now();
        for (int i = 0; i < 5; i++) {
            series.barBuilder()
                    .endTime(now.plus(Duration.ofDays(i)))
                    .openPrice(100 + i)
                    .highPrice(105 + i)
                    .lowPrice(95 + i)
                    .closePrice(100 + i)
                    .add();
        }
        var rule = new AverageTrueRangeStopLossRule(series, 3, 1.25);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }

    @Test
    public void serializeAndDeserializeWithCustomReference() {
        var now = Instant.now();
        for (int i = 0; i < 5; i++) {
            series.barBuilder()
                    .endTime(now.plus(Duration.ofDays(i)))
                    .openPrice(100 + i)
                    .highPrice(105 + i)
                    .lowPrice(95 + i)
                    .closePrice(100 + i)
                    .add();
        }
        Num base = series.numFactory().numOf(150);
        FixedIndicator<Num> referencePrice = new FixedIndicator<>(series, base, base, base, base, base);
        var rule = new AverageTrueRangeStopLossRule(series, referencePrice, 4, 2.0);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }
}
