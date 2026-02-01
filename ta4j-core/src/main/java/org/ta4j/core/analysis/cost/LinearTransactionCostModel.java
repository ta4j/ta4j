/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.cost;

import org.ta4j.core.Position;
import org.ta4j.core.TradeView;
import org.ta4j.core.num.Num;

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
     *                       {@link TradeView trade})
     */
    public LinearTransactionCostModel(double feePerPosition) {
        this.feePerPosition = feePerPosition;
    }

    /**
     * @param position     the position
     * @param currentIndex current bar index (irrelevant for the
     *                     LinearTransactionCostModel)
     * @return the trading cost of the single {@code position}
     */
    @Override
    public Num calculate(Position position, int currentIndex) {
        return this.calculate(position);
    }

    @Override
    public Num calculate(Position position) {
        Num totalPositionCost = null;
        TradeView entryTrade = position.getEntry();
        if (entryTrade != null) {
            // transaction costs of the entry trade
            totalPositionCost = entryTrade.getCost();
            if (position.getExit() != null) {
                totalPositionCost = totalPositionCost.plus(position.getExit().getCost());
            }
        }
        return totalPositionCost;
    }

    @Override
    public Num calculate(Num price, Num amount) {
        return amount.getNumFactory().numOf(feePerPosition).multipliedBy(price).multipliedBy(amount);
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
