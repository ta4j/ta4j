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
import org.ta4j.core.mocks.MockBarSeriesBuilder;

public class AverageTrueRangeTrailingStopGainRuleTest {

    private BarSeries series;

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withName("ATR Trailing Stop Gain Test").build();

        var now = Instant.now();
        double[] closes = { 10, 12, 14, 16, 11 };
        for (int i = 0; i < closes.length; i++) {
            double close = closes[i];
            series.barBuilder()
                    .endTime(now.plus(Duration.ofDays(i)))
                    .openPrice(close)
                    .highPrice(close + 1)
                    .lowPrice(close - 1)
                    .closePrice(close)
                    .add();
        }
    }

    @Test
    public void testTrailingStopGainTriggeredOnLongPosition() {
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());

        var rule = new AverageTrueRangeTrailingStopGainRule(series, 3, 1);

        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(3, tradingRecord));
        assertTrue(rule.isSatisfied(4, tradingRecord));
    }

    @Test
    public void testTrailingStopGainTriggeredOnShortPosition() {
        series = new MockBarSeriesBuilder().withName("ATR Trailing Stop Gain Short Test").build();
        var now = Instant.now();
        double[] closes = { 10, 8, 6, 4, 9 };
        for (int i = 0; i < closes.length; i++) {
            double close = closes[i];
            series.barBuilder()
                    .endTime(now.plus(Duration.ofDays(i)))
                    .openPrice(close)
                    .highPrice(close + 1)
                    .lowPrice(close - 1)
                    .closePrice(close)
                    .add();
        }

        var tradingRecord = new BaseTradingRecord(Trade.TradeType.SELL);
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());

        var rule = new AverageTrueRangeTrailingStopGainRule(series, 3, 1);

        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(3, tradingRecord));
        assertTrue(rule.isSatisfied(4, tradingRecord));
    }

    @Test
    public void serializeAndDeserialize() {
        var rule = new AverageTrueRangeTrailingStopGainRule(series, 3, 1.5);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }
}
