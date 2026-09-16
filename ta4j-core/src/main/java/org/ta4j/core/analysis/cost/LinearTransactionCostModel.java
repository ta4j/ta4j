/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.cost;

import java.util.Objects;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradeFill;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * With this cost model, the trading costs for opening or closing a position
 * accrue linearly.
 */
public class LinearTransactionCostModel implements CostModel {

    /** The slope of the linear model (fee per position). */
    private final double feePerPosition;

    /**
     * Constructor with {@code feePerPosition * x}.
     *
     * @param feePerPosition the feePerPosition coefficient (e.g. 0.005 for 0.5% per
     *                       {@link Trade trade})
     */
    public LinearTransactionCostModel(double feePerPosition) {
        this.feePerPosition = feePerPosition;
    }

    /**
     * @param position     the position
     * @param currentIndex current bar index through which futures fills are
     *                     included
     * @return the trading cost of the single {@code position}
     */
    @Override
    public Num calculate(Position position, int currentIndex) {
        Trade entryTrade = position.getEntry();
        if (entryTrade != null && entryTrade.getFuturesContract() != null) {
            return calculateFuturesPosition(position, currentIndex);
        }
        return this.calculate(position);
    }

    @Override
    public Num calculate(Position position) {
        Trade entryTrade = position.getEntry();
        if (entryTrade != null && entryTrade.getFuturesContract() != null) {
            return calculateFuturesPosition(position, Integer.MAX_VALUE);
        }
        Num totalPositionCost = null;
        if (entryTrade != null) {
            // transaction costs of the entry trade
            totalPositionCost = entryTrade.getCost();
            if (position.getExit() != null) {
                totalPositionCost = totalPositionCost.plus(position.getExit().getCost());
            }
        }
        return totalPositionCost;
    }

    private Num calculateFuturesPosition(Position position, int currentIndex) {
        Num totalPositionCost = calculateFuturesTradeCost(position.getEntry(), currentIndex);
        Trade exitTrade = position.getExit();
        if (exitTrade != null) {
            Num exitCost = calculateFuturesTradeCost(exitTrade, currentIndex);
            totalPositionCost = totalPositionCost.plus(totalPositionCost.getNumFactory().numOf(exitCost.getDelegate()));
        }
        return totalPositionCost;
    }

    private Num calculateFuturesTradeCost(Trade trade, int currentIndex) {
        NumFactory numFactory = trade.getPricePerAsset().getNumFactory();
        Num totalTradeCost = numFactory.zero();
        for (TradeFill fill : Trade.executionFillsOf(trade)) {
            if (fill.index() < 0 || fill.index() > currentIndex) {
                continue;
            }
            Num fillCost = fill.hasRecordedFees() ? fill.fee() : calculate(fill);
            totalTradeCost = totalTradeCost.plus(numFactory.numOf(fillCost.getDelegate()));
        }
        return totalTradeCost;
    }

    @Override
    public Num calculate(Num price, Num amount) {
        return amount.getNumFactory().numOf(feePerPosition).multipliedBy(price).multipliedBy(amount);
    }

    /**
     * Applies {@link #feePerPosition} to the settlement notional of a native
     * execution fill.
     *
     * <p>
     * The linear model is a rate on the traded notional; a futures fill trades the
     * contract settlement notional, so an inverse contract is priced on
     * {@code contracts * contractSize / price} rather than on the base quantity.
     * </p>
     *
     * @param fill the execution fill
     * @return the trading cost of {@code fill}
     * @since 0.25.1
     */
    @Override
    public Num calculate(TradeFill fill) {
        Objects.requireNonNull(fill, "fill");
        FuturesContract contract = fill.futuresContract();
        if (contract == null) {
            return calculate(fill.price(), fill.amount());
        }
        Num rate = fill.price().getNumFactory().numOf(feePerPosition);
        return contract.settlementNotional(fill.amount(), fill.price()).multipliedBy(rate);
    }

    @Override
    public boolean equals(CostModel otherModel) {
        boolean equality = false;
        if (this.getClass().equals(otherModel.getClass())) {
            equality = ((LinearTransactionCostModel) otherModel).feePerPosition == this.feePerPosition;
        }
        return equality;
    }
}
