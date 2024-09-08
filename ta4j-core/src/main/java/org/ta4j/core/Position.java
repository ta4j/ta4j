/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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

import java.util.Objects;

import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.backtest.BacktestBarSeries;
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
public class Position {

  /** The entry trade */
  private Trade entry;

  /** The exit trade */
  private Trade exit;

  /** The type of the entry trade */
  private final TradeType startingType;

  /** The cost model for transactions of the asset */
  private final CostModel transactionCostModel;

  /** The cost model for holding the asset */
  private final CostModel holdingCostModel;


  /** Constructor with {@link #startingType} = BUY. */
  public Position() {
    this(TradeType.BUY);
  }


  /**
   * Constructor.
   *
   * @param startingType the starting {@link TradeType trade type} of the position
   *     (i.e. type of the entry trade)
   */
  public Position(final TradeType startingType) {
    this(startingType, new ZeroCostModel(), new ZeroCostModel());
  }


  /**
   * Constructor.
   *
   * @param startingType the starting {@link TradeType trade type} of the
   *     position (i.e. type of the entry trade)
   * @param transactionCostModel the cost model for transactions of the asset
   * @param holdingCostModel the cost model for holding asset (e.g. borrowing)
   */
  public Position(
      final TradeType startingType,
      final CostModel transactionCostModel,
      final CostModel holdingCostModel
  ) {
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
   * @param exit the exit {@link Trade trade}
   */
  public Position(final Trade entry, final Trade exit) {
    this(entry, exit, entry.getCostModel(), new ZeroCostModel());
  }


  /**
   * Constructor.
   *
   * @param entry the entry {@link Trade trade}
   * @param exit the exit {@link Trade trade}
   * @param transactionCostModel the cost model for transactions of the asset
   * @param holdingCostModel the cost model for holding asset (e.g. borrowing)
   */
  public Position(
      final Trade entry,
      final Trade exit,
      final CostModel transactionCostModel,
      final CostModel holdingCostModel
  ) {

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
    return this.entry;
  }


  /**
   * @return the exit {@link Trade trade} of the position
   */
  public Trade getExit() {
    return this.exit;
  }


  @Override
  public boolean equals(final Object obj) {
    if (obj instanceof final Position p) {
      return (this.entry == null ? p.getEntry() == null : this.entry.equals(p.getEntry()))
             && (this.exit == null ? p.getExit() == null : this.exit.equals(p.getExit()));
    }
    return false;
  }


  @Override
  public int hashCode() {
    return Objects.hash(this.entry, this.exit);
  }


  /**
   * Operates the position at the index-th position.
   *
   * @return the trade
   *
   * @see #operate(int, Num, Num)
   */
  public Trade operate(final int index) {
    return operate(index, NaN, NaN);
  }


  /**
   * Operates the position at the index-th position.
   *
   * @param price the price
   * @param amount the amount
   *
   * @return the trade
   *
   * @throws IllegalStateException if {@link #isOpened()}
   */
  public Trade operate(final int index, final Num price, final Num amount) {
    Trade trade = null;
    if (isNew()) {
      trade = new Trade(index, this.startingType, price, amount, this.transactionCostModel);
      this.entry = trade;
    } else if (isOpened()) {
      if (index < this.entry.getIndex()) {
        throw new IllegalStateException("The index i is less than the entryTrade index");
      }
      trade = new Trade(index, this.startingType.complementType(), price, amount, this.transactionCostModel);
      this.exit = trade;
    }
    return trade;
  }


  /**
   * @return true if the position is closed, false otherwise
   */
  public boolean isClosed() {
    return (this.entry != null) && (this.exit != null);
  }


  /**
   * @return true if the position is opened, false otherwise
   */
  public boolean isOpened() {
    return (this.entry != null) && (this.exit == null);
  }


  /**
   * @return true if the position is new, false otherwise
   */
  public boolean isNew() {
    return (this.entry == null) && (this.exit == null);
  }


  /**
   * @return true if position is closed and {@link #getProfit()} > 0
   */
  public boolean hasProfit() {
    return getProfit().isPositive();
  }


  /**
   * @return true if position is closed and {@link #getProfit()} < 0
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
      return getGrossProfit(this.exit.getPricePerAsset()).minus(getPositionCost());
    }
  }


  /**
   * Calculates the net profit of the position. If it is open, calculates the
   * profit until the final bar. The net profit includes any trading costs.
   *
   * @param finalIndex the index of the final bar to be considered (if position is
   *     open)
   * @param finalPrice the price of the final bar to be considered (if position is
   *     open)
   *
   * @return the profit or loss of the position
   */
  public Num getProfit(final int finalIndex, final Num finalPrice) {
    final Num grossProfit = getGrossProfit(finalPrice);
    final Num tradingCost = getPositionCost(finalIndex);
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
      return getGrossProfit(this.exit.getPricePerAsset());
    }
  }


  /**
   * Calculates the gross profit of the position. The gross profit excludes any
   * trading costs.
   *
   * @param finalPrice the price of the final bar to be considered (if position is
   *     open)
   *
   * @return the profit or loss of the position
   */
  public Num getGrossProfit(final Num finalPrice) {
    Num grossProfit;
    if (isOpened()) {
      grossProfit = this.entry.getAmount().multipliedBy(finalPrice).minus(this.entry.getValue());
    } else {
      grossProfit = this.exit.getValue().minus(this.entry.getValue());
    }

    // Profits of long position are losses of short
    if (this.entry.isSell()) {
      grossProfit = grossProfit.negate();
    }
    return grossProfit;
  }


  /**
   * Calculates the gross return of the position if it is closed. The gross return
   * excludes any trading costs (and includes the base).
   *
   * @return the gross return of the position in percent
   *
   * @see #getGrossReturn(Num)
   */
  public Num getGrossReturn() {
    if (isOpened()) {
      return zero();
    } else {
      return getGrossReturn(this.exit.getPricePerAsset());
    }
  }


  /**
   * Calculates the gross return of the position, if it exited at the provided
   * price. The gross return excludes any trading costs (and includes the base).
   *
   * @param finalPrice the price of the final bar to be considered (if position is
   *     open)
   *
   * @return the gross return of the position in percent
   *
   * @see #getGrossReturn(Num, Num)
   */
  public Num getGrossReturn(final Num finalPrice) {
    return getGrossReturn(getEntry().getPricePerAsset(), finalPrice);
  }


  /**
   * Calculates the gross return of the position. If either the entry or exit
   * price is {@code NaN}, the close price from given {@code barSeries} is used.
   * The gross return excludes any trading costs (and includes the base).
   *
   * @param barSeries
   *
   * @return the gross return in percent with entry and exit prices from the
   *     barSeries
   *
   * @see #getGrossReturn(Num, Num)
   */
  public Num getGrossReturn(final BacktestBarSeries barSeries) {
    final Num entryPrice = getEntry().getPricePerAsset(barSeries);
    final Num exitPrice = getExit().getPricePerAsset(barSeries);
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
   * @param exitPrice the exit price
   *
   * @return the gross return in percent between entryPrice and exitPrice
   *     (includes the base)
   */
  public Num getGrossReturn(final Num entryPrice, final Num exitPrice) {
    if (getEntry().isBuy()) {
      return exitPrice.dividedBy(entryPrice);
    } else {
      final Num one = entryPrice.getNumFactory().one();
      return ((exitPrice.dividedBy(entryPrice).minus(one)).negate()).plus(one);
    }
  }


  /**
   * Calculates the total cost of the position.
   *
   * @param finalIndex the index of the final bar to be considered (if position is
   *     open)
   *
   * @return the cost of the position
   */
  public Num getPositionCost(final int finalIndex) {
    final Num transactionCost = this.transactionCostModel.calculate(this, finalIndex);
    final Num borrowingCost = getHoldingCost(finalIndex);
    return transactionCost.plus(borrowingCost);
  }


  /**
   * Calculates the total cost of the closed position.
   *
   * @return the cost of the position
   */
  public Num getPositionCost() {
    final Num transactionCost = this.transactionCostModel.calculate(this);
    final Num borrowingCost = getHoldingCost();
    return transactionCost.plus(borrowingCost);
  }


  /**
   * Calculates the holding cost of the closed position.
   *
   * @return the cost of the position
   */
  public Num getHoldingCost() {
    return this.holdingCostModel.calculate(this);
  }


  /**
   * Calculates the holding cost of the position.
   *
   * @param finalIndex the index of the final bar to be considered (if position is
   *     open)
   *
   * @return the cost of the position
   */
  public Num getHoldingCost(final int finalIndex) {
    return this.holdingCostModel.calculate(this, finalIndex);
  }


  /**
   * @return the {@link #startingType}
   */
  public TradeType getStartingType() {
    return this.startingType;
  }


  /**
   * @return the Num of 0
   */
  private Num zero() {
    return this.entry.getNetPrice().getNumFactory().zero();
  }


  @Override
  public String toString() {
    return "Entry: " + this.entry + " exit: " + this.exit;
  }
}
