/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.cost;

import org.ta4j.core.Position;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.Trade;
import org.ta4j.core.num.Num;

/**
 * With this cost model, the trading costs for borrowing a position accrue
 * linearly.
 *
 * <p>
 * By default borrowing costs apply to short positions only (the historical
 * behavior). Use {@link #LinearBorrowingCostModel(double, Applicability)} to
 * apply them to long positions, or to both sides.
 */
public class LinearBorrowingCostModel implements CostModel {

    /** Defines which position sides incur borrowing costs. */
    public enum Applicability {
        /** Borrowing costs apply to short positions only (default). */
        SHORT_ONLY,
        /** Borrowing costs apply to long positions only. */
        LONG_ONLY,
        /** Borrowing costs apply to both long and short positions. */
        BOTH
    }

    /** The slope of the linear model (fee per period). */
    private final double feePerPeriod;

    /** Which position sides incur borrowing costs. */
    private final Applicability applicability;

    /**
     * Constructor with {@code feePerPeriod * nPeriod}. Borrowing costs apply to
     * short positions only (backward-compatible default).
     *
     * @param feePerPeriod the coefficient (e.g. 0.0001 for 1bp per period)
     */
    public LinearBorrowingCostModel(double feePerPeriod) {
        this(feePerPeriod, Applicability.SHORT_ONLY);
    }

    /**
     * Constructor with {@code feePerPeriod * nPeriod}.
     *
     * @param feePerPeriod  the coefficient (e.g. 0.0001 for 1bp per period)
     * @param applicability which position sides incur borrowing costs
     */
    public LinearBorrowingCostModel(double feePerPeriod, Applicability applicability) {
        this.feePerPeriod = feePerPeriod;
        this.applicability = applicability;
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

        // Borrowing costs apply according to the configured applicability.
        if (entryTrade != null && entryTrade.getAmount() != null && appliesTo(entryTrade.getType())) {
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
     * @param type the entry {@link TradeType} of the position
     * @return {@code true} if borrowing costs apply to a position entered with this
     *         trade type
     */
    private boolean appliesTo(TradeType type) {
        return switch (applicability) {
        case BOTH -> true;
        case LONG_ONLY -> type == TradeType.BUY;
        case SHORT_ONLY -> type == TradeType.SELL;
        };
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
            LinearBorrowingCostModel other = (LinearBorrowingCostModel) otherModel;
            equality = other.feePerPeriod == this.feePerPeriod && other.applicability == this.applicability;
        }
        return equality;
    }
}
