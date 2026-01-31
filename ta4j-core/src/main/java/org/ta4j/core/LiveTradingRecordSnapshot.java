/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import com.google.gson.Gson;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Immutable snapshot of a live trading record.
 *
 * @param positions       closed positions
 * @param openPositions   open position lots
 * @param netOpenPosition aggregated open position
 * @param trades          recorded trades
 * @since 0.22.2
 */
public record LiveTradingRecordSnapshot(List<Position> positions, List<OpenPosition> openPositions,
        OpenPosition netOpenPosition, List<Trade> trades) implements Serializable {

    @Serial
    private static final long serialVersionUID = -7838090610321477417L;

    private static final Gson GSON = new Gson();

    public LiveTradingRecordSnapshot {
        positions = positions == null ? List.of() : List.copyOf(positions);
        openPositions = openPositions == null ? List.of() : List.copyOf(openPositions);
        trades = trades == null ? List.of() : List.copyOf(trades);
    }

    @Override
    public String toString() {
        return GSON.toJson(this);
    }
}
