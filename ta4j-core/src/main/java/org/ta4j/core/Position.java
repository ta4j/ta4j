/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core;

import static org.ta4j.core.num.NaN.NaN;

import java.io.Serializable;
import java.util.Objects;

import org.ta4j.core.Order.OrderType;
import org.ta4j.core.cost.CostModel;
import org.ta4j.core.cost.ZeroCostModel;
import org.ta4j.core.num.Num;

/**
 * Pair of two {@link Order orders}.
 *
 * The exit order has the complement type of the entry order.<br>
 * I.e.: entry == BUY --> exit == SELL entry == SELL --> exit == BUY
 */
public class Position implements Serializable {

    private static final long serialVersionUID = -5484709075767220358L;

    /** The entry order */
    private Order entry;

    /** The exit order */
    private Order exit;

    /** The type of the entry order */
    private OrderType startingType;

    /** The cost model for transactions of the asset */
    private CostModel transactionCostModel;

    /** The cost model for holding the asset */
    private CostModel holdingCostModel;

    /**
     * Constructor.
     */
    public Position() {
        this(OrderType.BUY);
    }

    /**
     * Constructor.
     * 
     * @param startingType the starting {@link OrderType order type} of the position
     *                     (i.e. type of the entry order)
     */
    public Position(OrderType startingType) {
        this(startingType, new ZeroCostModel(), new ZeroCostModel());
    }

    /**
     * Constructor.
     * 
     * @param startingType         the starting {@link OrderType order type} of the
     *                             position (i.e. type of the entry order)
     * @param transactionCostModel the cost model for transactions of the asset
     * @param holdingCostModel     the cost model for holding asset (e.g. borrowing)
     */
    public Position(OrderType startingType, CostModel transactionCostModel, CostModel holdingCostModel) {
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
     * @param entry the entry {@link Order order}
     * @param exit  the exit {@link Order order}
     */
    public Position(Order entry, Order exit) {
        this(entry, exit, entry.getCostModel(), new ZeroCostModel());
    }

    /**
     * Constructor.
     * 
     * @param entry                the entry {@link Order order}
     * @param exit                 the exit {@link Order order}
     * @param transactionCostModel the cost model for transactions of the asset
     * @param holdingCostModel     the cost model for holding asset (e.g. borrowing)
     */
    public Position(Order entry, Order exit, CostModel transactionCostModel, CostModel holdingCostModel) {

        if (entry.getType().equals(exit.getType())) {
            throw new IllegalArgumentException("Both orders must have different types");
        }

        if (!(entry.getCostModel().equals(transactionCostModel))
                || !(exit.getCostModel().equals(transactionCostModel))) {
            throw new IllegalArgumentException("Orders and the position must incorporate the same trading cost model");
        }

        this.startingType = entry.getType();
        this.entry = entry;
        this.exit = exit;
        this.transactionCostModel = transactionCostModel;
        this.holdingCostModel = holdingCostModel;
    }

    /**
     * @return the entry {@link Order order} of the position
     */
    public Order getEntry() {
        return entry;
    }

    /**
     * @return the exit {@link Order order} of the position
     */
    public Order getExit() {
        return exit;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Position) {
            Position p = (Position) obj;
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
     * Operates the position at the index-th position
     * 
     * @param index the bar index
     * @return the order
     */
    public Order operate(int index) {
        return operate(index, NaN, NaN);
    }

    /**
     * Operates the position at the index-th position
     * 
     * @param index  the bar index
     * @param price  the price
     * @param amount the amount
     * @return the order
     */
    public Order operate(int index, Num price, Num amount) {
        Order order = null;
        if (isNew()) {
            order = new Order(index, startingType, price, amount, transactionCostModel);
            entry = order;
        } else if (isOpened()) {
            if (index < entry.getIndex()) {
                throw new IllegalStateException("The index i is less than the entryOrder index");
            }
            order = new Order(index, startingType.complementType(), price, amount, transactionCostModel);
            exit = order;
        }
        return order;
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

    @Override
    public String toString() {
        return "Entry: " + entry + " exit: " + exit;
    }

    /**
     * Calculate the profit of the position if it is closed
     *
     * @return the profit or loss of the position
     */
    public Num getProfit() {
        if (isOpened()) {
            return numOf(0);
        } else {
            return getGrossProfit(exit.getPricePerAsset()).minus(getPositionCost());
        }
    }

    /**
     * Calculate the profit of the position. If it is open, calculates the profit
     * until the final bar.
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
     * Calculate the gross return of the position if it is closed
     *
     * @return the gross return of the position
     */
    public Num getGrossReturn() {
        if (isOpened()) {
            return numOf(0);
        } else {
            return getGrossReturn(exit.getPricePerAsset());
        }
    }

    /**
     * Calculate the gross return of the position, if it exited at the provided
     * price.
     *
     * @param finalPrice the price of the final bar to be considered (if position is
     *                   open)
     * @return the gross return of the position
     */
    public Num getGrossReturn(Num finalPrice) {
        return getGrossReturn(getEntry().getPricePerAsset(), finalPrice);
    }

    /**
     * Returns the gross return of the position. If either the entry or the exit
     * price are <code>NaN</code>, the close price from the supplies
     * {@link BarSeries} is used.
     * 
     * @param barSeries
     * @return
     */
    public Num getGrossReturn(BarSeries barSeries) {
        Num entryPrice = getEntry().getPricePerAsset(barSeries);
        Num exitPrice = getExit().getPricePerAsset(barSeries);
        return getGrossReturn(entryPrice, exitPrice);
    }

    private Num getGrossReturn(Num entryPrice, Num exitPrice) {
        Num profitPerAsset;
        if (getEntry().isBuy()) {
            profitPerAsset = exitPrice.minus(entryPrice);
        } else {
            profitPerAsset = entryPrice.minus(exitPrice);
        }
        return profitPerAsset.dividedBy(entryPrice).plus(entryPrice.numOf(1));
    }

    /**
     * Calculate the gross return of the position if it is closed
     *
     * @return the gross return of the position
     */
    public Num getGrossProfit() {
        if (isOpened()) {
            return numOf(0);
        } else {
            return getGrossProfit(exit.getPricePerAsset());
        }
    }

    /**
     * Calculate the gross (w/o trading costs) profit of the position.
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
        if (entry.getType().equals(OrderType.SELL)) {
            grossProfit = grossProfit.multipliedBy(numOf(-1));
        }
        return grossProfit;
    }

    /**
     * Calculates the total cost of the position
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
     * Calculates the total cost of the closed position
     * 
     * @return the cost of the position
     */
    public Num getPositionCost() {
        Num transactionCost = transactionCostModel.calculate(this);
        Num borrowingCost = getHoldingCost();
        return transactionCost.plus(borrowingCost);
    }

    /**
     * Calculates the holding cost of the closed position
     * 
     * @return the cost of the position
     */
    public Num getHoldingCost() {
        return holdingCostModel.calculate(this);
    }

    /**
     * Calculates the holding cost of the position
     * 
     * @param finalIndex the index of the final bar to be considered (if position is
     *                   open)
     * @return the cost of the position
     */
    public Num getHoldingCost(int finalIndex) {
        return holdingCostModel.calculate(this, finalIndex);
    }

    private Num numOf(Number num) {
        return entry.getNetPrice().numOf(num);
    }
}
