/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.ta4j.core.num.NaN.NaN;

import java.io.Serializable;
import java.util.Objects;

import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.Num;

/**
 * A {@code Position} is a pair of two {@link Trade trades}.
 *
 * <p>
 * The exit trade has the complement type of the entry trade, i.e.:
 * <ul>
 * <li>entry == BUY --> exit == SELL
 * <li>entry == SELL --> exit == BUY
 * </ul>
 */
public class Position implements Serializable {

    private static final long serialVersionUID = -5484709075767220358L;

    /** The entry trade */
    private Trade entry;

    /** The exit trade */
    private Trade exit;

    /** The type of the entry trade */
    private final TradeType startingType;

    /** The cost model for transactions of the asset */
    private final transient CostModel transactionCostModel;

    /** The cost model for holding the asset */
    private final transient CostModel holdingCostModel;

    /** Constructor with {@link #startingType} = BUY. */
    public Position() {
        this(TradeType.BUY);
    }

    /**
     * Constructor.
     *
     * @param startingType the starting {@link TradeType trade type} of the position
     *                     (i.e. type of the entry trade)
     */
    public Position(TradeType startingType) {
        this(startingType, new ZeroCostModel(), new ZeroCostModel());
    }

    /**
     * Constructor.
     *
     * @param startingType         the starting {@link TradeType trade type} of the
     *                             position (i.e. type of the entry trade)
     * @param transactionCostModel the cost model for transactions of the asset
     * @param holdingCostModel     the cost model for holding asset (e.g. borrowing)
     */
    public Position(TradeType startingType, CostModel transactionCostModel, CostModel holdingCostModel) {
        if (startingType == null) {
            throw new IllegalArgumentException("Starting type must not be null");
        }
        this.startingType = startingType;
        this.transactionCostModel = transactionCostModel;
        this.holdingCostModel = holdingCostModel;
    }

    /**
     * Constructor.
     *
     * @param entry the entry {@link Trade trade}
     * @param exit  the exit {@link Trade trade}
     */
    public Position(Trade entry, Trade exit) {
        this(entry, exit, entry.getCostModel(), new ZeroCostModel());
    }

    /**
     * Constructor.
     *
     * @param entry                the entry {@link Trade trade}
     * @param exit                 the exit {@link Trade trade}
     * @param transactionCostModel the cost model for transactions of the asset
     * @param holdingCostModel     the cost model for holding asset (e.g. borrowing)
     */
    public Position(Trade entry, Trade exit, CostModel transactionCostModel, CostModel holdingCostModel) {

        if (entry.getType().equals(exit.getType())) {
            throw new IllegalArgumentException("Both trades must have different types");
        }

        if (!(entry.getCostModel().equals(transactionCostModel))
                || !(exit.getCostModel().equals(transactionCostModel))) {
            throw new IllegalArgumentException("Trades and the position must incorporate the same trading cost model");
        }

        this.startingType = entry.getType();
        this.entry = entry;
        this.exit = exit;
        this.transactionCostModel = transactionCostModel;
        this.holdingCostModel = holdingCostModel;
    }

    /**
     * @return the entry {@link Trade trade} of the position
     */
    public Trade getEntry() {
        return entry;
    }

    /**
     * @return the exit {@link Trade trade} of the position
     */
    public Trade getExit() {
        return exit;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Position p) {
            return (entry == null ? p.getEntry() == null : entry.equals(p.getEntry()))
                    && (exit == null ? p.getExit() == null : exit.equals(p.getExit()));
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(entry, exit);
    }

    /**
     * Operates the position at the index-th position.
     *
     * @param index the bar index
     * @return the trade
     * @see #operate(int, Num, Num)
     */
    public Trade operate(int index) {
        return operate(index, NaN, NaN);
    }

    /**
     * Operates the position at the index-th position.
     *
     * @param index  the bar index
     * @param price  the price
     * @param amount the amount
     * @return the trade
     * @throws IllegalStateException if {@link #isOpened()} and index {@literal <}
     *                               entry.index
     */
    public Trade operate(int index, Num price, Num amount) {
        Trade trade = null;
        if (isNew()) {
            trade = new Trade(index, startingType, price, amount, transactionCostModel);
            entry = trade;
        } else if (isOpened()) {
            if (index < entry.getIndex()) {
                throw new IllegalStateException("The index i is less than the entryTrade index");
            }
            trade = new Trade(index, startingType.complementType(), price, amount, transactionCostModel);
            exit = trade;
        }
        return trade;
    }

    /**
     * @return true if the position is closed, false otherwise
     */
    public boolean isClosed() {
        return (entry != null) && (exit != null);
    }

    /**
     * @return true if the position is opened, false otherwise
     */
    public boolean isOpened() {
        return (entry != null) && (exit == null);
    }

    /**
     * @return true if the position is new, false otherwise
     */
    public boolean isNew() {
        return (entry == null) && (exit == null);
    }

    /**
     * @return true if position is closed and {@link #getProfit()} > 0
     */
    public boolean hasProfit() {
        return getProfit().isPositive();
    }

    /**
     * @return true if position is closed and {@link #getProfit()} {@literal <} 0
     */
    public boolean hasLoss() {
        return getProfit().isNegative();
    }

    /**
     * Calculates the net profit of the position if it is closed. The net profit
     * includes any trading costs.
     *
     * @return the profit or loss of the position
     */
    public Num getProfit() {
        if (isOpened()) {
            return zero();
        } else {
            return getGrossProfit(exit.getPricePerAsset()).minus(getPositionCost());
        }
    }

    /**
     * Calculates the net profit of the position. If it is open, calculates the
     * profit until the final bar. The net profit includes any trading costs.
     *
     * @param finalIndex the index of the final bar to be considered (if position is
     *                   open)
     * @param finalPrice the price of the final bar to be considered (if position is
     *                   open)
     * @return the profit or loss of the position
     */
    public Num getProfit(int finalIndex, Num finalPrice) {
        Num grossProfit = getGrossProfit(finalPrice);
        Num tradingCost = getPositionCost(finalIndex);
        return grossProfit.minus(tradingCost);
    }

    /**
     * Calculates the gross profit of the position if it is closed. The gross profit
     * excludes any trading costs.
     *
     * @return the gross profit of the position
     */
    public Num getGrossProfit() {
        if (isOpened()) {
            return zero();
        } else {
            return getGrossProfit(exit.getPricePerAsset());
        }
    }

    /**
     * Calculates the gross profit of the position. The gross profit excludes any
     * trading costs.
     *
     * @param finalPrice the price of the final bar to be considered (if position is
     *                   open)
     * @return the profit or loss of the position
     */
    public Num getGrossProfit(Num finalPrice) {
        Num grossProfit;
        if (isOpened()) {
            grossProfit = entry.getAmount().multipliedBy(finalPrice).minus(entry.getValue());
        } else {
            grossProfit = exit.getValue().minus(entry.getValue());
        }

        // Profits of long position are losses of short
        if (entry.isSell()) {
            grossProfit = grossProfit.negate();
        }
        return grossProfit;
    }

    /**
     * Calculates the gross return of the position if it is closed. The gross return
     * excludes any trading costs (and includes the base).
     *
     * @return the gross return of the position in percent
     * @see #getGrossReturn(Num)
     */
    public Num getGrossReturn() {
        if (isOpened()) {
            return zero();
        } else {
            return getGrossReturn(exit.getPricePerAsset());
        }
    }

    /**
     * Calculates the gross return of the position, if it exited at the provided
     * price. The gross return excludes any trading costs (and includes the base).
     *
     * @param finalPrice the price of the final bar to be considered (if position is
     *                   open)
     * @return the gross return of the position in percent
     * @see #getGrossReturn(Num, Num)
     */
    public Num getGrossReturn(Num finalPrice) {
        return getGrossReturn(getEntry().getPricePerAsset(), finalPrice);
    }

    /**
     * Calculates the gross return of the position. If either the entry or exit
     * price is {@code NaN}, the close price from given {@code barSeries} is used.
     * The gross return excludes any trading costs (and includes the base).
     *
     * @param barSeries
     * @return the gross return in percent with entry and exit prices from the
     *         barSeries
     * @see #getGrossReturn(Num, Num)
     */
    public Num getGrossReturn(BarSeries barSeries) {
        Num entryPrice = getEntry().getPricePerAsset(barSeries);
        Num exitPrice = getExit().getPricePerAsset(barSeries);
        return getGrossReturn(entryPrice, exitPrice);
    }

    /**
     * Calculates the gross return between entry and exit price in percent. Includes
     * the base.
     *
     * <p>
     * For example:
     * <ul>
     * <li>For buy position with a profit of 4%, it returns 1.04 (includes the base)
     * <li>For sell position with a loss of 4%, it returns 0.96 (includes the base)
     * </ul>
     *
     * @param entryPrice the entry price
     * @param exitPrice  the exit price
     * @return the gross return in percent between entryPrice and exitPrice
     *         (includes the base)
     */
    public Num getGrossReturn(Num entryPrice, Num exitPrice) {
        if (getEntry().isBuy()) {
            return exitPrice.dividedBy(entryPrice);
        } else {
            Num one = entryPrice.getNumFactory().one();
            return ((exitPrice.dividedBy(entryPrice).minus(one)).negate()).plus(one);
        }
    }

    /**
     * Calculates the total cost of the position.
     *
     * @param finalIndex the index of the final bar to be considered (if position is
     *                   open)
     * @return the cost of the position
     */
    public Num getPositionCost(int finalIndex) {
        Num transactionCost = transactionCostModel.calculate(this, finalIndex);
        Num borrowingCost = getHoldingCost(finalIndex);
        return transactionCost.plus(borrowingCost);
    }

    /**
     * Calculates the total cost of the closed position.
     *
     * @return the cost of the position
     */
    public Num getPositionCost() {
        Num transactionCost = transactionCostModel.calculate(this);
        Num borrowingCost = getHoldingCost();
        return transactionCost.plus(borrowingCost);
    }

    /**
     * Calculates the holding cost of the closed position.
     *
     * @return the cost of the position
     */
    public Num getHoldingCost() {
        return holdingCostModel.calculate(this);
    }

    /**
     * Calculates the holding cost of the position.
     *
     * @param finalIndex the index of the final bar to be considered (if position is
     *                   open)
     * @return the cost of the position
     */
    public Num getHoldingCost(int finalIndex) {
        return holdingCostModel.calculate(this, finalIndex);
    }

    /**
     * @return the {@link #startingType}
     */
    public TradeType getStartingType() {
        return startingType;
    }

    /**
     * @return the Num of 0
     */
    private Num zero() {
        return entry.getNetPrice().getNumFactory().zero();
    }

    @Override
    public String toString() {
        return "Entry: " + entry + " exit: " + exit;
    }
}
