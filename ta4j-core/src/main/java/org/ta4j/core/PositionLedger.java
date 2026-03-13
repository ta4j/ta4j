/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.util.List;

/**
 * Deprecated compatibility view of closed and open positions.
 *
 * <p>
 * Use {@link TradingRecord#getPositions()},
 * {@link TradingRecord#getOpenPositions()}, and
 * {@link TradingRecord#getCurrentPosition()} directly in new code.
 * </p>
 *
 * @since 0.22.2
 */
@Deprecated(since = "0.22.4")
public interface PositionLedger {

    /**
     * @return the recorded closed positions
     * @since 0.22.2
     */
    List<Position> getPositions();

    /**
     * @return open positions
     * @since 0.22.2
     */
    List<Position> getOpenPositions();

    /**
     * @return the aggregated net open position
     * @since 0.22.2
     */
    Position getNetOpenPosition();
}
