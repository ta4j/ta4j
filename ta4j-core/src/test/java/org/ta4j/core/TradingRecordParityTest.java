/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.backtest.TradeOnCurrentCloseModel;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.FixedRule;

class TradingRecordParityTest {

    private final NumFactory numFactory = DoubleNumFactory.getInstance();

    @Test
    void operateParityLongFlow() {
        BaseTradingRecord baseRecord = new BaseTradingRecord(TradeType.BUY, new ZeroCostModel(), new ZeroCostModel());
        LiveTradingRecord liveRecord = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO,
                new ZeroCostModel(), new ZeroCostModel(), null, null);

        applySyntheticTrade(baseRecord, 1, numOf(100), numOf(2));
        applySyntheticTrade(baseRecord, 3, numOf(120), numOf(2));
        applySyntheticTrade(baseRecord, 5, numOf(90), numOf(1));

        applySyntheticTrade(liveRecord, 1, numOf(100), numOf(2));
        applySyntheticTrade(liveRecord, 3, numOf(120), numOf(2));
        applySyntheticTrade(liveRecord, 5, numOf(90), numOf(1));

        assertEquivalent(baseRecord, liveRecord);
    }

    @Test
    void operateParityShortFlow() {
        BaseTradingRecord baseRecord = new BaseTradingRecord(TradeType.SELL, new ZeroCostModel(), new ZeroCostModel());
        LiveTradingRecord liveRecord = new LiveTradingRecord(TradeType.SELL, ExecutionMatchPolicy.FIFO,
                new ZeroCostModel(), new ZeroCostModel(), null, null);

        applySyntheticTrade(baseRecord, 2, numOf(100), numOf(1));
        applySyntheticTrade(baseRecord, 4, numOf(90), numOf(1));
        applySyntheticTrade(baseRecord, 6, numOf(95), numOf(1));
        applySyntheticTrade(baseRecord, 7, numOf(85), numOf(1));

        applySyntheticTrade(liveRecord, 2, numOf(100), numOf(1));
        applySyntheticTrade(liveRecord, 4, numOf(90), numOf(1));
        applySyntheticTrade(liveRecord, 6, numOf(95), numOf(1));
        applySyntheticTrade(liveRecord, 7, numOf(85), numOf(1));

        assertEquivalent(baseRecord, liveRecord);
    }

    @Test
    void barSeriesManagerParityWithProvidedLiveRecord() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10d, 20d, 30d, 15d, 25d, 35d)
                .build();
        Strategy strategy = new BaseStrategy(new FixedRule(1, 4), new FixedRule(2, 5));
        BarSeriesManager manager = new BarSeriesManager(series, new TradeOnCurrentCloseModel());

        TradingRecord baseRecord = manager.run(strategy, TradeType.BUY, numFactory.one(), 0, series.getEndIndex());
        LiveTradingRecord liveRecord = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO,
                new ZeroCostModel(), new ZeroCostModel(), 0, series.getEndIndex());
        TradingRecord liveBacktestRecord = manager.run(strategy, liveRecord, numFactory.one(), 0, series.getEndIndex());

        assertEquivalent(baseRecord, liveBacktestRecord);
    }

    private void applySyntheticTrade(TradingRecord record, int index, Num price, Num amount) {
        record.operate(index, price, amount);
    }

    private void assertEquivalent(TradingRecord expected, TradingRecord actual) {
        assertEquals(expected.getStartingType(), actual.getStartingType());
        assertEquals(expected.isClosed(), actual.isClosed());
        assertNullableTradeEqual(expected.getLastTrade(), actual.getLastTrade());
        assertNullableTradeEqual(expected.getLastTrade(TradeType.BUY), actual.getLastTrade(TradeType.BUY));
        assertNullableTradeEqual(expected.getLastTrade(TradeType.SELL), actual.getLastTrade(TradeType.SELL));
        assertNullableTradeEqual(expected.getLastEntry(), actual.getLastEntry());
        assertNullableTradeEqual(expected.getLastExit(), actual.getLastExit());

        assertEquals(expected.getTrades().size(), actual.getTrades().size());
        for (int i = 0; i < expected.getTrades().size(); i++) {
            Trade expectedTrade = expected.getTrades().get(i);
            Trade actualTrade = actual.getTrades().get(i);
            assertTradeEqual(expectedTrade, actualTrade);
        }

        assertEquals(expected.getPositions().size(), actual.getPositions().size());
        for (int i = 0; i < expected.getPositions().size(); i++) {
            Position expectedPosition = expected.getPositions().get(i);
            Position actualPosition = actual.getPositions().get(i);
            assertNotNull(expectedPosition.getEntry());
            assertNotNull(actualPosition.getEntry());
            assertTradeEqual(expectedPosition.getEntry(), actualPosition.getEntry());
            if (expectedPosition.getExit() != null || actualPosition.getExit() != null) {
                assertNotNull(expectedPosition.getExit());
                assertNotNull(actualPosition.getExit());
                assertTradeEqual(expectedPosition.getExit(), actualPosition.getExit());
            }
        }

        Position expectedCurrent = expected.getCurrentPosition();
        Position actualCurrent = actual.getCurrentPosition();
        assertEquals(expectedCurrent.isNew(), actualCurrent.isNew());
        assertEquals(expectedCurrent.isOpened(), actualCurrent.isOpened());
        assertEquals(expectedCurrent.isClosed(), actualCurrent.isClosed());
        if (expectedCurrent.getEntry() != null || actualCurrent.getEntry() != null) {
            assertNotNull(expectedCurrent.getEntry());
            assertNotNull(actualCurrent.getEntry());
            assertTradeEqual(expectedCurrent.getEntry(), actualCurrent.getEntry());
        }
    }

    private void assertNullableTradeEqual(Trade expected, Trade actual) {
        if (expected == null || actual == null) {
            assertEquals(expected, actual);
            return;
        }
        assertTradeEqual(expected, actual);
    }

    private void assertTradeEqual(Trade expected, Trade actual) {
        assertEquals(expected.getType(), actual.getType());
        assertEquals(expected.getIndex(), actual.getIndex());
        assertEquals(expected.getPricePerAsset(), actual.getPricePerAsset());
        assertEquals(expected.getAmount(), actual.getAmount());
    }

    private Num numOf(Number value) {
        return numFactory.numOf(value);
    }
}
