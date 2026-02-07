/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
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

        // Enter long position
        var tradingRecord = new BaseTradingRecord(Trade.TradeType.BUY, new LinearTransactionCostModel(0.01),
                new ZeroCostModel());
        tradingRecord.enter(0, series.numFactory().hundred(), series.numFactory().one());

        var rules = constructRules(5, 1);

        // Price remains above stop loss
        series.barBuilder()
                .endTime(series.getLastBar().getEndTime().plus(Duration.ofDays(1)))
                .openPrice(90)
                .highPrice(95)
                .lowPrice(85)
                .closePrice(90)
                .add();

        for (var rule : rules) {
            assertFalse(rule.isSatisfied(series.getEndIndex(), tradingRecord));
        }

        // Price drops below stop loss
        series.barBuilder()
                .endTime(series.getLastBar().getEndTime().plus(Duration.ofDays(1)))
                .openPrice(90)
                .highPrice(95)
                .lowPrice(85)
                .closePrice(89)
                .add();

        for (var rule : rules) {
            assertTrue(rule.isSatisfied(series.getEndIndex(), tradingRecord));
        }
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

        // Enter short position
        var tradingRecord = new BaseTradingRecord(Trade.TradeType.SELL, new FixedTransactionCostModel(1),
                new ZeroCostModel());
        tradingRecord.enter(0, series.numFactory().hundred(), series.numFactory().one());

        var rules = constructRules(5, 2);

        // Price below stop loss
        series.barBuilder()
                .endTime(series.getLastBar().getEndTime().plus(Duration.ofDays(1)))
                .openPrice(110)
                .highPrice(123)
                .lowPrice(113)
                .closePrice(123)
                .add();

        for (var rule : rules) {
            assertFalse(rule.isSatisfied(series.getEndIndex(), tradingRecord));
        }

        // Price rises above stop loss
        series.barBuilder()
                .endTime(series.getLastBar().getEndTime().plus(Duration.ofDays(1)))
                .openPrice(110)
                .highPrice(127)
                .lowPrice(117)
                .closePrice(127)
                .add();

        for (var rule : rules) {
            assertTrue(rule.isSatisfied(series.getEndIndex(), tradingRecord));
        }
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

        // Enter long position
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.numFactory().hundred(), series.numFactory().one());

        var rules = constructRules(5, 2);

        // Price stays within stop loss range
        series.barBuilder()
                .endTime(series.getLastBar().getEndTime().plus(Duration.ofDays(1)))
                .openPrice(98)
                .highPrice(102)
                .lowPrice(97)
                .closePrice(98)
                .add();

        for (var rule : rules) {
            assertFalse(rule.isSatisfied(10, tradingRecord));
        }
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

        // Enter long position
        TradingRecord tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.numFactory().hundred(), series.numFactory().one());

        var customReference = new ClosePriceIndicator(series);
        var rules = Arrays.asList(new AverageTrueRangeStopLossRule(series, customReference, 5, 2),
                new AverageTrueRangeStopLossRule(customReference, new ATRIndicator(series, 5), 2));

        // Price drops below stop loss
        series.barBuilder()
                .endTime(series.getLastBar().getEndTime().plus(Duration.ofDays(1)))
                .openPrice(90)
                .highPrice(90)
                .lowPrice(73)
                .closePrice(73)
                .add();

        for (var rule : rules) {
            assertTrue(rule.isSatisfied(series.getEndIndex(), tradingRecord));
        }
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

        var rules = constructRules(5, 2);

        for (var rule : rules) {
            assertFalse(rule.isSatisfied(9, null));
        }
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

        // Enter and exit position
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.numFactory().hundred(), series.numFactory().one());
        tradingRecord.exit(5, series.numFactory().hundred(), series.numFactory().one());

        var rules = constructRules(5, 2);

        for (var rule : rules) {
            assertFalse(rule.isSatisfied(9, tradingRecord));
        }
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
        var rules = constructRules(3, 1.25);

        for (var rule : rules) {
            RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
            RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
        }
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
        var rules = Arrays.asList(new AverageTrueRangeStopLossRule(series, referencePrice, 4, 2.0),
                new AverageTrueRangeStopLossRule(referencePrice, new ATRIndicator(series, 4), 2.0));

        for (var rule : rules) {
            RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
            RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
        }
    }

    private List<AverageTrueRangeStopLossRule> constructRules(int atrBarCount, Number atrCoefficient) {
        return Arrays.asList(new AverageTrueRangeStopLossRule(series, atrBarCount, atrCoefficient),
                new AverageTrueRangeStopLossRule(series, new ClosePriceIndicator(series), atrBarCount, atrCoefficient),
                new AverageTrueRangeStopLossRule(new ClosePriceIndicator(series), new ATRIndicator(series, atrBarCount),
                        atrCoefficient));
    }
}
