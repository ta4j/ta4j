/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

import java.time.Instant;
import java.util.List;
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
 * Demonstrates the core live-style trading-record workflow: streaming fills
 * directly into a record, grouping fills into one logical trade, and inspecting
 * how partial exits are matched under different {@link ExecutionMatchPolicy
 * execution match policies}.
 *
 * <p>
 * The example first streams each {@link TradeFill} directly into a
 * {@link TradingRecord} with {@link TradingRecord#operate(TradeFill)}, then
 * shows the equivalent grouped-order path with
 * {@link Trade#fromFills(TradeType, List)}. Both paths end on the same
 * analytics surface: positions, fees, open-lot snapshots, and closed profit are
 * read from the resulting {@link TradingRecord}. It then replays the same
 * partial exit under FIFO, LIFO, average-cost, and specific-id matching so the
 * resulting closed/open positions are easy to compare.
 * </p>
 */
public class TradeFillRecordingExample {

    private static final Logger LOG = LogManager.getLogger(TradeFillRecordingExample.class);

    public static void main(String[] args) {
        LOG.info("Step 1: stream partial fills directly into TradingRecord");
        BaseTradingRecord streamingRecord = newRecord();
        recordStreamingOrder(streamingRecord, "BUY entry order", entryFills());
        logOpenExposure("After BUY entry order", streamingRecord);
        recordStreamingOrder(streamingRecord, "SELL exit order", exitFills());
        logRecordSummary("Streaming fills", streamingRecord);

        LOG.info("Step 2: record the same exchange fills as grouped logical orders");
        BaseTradingRecord groupedTradeRecord = buildGroupedTradeRecord();
        logRecordSummary("Grouped order batches", groupedTradeRecord);

        LOG.info("Step 3: replay one partial exit under each ExecutionMatchPolicy");
        for (ExecutionMatchPolicy matchPolicy : ExecutionMatchPolicy.values()) {
            BaseTradingRecord matchPolicyRecord = buildMatchingPolicyRecord(matchPolicy);
            logMatchingPolicyOutcome(matchPolicy, matchPolicyRecord);
        }

        LOG.info("The same TradingRecord APIs cover direct fills, grouped trades, and lot-matching inspection.");
    }

    static BaseTradingRecord buildStreamingRecord() {
        BaseTradingRecord record = newRecord();
        applyFills(record, entryFills());
        applyFills(record, exitFills());
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

    static BaseTradingRecord buildMatchingPolicyRecord(ExecutionMatchPolicy matchPolicy) {
        BaseTradingRecord record = newRecord(matchPolicy);
        applyFills(record, matchingPolicyEntryFills());
        record.operate(matchingPolicyExitFill(matchPolicy));
        return record;
    }

    private static BaseTradingRecord newRecord() {
        return newRecord(ExecutionMatchPolicy.FIFO);
    }

    private static BaseTradingRecord newRecord(ExecutionMatchPolicy matchPolicy) {
        return new BaseTradingRecord(TradeType.BUY, matchPolicy, new ZeroCostModel(), new ZeroCostModel(), null, null);
    }

    private static void applyFills(TradingRecord record, List<TradeFill> fills) {
        for (TradeFill fill : fills) {
            record.operate(fill);
        }
    }

    private static void recordStreamingOrder(TradingRecord record, String label, List<TradeFill> fills) {
        LOG.info("{} ({})", label, fills.get(0).orderId());
        for (TradeFill fill : fills) {
            record.operate(fill);
            LOG.info("  {} fill {} -> index={}, price={}, amount={}, fee={}, openPositions={}", fill.side(),
                    fill.correlationId(), fill.index(), fill.price(), fill.amount(), fill.fee(),
                    record.getOpenPositions().size());
        }
    }

    private static void logOpenExposure(String label, TradingRecord record) {
        Position currentPosition = record.getCurrentPosition();
        LOG.info("{} -> netOpenAmount={}, netAverageEntry={}, openLots={}, recordedFees={}", label,
                currentPosition.amount(), currentPosition.averageEntryPrice(), record.getOpenPositions().size(),
                record.getRecordedTotalFees());
        for (int i = 0; i < record.getOpenPositions().size(); i++) {
            Position openPosition = record.getOpenPositions().get(i);
            LOG.info("  open[{}] lot={} amount={} avgEntry={}", i, openPosition.getEntry().getCorrelationId(),
                    openPosition.amount(), openPosition.averageEntryPrice());
        }
    }

    private static void logRecordSummary(String label, TradingRecord record) {
        LOG.info("{} -> trades={}, closedPositions={}, openPositions={}, fees={}, closedProfit={}", label,
                record.getTrades().size(), record.getPositionCount(), record.getOpenPositions().size(),
                record.getRecordedTotalFees(), totalClosedProfit(record));
        for (int i = 0; i < record.getPositions().size(); i++) {
            Position position = record.getPositions().get(i);
            LOG.info("  position[{}] entry={} @ {} amount={}, exit={} @ {}, profit={}", i,
                    position.getEntry().getIndex(), position.getEntry().getPricePerAsset(),
                    position.getEntry().getAmount(), position.getExit().getIndex(),
                    position.getExit().getPricePerAsset(), position.getProfit());
        }
    }

    private static void logMatchingPolicyOutcome(ExecutionMatchPolicy matchPolicy, TradingRecord record) {
        Position closedPosition = record.getPositions().get(0);
        Position currentPosition = record.getCurrentPosition();
        LOG.info("{} -> closedLot={} entry={} amount={}, remainingOpenLots={}, netOpenAmount={}, netAverageEntry={}",
                matchPolicy, closedPosition.getEntry().getCorrelationId(), closedPosition.getEntry().getPricePerAsset(),
                closedPosition.getEntry().getAmount(), record.getOpenPositions().size(), currentPosition.amount(),
                currentPosition.averageEntryPrice());
        for (int i = 0; i < record.getOpenPositions().size(); i++) {
            Position openPosition = record.getOpenPositions().get(i);
            LOG.info("  remaining[{}] lot={} amount={} avgEntry={}", i, openPosition.getEntry().getCorrelationId(),
                    openPosition.amount(), openPosition.averageEntryPrice());
        }
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

    private static List<TradeFill> matchingPolicyEntryFills() {
        DoubleNumFactory numFactory = DoubleNumFactory.getInstance();
        return List.of(
                new TradeFill(1, Instant.parse("2025-02-01T00:00:00Z"), numFactory.hundred(), numFactory.two(),
                        numFactory.zero(), ExecutionSide.BUY, "policy-entry-a", "lot-a"),
                new TradeFill(2, Instant.parse("2025-02-01T00:01:00Z"), numFactory.numOf(106), numFactory.one(),
                        numFactory.zero(), ExecutionSide.BUY, "policy-entry-b", "lot-b"));
    }

    private static TradeFill matchingPolicyExitFill(ExecutionMatchPolicy matchPolicy) {
        DoubleNumFactory numFactory = DoubleNumFactory.getInstance();
        String correlationId = matchPolicy == ExecutionMatchPolicy.SPECIFIC_ID ? "lot-b" : null;
        return new TradeFill(3, Instant.parse("2025-02-01T00:02:00Z"), numFactory.numOf(120), numFactory.one(),
                numFactory.zero(), ExecutionSide.SELL, "policy-exit", correlationId);
    }
}
