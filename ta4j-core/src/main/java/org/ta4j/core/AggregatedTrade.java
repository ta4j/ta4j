/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.util.List;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;

/**
 * Simulated trade built from multiple execution fills.
 *
 * <p>
 * The exposed trade amount is the sum of all fills and the trade price is the
 * volume-weighted average execution price.
 * </p>
 *
 * @since 0.22.4
 */
public class AggregatedTrade extends SimulatedTrade {

    /**
     * Creates an aggregated trade with zero transaction costs.
     *
     * @param type  trade type
     * @param fills execution fills (must not be empty)
     * @since 0.22.4
     */
    public AggregatedTrade(TradeType type, List<TradeFill> fills) {
        this(type, fills, new ZeroCostModel());
    }

    /**
     * Creates an aggregated trade.
     *
     * @param type                 trade type
     * @param fills                execution fills (must not be empty)
     * @param transactionCostModel transaction cost model
     * @since 0.22.4
     */
    public AggregatedTrade(TradeType type, List<TradeFill> fills, CostModel transactionCostModel) {
        super(type, fills, transactionCostModel);
    }
}
