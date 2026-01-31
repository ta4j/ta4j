/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

/**
 * Execution side for live fills.
 *
 * @since 0.22.2
 */
public enum ExecutionSide {

    BUY, SELL;

    /**
     * @return the corresponding {@link Trade.TradeType}
     * @since 0.22.2
     */
    public Trade.TradeType toTradeType() {
        return this == BUY ? Trade.TradeType.BUY : Trade.TradeType.SELL;
    }
}
