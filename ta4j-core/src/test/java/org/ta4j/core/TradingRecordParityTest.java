/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
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

    @Test
    void partialFillWeightedAverageParity() {
        BaseTradingRecord baseRecord = new BaseTradingRecord(TradeType.BUY, new ZeroCostModel(), new ZeroCostModel());
        LiveTradingRecord liveRecord = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO,
                new ZeroCostModel(), new ZeroCostModel(), null, null);
        Trade aggregatedEntry = Trade.fromFills(TradeType.BUY,
                List.of(new TradeFill(1, numOf(100), numOf(1)), new TradeFill(2, numOf(101), numOf(2))));
        Num expectedAverage = numOf(302).dividedBy(numOf(3));

        baseRecord.operate(aggregatedEntry);
        liveRecord.operate(aggregatedEntry);

        Position baseCurrent = baseRecord.getCurrentPosition();
        Position liveCurrent = liveRecord.getCurrentPosition();
        assertNotNull(baseCurrent.getEntry());
        assertNotNull(liveCurrent.getEntry());
        assertEquals(numOf(3), baseCurrent.getEntry().getAmount());
        assertEquals(numOf(3), liveCurrent.getEntry().getAmount());
        assertEquals(expectedAverage, baseCurrent.getEntry().getPricePerAsset());
        assertEquals(expectedAverage, liveCurrent.getEntry().getPricePerAsset());
    }

    @Test
    void debugSnapshotParityForClosedFlow() {
        BaseTradingRecord baseRecord = new BaseTradingRecord(TradeType.BUY, new ZeroCostModel(), new ZeroCostModel());
        LiveTradingRecord liveRecord = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO,
                new ZeroCostModel(), new ZeroCostModel(), null, null);

        applySyntheticTrade(baseRecord, 1, numOf(100), numOf(2));
        applySyntheticTrade(baseRecord, 3, numOf(120), numOf(2));
        applySyntheticTrade(liveRecord, 1, numOf(100), numOf(2));
        applySyntheticTrade(liveRecord, 3, numOf(120), numOf(2));

        TradingRecordDebugSnapshot baseSnapshot = baseRecord.debugSnapshot();
        TradingRecordDebugSnapshot liveSnapshot = liveRecord.debugSnapshot();

        assertSnapshotEquivalent(baseSnapshot, liveSnapshot);
        assertThrows(UnsupportedOperationException.class, () -> baseSnapshot.trades().add(null));
        assertThrows(UnsupportedOperationException.class, () -> liveSnapshot.closedPositions().add(null));
    }

    @Test
    void debugSnapshotCapturesOpenExposureParity() {
        BaseTradingRecord baseRecord = new BaseTradingRecord(TradeType.BUY, new ZeroCostModel(), new ZeroCostModel());
        LiveTradingRecord liveRecord = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO,
                new ZeroCostModel(), new ZeroCostModel(), null, null);

        applySyntheticTrade(baseRecord, 1, numOf(100), numOf(2));
        applySyntheticTrade(liveRecord, 1, numOf(100), numOf(2));

        TradingRecordDebugSnapshot baseSnapshot = baseRecord.debugSnapshot();
        TradingRecordDebugSnapshot liveSnapshot = liveRecord.debugSnapshot();

        assertSnapshotEquivalent(baseSnapshot, liveSnapshot);
        assertEquals(1, baseSnapshot.openPositions().size());
        assertEquals(numOf(2), baseSnapshot.netOpenPosition().amount());
        assertEquals(numOf(100), baseSnapshot.netOpenPosition().averageEntryPrice());
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

    private void assertSnapshotEquivalent(TradingRecordDebugSnapshot expected, TradingRecordDebugSnapshot actual) {
        assertEquals(expected.startingType(), actual.startingType());
        assertEquals(expected.totalFees(), actual.totalFees());
        assertEquals(expected.trades().size(), actual.trades().size());
        assertEquals(expected.closedPositions().size(), actual.closedPositions().size());
        assertEquals(expected.openPositions().size(), actual.openPositions().size());

        for (int i = 0; i < expected.trades().size(); i++) {
            assertTradeEqual(expected.trades().get(i), actual.trades().get(i));
        }
        for (int i = 0; i < expected.closedPositions().size(); i++) {
            Position expectedPosition = expected.closedPositions().get(i);
            Position actualPosition = actual.closedPositions().get(i);
            assertTradeEqual(expectedPosition.getEntry(), actualPosition.getEntry());
            assertTradeEqual(expectedPosition.getExit(), actualPosition.getExit());
        }

        OpenPosition expectedNet = expected.netOpenPosition();
        OpenPosition actualNet = actual.netOpenPosition();
        if (expectedNet == null || actualNet == null) {
            assertEquals(expectedNet, actualNet);
            return;
        }
        assertEquals(expectedNet.side(), actualNet.side());
        assertEquals(expectedNet.amount(), actualNet.amount());
        assertEquals(expectedNet.averageEntryPrice(), actualNet.averageEntryPrice());
        assertEquals(expectedNet.totalEntryCost(), actualNet.totalEntryCost());
        assertEquals(expectedNet.totalFees(), actualNet.totalFees());
    }

    private Num numOf(Number value) {
        return numFactory.numOf(value);
    }
}
