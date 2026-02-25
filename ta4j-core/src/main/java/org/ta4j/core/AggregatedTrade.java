/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.util.List;
import java.util.Objects;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.Num;

/**
 * Simulated trade built from multiple execution fills.
 *
 * <p>
 * The exposed trade amount is the sum of all fills and the trade price is the
 * volume-weighted average execution price.
 * </p>
 *
 * @since 0.22.2
 */
public class AggregatedTrade extends SimulatedTrade {

    private final List<TradeFill> fills;

    /**
     * Creates an aggregated trade with zero transaction costs.
     *
     * @param type  trade type
     * @param fills execution fills (must not be empty)
     * @since 0.22.2
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
     * @since 0.22.2
     */
    public AggregatedTrade(TradeType type, List<TradeFill> fills, CostModel transactionCostModel) {
        this(type, summarizeFills(fills), transactionCostModel);
    }

    private AggregatedTrade(TradeType type, FillSummary fillSummary, CostModel transactionCostModel) {
        super(fillSummary.firstFillIndex(), type, fillSummary.weightedAveragePrice(), fillSummary.totalAmount(),
                Objects.requireNonNull(transactionCostModel, "transactionCostModel"));
        this.fills = fillSummary.fills();
    }

    @Override
    public List<TradeFill> getFills() {
        return fills;
    }

    private static FillSummary summarizeFills(List<TradeFill> fills) {
        Objects.requireNonNull(fills, "fills");
        if (fills.isEmpty()) {
            throw new IllegalArgumentException("fills must not be empty");
        }
        Num totalAmount = fills.getFirst().amount().getNumFactory().zero();
        Num weightedPrice = fills.getFirst().price().getNumFactory().zero();
        for (TradeFill fill : fills) {
            if (fill.price().isNaN()) {
                throw new IllegalArgumentException("fill price must be set");
            }
            if (fill.amount().isNaN() || fill.amount().isZero() || fill.amount().isNegative()) {
                throw new IllegalArgumentException("fill amount must be positive");
            }
            totalAmount = totalAmount.plus(fill.amount());
            weightedPrice = weightedPrice.plus(fill.price().multipliedBy(fill.amount()));
        }
        return new FillSummary(List.copyOf(fills), fills.getFirst().index(), totalAmount,
                weightedPrice.dividedBy(totalAmount));
    }

    private record FillSummary(List<TradeFill> fills, int firstFillIndex, Num totalAmount, Num weightedAveragePrice) {
    }
}
