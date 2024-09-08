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
package org.ta4j.core.backtest;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.Num;

/**
 * Base implementation of a {@link TradingRecord}.
 */
public class BackTestTradingRecord implements TradingRecord {

  /** The name of the trading record. */
  private String name;

  /** The start of the recording (included). */
  private final Integer startIndex;

  /** The end of the recording (included). */
  private final Integer endIndex;

  /** The recorded trades. */
  private final List<Trade> trades = new ArrayList<>();

  /** The recorded BUY trades. */
  private final List<Trade> buyTrades = new ArrayList<>();

  /** The recorded SELL trades. */
  private final List<Trade> sellTrades = new ArrayList<>();

  /** The recorded entry trades. */
  private final List<Trade> entryTrades = new ArrayList<>();

  /** The recorded exit trades. */
  private final List<Trade> exitTrades = new ArrayList<>();

  /** The entry type (BUY or SELL) in the trading session. */
  private final TradeType startingType;

  /** The recorded positions. */
  private final List<Position> positions = new ArrayList<>();

  /** The current non-closed position (there's always one). */
  private Position currentPosition;

  /** The cost model for transactions of the asset. */
  private final CostModel transactionCostModel;

  /** The cost model for holding asset (e.g. borrowing). */
  private final CostModel holdingCostModel;


  /** Constructor with {@link #startingType} = BUY. */
  public BackTestTradingRecord() {
    this(TradeType.BUY);
  }


  /**
   * Constructor with {@link #startingType} = BUY.
   *
   * @param name the name of the tradingRecord
   */
  public BackTestTradingRecord(final String name) {
    this(TradeType.BUY);
    this.name = name;
  }


  /**
   * Constructor.
   *
   * @param name the name of the trading record
   * @param tradeType the {@link TradeType trade type} of entries in the trading
   *     session
   */
  public BackTestTradingRecord(final String name, final TradeType tradeType) {
    this(tradeType, new ZeroCostModel(), new ZeroCostModel());
    this.name = name;
  }


  /**
   * Constructor.
   *
   * @param tradeType the {@link TradeType trade type} of entries in the trading
   *     session
   */
  public BackTestTradingRecord(final TradeType tradeType) {
    this(tradeType, new ZeroCostModel(), new ZeroCostModel());
  }


  /**
   * Constructor.
   *
   * @param entryTradeType the {@link TradeType trade type} of entries in
   *     the trading session
   * @param transactionCostModel the cost model for transactions of the asset
   * @param holdingCostModel the cost model for holding the asset (e.g.
   *     borrowing)
   */
  public BackTestTradingRecord(
      final TradeType entryTradeType,
      final CostModel transactionCostModel,
      final CostModel holdingCostModel
  ) {
    this(entryTradeType, null, null, transactionCostModel, holdingCostModel);
  }


  /**
   * Constructor.
   *
   * @param entryTradeType the {@link TradeType trade type} of entries in
   *     the trading session
   * @param startIndex the start of the recording (included)
   * @param endIndex the end of the recording (included)
   * @param transactionCostModel the cost model for transactions of the asset
   * @param holdingCostModel the cost model for holding the asset (e.g.
   *     borrowing)
   *
   * @throws NullPointerException if entryTradeType is null
   */
  public BackTestTradingRecord(
      final TradeType entryTradeType,
      final Integer startIndex,
      final Integer endIndex,
      final CostModel transactionCostModel,
      final CostModel holdingCostModel
  ) {
    this.startingType = Objects.requireNonNull(entryTradeType, "Starting type must not be null");
    this.startIndex = startIndex;
    this.endIndex = endIndex;
    this.transactionCostModel = transactionCostModel;
    this.holdingCostModel = holdingCostModel;
    this.currentPosition = new Position(entryTradeType, transactionCostModel, holdingCostModel);
  }


  /**
   * Constructor.
   *
   * @param trades the trades to be recorded (cannot be empty)
   */
  public BackTestTradingRecord(final Trade... trades) {
    this(new ZeroCostModel(), new ZeroCostModel(), trades);
  }


  /**
   * Constructor.
   *
   * @param transactionCostModel the cost model for transactions of the asset
   * @param holdingCostModel the cost model for holding the asset (e.g.
   *     borrowing)
   * @param trades the trades to be recorded (cannot be empty)
   */
  public BackTestTradingRecord(
      final CostModel transactionCostModel,
      final CostModel holdingCostModel,
      final Trade... trades
  ) {
    this(
        trades[0].getType(),
        trades[0].getIndex(),
        trades[trades.length - 1].getIndex(),
        transactionCostModel,
        holdingCostModel
    );
    for (final var trade : trades) {
      final boolean newTradeWillBeAnEntry = this.currentPosition.isNew();
      if (newTradeWillBeAnEntry && trade.getType() != this.startingType) {
        // Special case for entry/exit types reversal
        // E.g.: BUY, SELL,
        // BUY, SELL,
        // SELL, BUY,
        // BUY, SELL
        this.currentPosition = new Position(trade.getType(), transactionCostModel, holdingCostModel);
      }
      final var newTrade =
          this.currentPosition.operate(trade.getIndex(), trade.getPricePerAsset(), trade.getAmount());
      recordTrade(newTrade, newTradeWillBeAnEntry);
    }
  }


  @Override
  public String getName() {
    return this.name;
  }


  @Override
  public TradeType getStartingType() {
    return this.startingType;
  }


  @Override
  public Position getCurrentPosition() {
    return this.currentPosition;
  }


  @Override
  public void operate(final int index, final Num price, final Num amount) {
    if (this.currentPosition.isClosed()) {
      // Current position closed, should not occur
      throw new IllegalStateException("Current position should not be closed");
    }
    final boolean newTradeWillBeAnEntry = this.currentPosition.isNew();
    final Trade newTrade = this.currentPosition.operate(index, price, amount);
    recordTrade(newTrade, newTradeWillBeAnEntry);
  }


  @Override
  public boolean enter(final int index, final Num price, final Num amount) {
    if (this.currentPosition.isNew()) {
      operate(index, price, amount);
      return true;
    }
    return false;
  }


  @Override
  public boolean exit(final int index, final Num price, final Num amount) {
    if (this.currentPosition.isOpened()) {
      operate(index, price, amount);
      return true;
    }
    return false;
  }


  @Override
  public List<Position> getPositions() {
    return this.positions;
  }


  @Override
  public Trade getLastTrade() {
    if (!this.trades.isEmpty()) {
      return this.trades.getLast();
    }
    return null;
  }


  @Override
  public Trade getLastTrade(final TradeType tradeType) {
    if (TradeType.BUY == tradeType && !this.buyTrades.isEmpty()) {
      return this.buyTrades.getLast();
    } else if (TradeType.SELL == tradeType && !this.sellTrades.isEmpty()) {
      return this.sellTrades.getLast();
    }
    return null;
  }


  @Override
  public Trade getLastEntry() {
    if (!this.entryTrades.isEmpty()) {
      return this.entryTrades.getLast();
    }
    return null;
  }


  @Override
  public Trade getLastExit() {
    if (!this.exitTrades.isEmpty()) {
      return this.exitTrades.getLast();
    }
    return null;
  }


  @Override
  public Integer getStartIndex() {
    return this.startIndex;
  }


  @Override
  public Integer getEndIndex() {
    return this.endIndex;
  }


  @Override
  public boolean isEmpty() {
    return this.startIndex == null;
  }


  /**
   * Records a trade and the corresponding position (if closed).
   *
   * @param trade the trade to be recorded
   * @param isEntry true if the trade is an entry, false otherwise (exit)
   *
   * @throws NullPointerException if trade is null
   */
  private void recordTrade(final Trade trade, final boolean isEntry) {
    Objects.requireNonNull(trade, "Trade should not be null");

    // Storing the new trade in entries/exits lists
    if (isEntry) {
      this.entryTrades.add(trade);
    } else {
      this.exitTrades.add(trade);
    }

    // Storing the new trade in trades list
    this.trades.add(trade);
    if (TradeType.BUY == trade.getType()) {
      // Storing the new trade in buy trades list
      this.buyTrades.add(trade);
    } else if (TradeType.SELL == trade.getType()) {
      // Storing the new trade in sell trades list
      this.sellTrades.add(trade);
    }

    // Storing the position if closed
    if (this.currentPosition.isClosed()) {
      this.positions.add(this.currentPosition);
      this.currentPosition = new Position(this.startingType, this.transactionCostModel, this.holdingCostModel);
    }
  }


  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder().append("BackTestTradingRecord: ")
        .append(this.name == null ? "" : this.name)
        .append(System.lineSeparator());
    for (final var trade : this.trades) {
      sb.append(trade.toString()).append(System.lineSeparator());
    }
    return sb.toString();
  }
}
