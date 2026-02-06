/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.util.List;

/**
 * Shared view of closed positions and open positions.
 *
 * @since 0.22.2
 */
public interface PositionLedger {

    /**
     * @return the recorded closed positions
     * @since 0.22.2
     */
    List<Position> getPositions();

    /**
     * @return open positions as lots
     * @since 0.22.2
     */
    List<OpenPosition> getOpenPositions();

    /**
     * @return the aggregated net open position
     * @since 0.22.2
     */
    OpenPosition getNetOpenPosition();
}
