/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.util.List;
import java.util.Objects;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.num.Num;

/**
 * Immutable diagnostic snapshot for a trading record.
 *
 * <p>
 * Package-private on purpose: this is an internal debugging and parity aid
 * while the live and backtest stacks converge on one mutation core.
 * </p>
 */
record TradingRecordDebugSnapshot(TradeType startingType, List<Trade> trades, List<Position> closedPositions,
        Position currentPosition, List<OpenPosition> openPositions, OpenPosition netOpenPosition, Num totalFees) {

    TradingRecordDebugSnapshot {
        Objects.requireNonNull(startingType, "startingType");
        Objects.requireNonNull(totalFees, "totalFees");
        trades = trades == null ? List.of() : List.copyOf(trades);
        closedPositions = closedPositions == null ? List.of() : List.copyOf(closedPositions);
        openPositions = openPositions == null ? List.of() : List.copyOf(openPositions);
    }
}
