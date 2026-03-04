/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;

/**
 * Internal core that produces unified state snapshots for trading records.
 *
 * <p>
 * This class is intentionally package-private and supplier-based so both
 * {@link BaseTradingRecord} and {@link LiveTradingRecord} can expose
 * diagnostics from one shared implementation while their write paths are being
 * consolidated.
 * </p>
 */
final class TradingRecordCore {

    private final TradeType startingType;
    private final Supplier<List<Trade>> tradesSupplier;
    private final Supplier<List<Position>> closedPositionsSupplier;
    private final Supplier<Position> currentPositionSupplier;
    private final Supplier<List<OpenPosition>> openPositionsSupplier;
    private final Supplier<OpenPosition> netOpenPositionSupplier;
    private final Supplier<Num> totalFeesSupplier;

    TradingRecordCore(TradeType startingType, Supplier<List<Trade>> tradesSupplier,
            Supplier<List<Position>> closedPositionsSupplier, Supplier<Position> currentPositionSupplier,
            Supplier<List<OpenPosition>> openPositionsSupplier, Supplier<OpenPosition> netOpenPositionSupplier,
            Supplier<Num> totalFeesSupplier) {
        this.startingType = Objects.requireNonNull(startingType, "startingType");
        this.tradesSupplier = Objects.requireNonNull(tradesSupplier, "tradesSupplier");
        this.closedPositionsSupplier = Objects.requireNonNull(closedPositionsSupplier, "closedPositionsSupplier");
        this.currentPositionSupplier = Objects.requireNonNull(currentPositionSupplier, "currentPositionSupplier");
        this.openPositionsSupplier = Objects.requireNonNull(openPositionsSupplier, "openPositionsSupplier");
        this.netOpenPositionSupplier = Objects.requireNonNull(netOpenPositionSupplier, "netOpenPositionSupplier");
        this.totalFeesSupplier = Objects.requireNonNull(totalFeesSupplier, "totalFeesSupplier");
    }

    List<Trade> getTradesSnapshot() {
        List<Trade> trades = tradesSupplier.get();
        if (trades == null) {
            return List.of();
        }
        return List.copyOf(trades);
    }

    List<Position> getClosedPositionsSnapshot() {
        List<Position> closedPositions = closedPositionsSupplier.get();
        if (closedPositions == null) {
            return List.of();
        }
        return List.copyOf(closedPositions);
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
        Num totalFees = totalFeesSupplier.get();
        if (totalFees != null) {
            return totalFees;
        }
        return DoubleNumFactory.getInstance().zero();
    }

    TradingRecordDebugSnapshot snapshot() {
        return new TradingRecordDebugSnapshot(startingType, getTradesSnapshot(), getClosedPositionsSnapshot(),
                getCurrentPositionView(), getOpenPositionsSnapshot(), getNetOpenPositionSnapshot(), getTotalFees());
    }
}
