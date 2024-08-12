/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.*;
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.time.ZonedDateTime;

import static org.junit.Assert.*;

public class AverageTrueRangeStopLossRuleTest {

    private BarSeries series;

    @Before
    public void setUp() {
        series = new BaseBarSeriesBuilder().withName("AverageTrueRangeStopLossRuleTest").build();
    }

    @Test
    public void testLongPositionStopLoss() {
        ZonedDateTime initialEndDateTime = ZonedDateTime.now();

        for (int i = 0; i < 10; i++) {
            series.addBar(initialEndDateTime.plusDays(i), 100, 105, 95, 100);
        }

        AverageTrueRangeStopLossRule rule = new AverageTrueRangeStopLossRule(series, 5, 1);

        // Enter long position
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.TradeType.BUY, new LinearTransactionCostModel(0.01),
                new ZeroCostModel());
        tradingRecord.enter(0, series.numOf(100), series.numOf(1));

        // Price remains above stop loss
        series.addBar(series.getLastBar().getEndTime().plusDays(1), 90, 95, 85, 90);
        assertFalse(rule.isSatisfied(series.getEndIndex(), tradingRecord));

        // Price drops below stop loss
        series.addBar(series.getLastBar().getEndTime().plusDays(1), 90, 95, 85, 89);
        assertTrue(rule.isSatisfied(series.getEndIndex(), tradingRecord));
    }

    @Test
    public void testShortPositionStopLoss() {
        ZonedDateTime initialEndDateTime = ZonedDateTime.now();

        for (int i = 0; i < 10; i++) {
            series.addBar(initialEndDateTime.plusDays(i), 100, 105, 95, 100);
        }

        AverageTrueRangeStopLossRule rule = new AverageTrueRangeStopLossRule(series, 5, 2);

        // Enter short position
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.TradeType.SELL, new FixedTransactionCostModel(1),
                new ZeroCostModel());
        tradingRecord.enter(0, series.numOf(100), series.numOf(1));

        // Price below stop loss
        series.addBar(series.getLastBar().getEndTime().plusDays(1), 110, 123, 113, 123);
        assertFalse(rule.isSatisfied(series.getEndIndex(), tradingRecord));

        // Price rises above stop loss
        series.addBar(series.getLastBar().getEndTime().plusDays(1), 110, 127, 117, 127);
        assertTrue(rule.isSatisfied(series.getEndIndex(), tradingRecord));
    }

    @Test
    public void testNoStopLoss() {
        ZonedDateTime initialEndDateTime = ZonedDateTime.now();

        for (int i = 0; i < 10; i++) {
            series.addBar(initialEndDateTime.plusDays(i), 100, 105, 95, 100);
        }

        AverageTrueRangeStopLossRule rule = new AverageTrueRangeStopLossRule(series, 5, 2);

        // Enter long position
        TradingRecord tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.numOf(100), series.numOf(1));

        // Price stays within stop loss range
        series.addBar(series.getLastBar().getEndTime().plusDays(1), 98, 102, 97, 98);

        assertFalse(rule.isSatisfied(10, tradingRecord));
    }

    @Test
    public void testCustomReferencePrice() {
        ZonedDateTime initialEndDateTime = ZonedDateTime.now();

        for (int i = 0; i < 10; i++) {
            series.addBar(initialEndDateTime.plusDays(i), 100, 105, 95, 100);
        }

        ClosePriceIndicator customReference = new ClosePriceIndicator(series);
        AverageTrueRangeStopLossRule rule = new AverageTrueRangeStopLossRule(series, customReference, 5, 2);

        // Enter long position
        TradingRecord tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.numOf(100), series.numOf(1));

        // Price drops below stop loss
        series.addBar(series.getLastBar().getEndTime().plusDays(1), 90, 90, 73, 73);
        assertTrue(rule.isSatisfied(series.getEndIndex(), tradingRecord));
    }

    @Test
    public void testNoTradingRecord() {
        ZonedDateTime initialEndDateTime = ZonedDateTime.now();

        for (int i = 0; i < 10; i++) {
            series.addBar(initialEndDateTime.plusDays(i), 100, 105, 95, 100);
        }

        AverageTrueRangeStopLossRule rule = new AverageTrueRangeStopLossRule(series, 5, 2);

        assertFalse(rule.isSatisfied(9, null));
    }

    @Test
    public void testClosedPosition() {
        ZonedDateTime initialEndDateTime = ZonedDateTime.now();

        for (int i = 0; i < 10; i++) {
            series.addBar(initialEndDateTime.plusDays(i), 100, 105, 95, 100);
        }

        AverageTrueRangeStopLossRule rule = new AverageTrueRangeStopLossRule(series, 5, 2);

        // Enter and exit position
        TradingRecord tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.numOf(100), series.numOf(1));
        tradingRecord.exit(5, series.numOf(110), series.numOf(1));

        assertFalse(rule.isSatisfied(9, tradingRecord));
    }
}