/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ExecutionMatchPolicy;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradeFill;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;

/**
 * Demonstrates two equivalent ways to record partial fills in a
 * {@link TradingRecord}: stream one {@link TradeFill} at a time with
 * {@link TradingRecord#operate(TradeFill)}, or group a logical order with
 * {@link Trade#fromFills(TradeType, List)} and submit it in one call.
 */
public class TradeFillRecordingExample {

    private static final Logger LOG = LogManager.getLogger(TradeFillRecordingExample.class);

    public static void main(String[] args) {
        BaseTradingRecord streamingRecord = buildStreamingRecord();
        BaseTradingRecord groupedTradeRecord = buildGroupedTradeRecord();

        assertEquivalent(streamingRecord, groupedTradeRecord, "grouped trade parity");

        LOG.info("Streaming fills matched grouped trades: trades={}, positions={}, fees={}, profit={}",
                streamingRecord.getTrades().size(), streamingRecord.getPositionCount(),
                streamingRecord.getRecordedTotalFees(), totalClosedProfit(streamingRecord));
    }

    static BaseTradingRecord buildStreamingRecord() {
        BaseTradingRecord record = newRecord();
        for (TradeFill fill : entryFills()) {
            record.operate(fill);
        }
        for (TradeFill fill : exitFills()) {
            record.operate(fill);
        }
        return record;
    }

    static BaseTradingRecord buildGroupedTradeRecord() {
        BaseTradingRecord record = newRecord();
        record.operate(Trade.fromFills(TradeType.BUY, entryFills()));
        record.operate(Trade.fromFills(TradeType.SELL, exitFills()));
        return record;
    }

    static Num totalClosedProfit(TradingRecord record) {
        Num total = DoubleNumFactory.getInstance().zero();
        for (Position position : record.getPositions()) {
            total = total.plus(position.getProfit());
        }
        return total;
    }

    static void assertEquivalent(TradingRecord expected, TradingRecord actual, String label) {
        require(expected.getTrades().size() == actual.getTrades().size(), () -> label + ": trade count mismatch");
        require(expected.getPositionCount() == actual.getPositionCount(), () -> label + ": position count mismatch");
        assertNum(expected.getRecordedTotalFees(), actual.getRecordedTotalFees(), label + ": fee mismatch");

        for (int i = 0; i < expected.getTrades().size(); i++) {
            Trade left = expected.getTrades().get(i);
            Trade right = actual.getTrades().get(i);
            assertTrade(left, right, label + ": trade[" + i + "]");
        }

        for (int i = 0; i < expected.getPositions().size(); i++) {
            Position left = expected.getPositions().get(i);
            Position right = actual.getPositions().get(i);
            assertTrade(left.getEntry(), right.getEntry(), label + ": position[" + i + "].entry");
            assertTrade(left.getExit(), right.getExit(), label + ": position[" + i + "].exit");
            assertNum(left.getProfit(), right.getProfit(), label + ": position[" + i + "].profit");
        }
    }

    private static BaseTradingRecord newRecord() {
        return new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(), new ZeroCostModel(),
                null, null);
    }

    private static List<TradeFill> entryFills() {
        DoubleNumFactory numFactory = DoubleNumFactory.getInstance();
        return List.of(
                new TradeFill(4, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(), numFactory.one(),
                        numFactory.numOf(0.1), ExecutionSide.BUY, "entry-fill-1", "entry-order"),
                new TradeFill(5, Instant.parse("2025-01-01T00:01:00Z"), numFactory.numOf(101), numFactory.two(),
                        numFactory.numOf(0.2), ExecutionSide.BUY, "entry-fill-2", "entry-order"));
    }

    private static List<TradeFill> exitFills() {
        DoubleNumFactory numFactory = DoubleNumFactory.getInstance();
        return List.of(
                new TradeFill(8, Instant.parse("2025-01-01T00:02:00Z"), numFactory.numOf(110), numFactory.one(),
                        numFactory.numOf(0.05), ExecutionSide.SELL, "exit-fill-1", "exit-order"),
                new TradeFill(9, Instant.parse("2025-01-01T00:03:00Z"), numFactory.numOf(111), numFactory.two(),
                        numFactory.numOf(0.06), ExecutionSide.SELL, "exit-fill-2", "exit-order"));
    }

    private static void assertTrade(Trade expected, Trade actual, String label) {
        require(expected.getType() == actual.getType(), () -> label + ": type mismatch");
        require(expected.getIndex() == actual.getIndex(), () -> label + ": index mismatch");
        assertNum(expected.getPricePerAsset(), actual.getPricePerAsset(), label + ": price mismatch");
        assertNum(expected.getAmount(), actual.getAmount(), label + ": amount mismatch");
        assertNum(expected.getCost(), actual.getCost(), label + ": fee mismatch");
        require(Objects.equals(expected.getOrderId(), actual.getOrderId()), () -> label + ": orderId mismatch");
        require(Objects.equals(expected.getCorrelationId(), actual.getCorrelationId()),
                () -> label + ": correlationId mismatch");
    }

    private static void assertNum(Num expected, Num actual, String message) {
        if (expected == null || actual == null) {
            require(Objects.equals(expected, actual), () -> message);
            return;
        }
        require(expected.isEqual(actual), () -> message + " expected=" + expected + ", actual=" + actual);
    }

    private static void require(boolean condition, Supplier<String> messageSupplier) {
        if (!condition) {
            throw new IllegalStateException(messageSupplier.get());
        }
    }
}
