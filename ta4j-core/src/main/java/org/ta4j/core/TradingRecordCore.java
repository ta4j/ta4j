/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.num.Num;

/**
 * Internal core that produces unified state snapshots for trading records.
 *
 * <p>
 * This class is intentionally package-private and supplier-based so trading
 * record facades can expose diagnostics from one shared implementation.
 * </p>
 */
final class TradingRecordCore {

    @FunctionalInterface
    interface TradeApplier {
        /**
         * Applies one concrete trade mutation at the record facade.
         */
        void apply(int index, Trade trade, long sequence);
    }

    @FunctionalInterface
    interface SyntheticApplier {
        /**
         * Applies one synthetic trade mutation at the record facade.
         */
        void apply(int index, TradeType type, Num price, Num amount, CostModel transactionCostModel);
    }

    private final TradeType startingType;
    private final Supplier<List<Trade>> tradesSupplier;
    private final Supplier<List<Position>> closedPositionsSupplier;
    private final Supplier<Position> currentPositionSupplier;
    private final Supplier<List<OpenPosition>> openPositionsSupplier;
    private final Supplier<OpenPosition> netOpenPositionSupplier;
    private final Supplier<Num> totalFeesSupplier;
    private final TradeApplier tradeApplier;
    private final SyntheticApplier syntheticApplier;

    TradingRecordCore(TradeType startingType, Supplier<List<Trade>> tradesSupplier,
            Supplier<List<Position>> closedPositionsSupplier, Supplier<Position> currentPositionSupplier,
            Supplier<List<OpenPosition>> openPositionsSupplier, Supplier<OpenPosition> netOpenPositionSupplier,
            Supplier<Num> totalFeesSupplier) {
        this(startingType, tradesSupplier, closedPositionsSupplier, currentPositionSupplier, openPositionsSupplier,
                netOpenPositionSupplier, totalFeesSupplier, null, null);
    }

    TradingRecordCore(TradeType startingType, Supplier<List<Trade>> tradesSupplier,
            Supplier<List<Position>> closedPositionsSupplier, Supplier<Position> currentPositionSupplier,
            Supplier<List<OpenPosition>> openPositionsSupplier, Supplier<OpenPosition> netOpenPositionSupplier,
            Supplier<Num> totalFeesSupplier, TradeApplier tradeApplier, SyntheticApplier syntheticApplier) {
        this.startingType = Objects.requireNonNull(startingType, "startingType");
        this.tradesSupplier = Objects.requireNonNull(tradesSupplier, "tradesSupplier");
        this.closedPositionsSupplier = Objects.requireNonNull(closedPositionsSupplier, "closedPositionsSupplier");
        this.currentPositionSupplier = Objects.requireNonNull(currentPositionSupplier, "currentPositionSupplier");
        this.openPositionsSupplier = Objects.requireNonNull(openPositionsSupplier, "openPositionsSupplier");
        this.netOpenPositionSupplier = Objects.requireNonNull(netOpenPositionSupplier, "netOpenPositionSupplier");
        this.totalFeesSupplier = Objects.requireNonNull(totalFeesSupplier, "totalFeesSupplier");
        this.tradeApplier = tradeApplier;
        this.syntheticApplier = syntheticApplier;
    }

    void applyTrade(int index, Trade trade, long sequence) {
        Objects.requireNonNull(trade, "trade");
        if (tradeApplier == null) {
            throw new UnsupportedOperationException("Trade mutation hook is not configured");
        }
        tradeApplier.apply(index, trade, sequence);
    }

    void applySynthetic(int index, TradeType type, Num price, Num amount, CostModel transactionCostModel) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(transactionCostModel, "transactionCostModel");
        if (syntheticApplier == null) {
            throw new UnsupportedOperationException("Synthetic mutation hook is not configured");
        }
        syntheticApplier.apply(index, type, price, amount, transactionCostModel);
    }

    List<Trade> getTradesSnapshot() {
        List<Trade> trades = tradesSupplier.get();
        if (trades == null) {
            return List.of();
        }
        return List.copyOf(trades);
    }

    List<Trade> getTradesView() {
        List<Trade> trades = tradesSupplier.get();
        if (trades == null) {
            return List.of();
        }
        return trades;
    }

    List<Position> getClosedPositionsSnapshot() {
        List<Position> closedPositions = closedPositionsSupplier.get();
        if (closedPositions == null) {
            return List.of();
        }
        return List.copyOf(closedPositions);
    }

    List<Position> getClosedPositionsView() {
        List<Position> closedPositions = closedPositionsSupplier.get();
        if (closedPositions == null) {
            return List.of();
        }
        return closedPositions;
    }

    List<OpenPosition> getOpenPositionsSnapshot() {
        List<OpenPosition> openPositions = openPositionsSupplier.get();
        if (openPositions == null) {
            return List.of();
        }
        return List.copyOf(openPositions);
    }

    OpenPosition getNetOpenPositionSnapshot() {
        return netOpenPositionSupplier.get();
    }

    Position getCurrentPositionView() {
        return currentPositionSupplier.get();
    }

    Num getTotalFees() {
        return Objects.requireNonNull(totalFeesSupplier.get(), "totalFeesSupplier.get()");
    }

    TradingRecordDebugSnapshot snapshot() {
        return new TradingRecordDebugSnapshot(startingType, getTradesSnapshot(), getClosedPositionsSnapshot(),
                getCurrentPositionView(), getOpenPositionsSnapshot(), getNetOpenPositionSnapshot(), getTotalFees());
    }
}
