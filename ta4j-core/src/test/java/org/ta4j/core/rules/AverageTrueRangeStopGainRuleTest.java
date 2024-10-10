/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
}
