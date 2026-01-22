/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.cost;

import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.num.Num;

/**
 * With this cost model, the trading costs for borrowing a position (i.e.
 * selling a position short) accrue linearly.
 */
public class LinearBorrowingCostModel implements CostModel {

    /** The slope of the linear model (fee per period). */
    private final double feePerPeriod;

    /**
     * Constructor with {@code feePerPeriod * nPeriod}.
     *
     * @param feePerPeriod the coefficient (e.g. 0.0001 for 1bp per period)
     */
    public LinearBorrowingCostModel(double feePerPeriod) {
        this.feePerPeriod = feePerPeriod;
    }

    /**
     * @return always {@code 0}, as borrowing costs depend on borrowed period
     */
    @Override
    public Num calculate(Num price, Num amount) {
        return price.getNumFactory().zero();
    }

    /**
     * @return the borrowing cost of the closed {@code position}
     * @throws IllegalArgumentException if {@code position} is still open
     */
    @Override
    public Num calculate(Position position) {
        if (position.isOpened()) {
            throw new IllegalArgumentException(
                    "Position is not closed. Final index of observation needs to be provided.");
        }
        return calculate(position, position.getExit().getIndex());
    }

    /**
     * @return the borrowing cost of the {@code position}
     */
    @Override
    public Num calculate(Position position, int currentIndex) {
        Trade entryTrade = position.getEntry();
        Trade exitTrade = position.getExit();
        Num borrowingCost = position.getEntry().getNetPrice().getNumFactory().zero();

        // Borrowing costs only apply to short positions.
        if (entryTrade != null && entryTrade.getType().equals(Trade.TradeType.SELL) && entryTrade.getAmount() != null) {
            int tradingPeriods = 0;
            if (position.isClosed()) {
                tradingPeriods = exitTrade.getIndex() - entryTrade.getIndex();
            } else if (position.isOpened()) {
                tradingPeriods = currentIndex - entryTrade.getIndex();
            }
            borrowingCost = getHoldingCostForPeriods(tradingPeriods, position.getEntry().getValue());
        }
        return borrowingCost;
    }

    /**
     * @param tradingPeriods the number of periods
     * @param tradedValue    the value of the initial trading position of the trade
     * @return the absolute borrowing cost
     */
    private Num getHoldingCostForPeriods(int tradingPeriods, Num tradedValue) {
        return tradedValue.multipliedBy(tradedValue.getNumFactory()
                .numOf(tradingPeriods)
                .multipliedBy(tradedValue.getNumFactory().numOf(feePerPeriod)));
    }

    @Override
    public boolean equals(CostModel otherModel) {
        boolean equality = false;
        if (this.getClass().equals(otherModel.getClass())) {
            equality = ((LinearBorrowingCostModel) otherModel).feePerPeriod == this.feePerPeriod;
        }
        return equality;
    }
}
