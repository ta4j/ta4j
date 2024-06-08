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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.Bar;
import org.ta4j.core.BarBuilderFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.reports.TradingStatementGenerator;

/**
 * Base implementation of a {@link BarSeries}.
 *
 * <ul>
 *  <li>constrained between beginning and ending indices (e.g. for some backtesting cases)
 *  <li>limited to a fixed number of bars (e.g. for actual trading)
 * </ul>
 */
public class BacktestBarSeries implements BarSeries {

  /** The name of the bar series. */
  private final String name;

  /** The list of bars of the bar series. */
  private final List<BacktestBar> bars = new ArrayList<>();
  private final BarBuilderFactory barBuilderFactory;
  private final List<BacktestStrategy> strategies = new ArrayList<>(1);

  private final NumFactory numFactory;

  /**
   * Where we are located now
   */
  private int currentBarIndex = -1;


  /**
   * Constructor.
   *
   * @param name the name of the bar series cannot change), false
   *     otherwise
   * @param numFactory the factory of numbers used in series {@link Num Num
   *     implementation}
   * @param barBuilderFactory factory for creating bars of this series
   */
  BacktestBarSeries(final String name, final NumFactory numFactory, final BarBuilderFactory barBuilderFactory) {
    this.name = name;
    this.numFactory = numFactory;

    this.barBuilderFactory = Objects.requireNonNull(barBuilderFactory);
  }


  /**
   * Cuts a list of bars into a new list of bars that is a subset of it.
   *
   * @param bars the list of {@link Bar bars}
   * @param startIndex start index of the subset
   * @param endIndex end index of the subset
   *
   * @return a new list of bars with tick from startIndex (inclusive) to endIndex
   *     (exclusive)
   */
  private static List<BacktestBar> cut(final List<BacktestBar> bars, final int startIndex, final int endIndex) {
    return new ArrayList<>(bars.subList(startIndex, endIndex));
  }


  /**
   * Returns a new {@link BarSeries} instance (= "subseries") that is a subset of
   * {@code this} BarSeries instance. It contains a copy of all {@link Bar bars}
   * between {@code startIndex} (inclusive) and {@code endIndex} (exclusive) of
   * {@code this} instance. The indices of {@code this} and its subseries can be
   * different, i. e. index 0 of the subseries will be the {@code startIndex} of
   * {@code this}. If {@code startIndex} < this.seriesBeginIndex, then the
   * subseries will start with the first available bar of {@code this}. If
   * {@code endIndex} > this.seriesEndIndex, then the subseries will end at the
   * last available bar of {@code this}.
   *
   * @param startIndex the startIndex (inclusive)
   * @param endIndex the endIndex (exclusive)
   *
   * @return a new BarSeries with Bars from startIndex to endIndex-1
   *
   * @throws IllegalArgumentException if endIndex <= startIndex or startIndex < 0
   */
  public BacktestBarSeries getSubSeries(final int startIndex, final int endIndex) {
    if (startIndex < 0) {
      throw new IllegalArgumentException(String.format("the startIndex: %s must not be negative", startIndex));
    }
    if (startIndex >= endIndex) {
      throw new IllegalArgumentException(
          String.format("the endIndex: %s must be greater than startIndex: %s", endIndex, startIndex));
    }
    if (!this.bars.isEmpty()) {
      final int start = startIndex;
      final int end = this.getEndIndex() + 1;
      return new BacktestBarSeriesBuilder().withName(getName())
          .withBars(cut(this.bars, start, end))
          .withNumFactory(this.numFactory)
          .build();
    }
    return new BacktestBarSeriesBuilder().withNumFactory(this.numFactory).withName(getName()).build();

  }


  /**
   * Advances time to next bar.
   *
   * Notifies strategies to refresh their state
   *
   * @return true if advanced to next bar
   */
  public boolean advance() {
    if (canAdvance()) {
      ++this.currentBarIndex;

      for (final var strategy : this.strategies) {
        strategy.refresh(getBar().endTime());
      }
      return true;
    }

    return false;
  }


  private boolean canAdvance() {
    return this.currentBarIndex < getEndIndex();
  }


  @Override
  public NumFactory numFactory() {
    return this.numFactory;
  }


  @Override
  public BacktestBarBuilder barBuilder() {
    return (BacktestBarBuilder) this.barBuilderFactory.createBarBuilder(this);
  }


  @Override
  public String getName() {
    return this.name;
  }


  @Override
  public BacktestBar getBar() {
    return this.bars.get(this.currentBarIndex);
  }


  public void addBar(final Bar bar) {
    addBar(bar, false);
  }


  public Bar getBar(final int index) {
    return this.bars.get(index);
  }


  /**
   * @return the number of bars in the series
   */
  public int getBarCount() {
    return this.bars.size();
  }


  /**
   * @return true if the series is empty, false otherwise
   */
  public boolean isEmpty() {
    return getBarCount() == 0;
  }


  /**
   * Returns the raw bar data, i.e. it returns the current list object, which is
   * used internally to store the {@link Bar bars}. It may be:
   *
   * <ul>
   * <li>a shortened bar list if a {@code maximumBarCount} has been set.
   * <li>an extended bar list if it is a constrained bar series.
   * </ul>
   *
   * <p>
   * <b>Warning:</b> This method should be used carefully!
   *
   * @return the raw bar data
   */
  public List<BacktestBar> getBarData() {
    return this.bars;
  }


  /**
   * @return the begin index of the series
   */
  public int getBeginIndex() {
    return 0;
  }


  /**
   * @return the end index of the series
   */
  public int getEndIndex() {
    return this.bars.size() - 1;
  }


  /**
   * Adds the {@code bar} at the end of the series.
   *
   * <p>
   * The {@code beginIndex} is set to {@code 0} if not already initialized.<br>
   * The {@code endIndex} is set to {@code 0} if not already initialized, or
   * incremented if it matches the end of the series.<br>
   * Exceeding bars are removed.
   *
   * @param bar the bar to be added
   * @param replace true to replace the latest bar. Some exchanges continuously
   *     provide new bar data in the respective period, e.g. 1 second
   *     in 1 minute duration.
   *
   * @throws NullPointerException if {@code bar} is {@code null}
   */
  public void addBar(final Bar bar, final boolean replace) {
    Objects.requireNonNull(bar, "bar must not be null");
    if (!this.numFactory.produces(bar.closePrice())) {
      throw new IllegalArgumentException(
          String.format("Cannot add Bar with data type: %s to series with datatype: %s",
              bar.closePrice().getClass(), this.numFactory.one().getClass()
          ));
    }

    if (!(bar instanceof BacktestBar)) {
      throw new IllegalArgumentException("Wrong bar type: " + bar.closePrice().getName());
    }

    if (!this.bars.isEmpty()) {
      if (replace) {
        this.bars.set(this.bars.size() - 1, (BacktestBar) bar);
        return;
      }
      final int lastBarIndex = this.bars.size() - 1;
      final Instant seriesEndTime = this.bars.get(lastBarIndex).endTime();
      if (!bar.endTime().isAfter(seriesEndTime)) {
        throw new IllegalArgumentException(
            String.format("Cannot add a bar with end time:%s that is <= to series end time: %s",
                bar.endTime(), seriesEndTime
            ));
      }
    }

    this.bars.add((BacktestBar) bar);
  }


  /**
   * Adds a trade and updates the close price of the last bar.
   *
   * @param amount the traded volume
   * @param price the price
   *
   * @see BacktestBar#addTrade(Num, Num)
   */
  public void addTrade(final Number price, final Number amount) {
    addTrade(numFactory().numOf(price), numFactory().numOf(amount));
  }


  /**
   * Adds a trade and updates the close price of the last bar.
   *
   * @param tradeVolume the traded volume
   * @param tradePrice the price
   *
   * @see BacktestBar#addTrade(Num, Num)
   */
  public void addTrade(final Num tradeVolume, final Num tradePrice) {
    getLastBar().addTrade(tradeVolume, tradePrice);
  }


  private BacktestBar getLastBar() {
    return this.bars.getLast();
  }


  private BacktestBar getFirstBar() {
    return this.bars.getFirst();
  }


  /**
   * Updates the close price of the last bar. The open, high and low prices are
   * also updated as needed.
   *
   * @param price the price for the bar
   *
   * @see BacktestBar#addPrice(Num)
   */
  public void addPrice(final Num price) {
    getLastBar().addPrice(price);
  }


  public List<TradingStatement> replay(
      final TradeExecutionModel tradeExecutionModel, final Trade.TradeType tradeType,
      final Num amount, final CostModel transactionCostModel, final CostModel holdingCostMOdel
  ) {
    if (!canAdvance()) {
      throw new IllegalArgumentException("Cannot advance further. Rewind bar series before replaying.");
    }
    this.strategies
        .forEach(strategy -> strategy
            .register(new BackTestTradingRecord(tradeType, transactionCostModel, holdingCostMOdel)));

    while (advance()) {
      // execute strategy for each bar until series is at the end
      this.strategies.parallelStream().forEach(strategy -> {

        if (strategy.shouldOperate()) {
          tradeExecutionModel.execute(this.currentBarIndex, strategy.getTradeRecord(), this, amount);
        }
      });
    }

    return this.strategies.stream()
        .map(strategy -> new TradingStatementGenerator().generate(strategy, this))
        .toList();
  }


  public int getCurrentIndex() {
    return this.currentBarIndex;
  }


  public void addStrategy(final BacktestStrategy strategy) {
    this.strategies.add(strategy);
  }


  @Override
  public void replaceStrategy(final Strategy strategy) {
    this.strategies.clear();
    this.strategies.add((BacktestStrategy) strategy);
  }


  public void replaceStrategies(final List<BacktestStrategy> strategies) {
    this.strategies.clear();
    this.strategies.addAll(strategies);
  }


  public void rewind() {
    this.currentBarIndex = -1;
  }


  /**
   * @return the description of the series period (e.g. "from 12:00 21/01/2014 to
   *     12:15 21/01/2014")
   */
  public String getSeriesPeriodDescription() {
    final StringBuilder sb = new StringBuilder();
    if (!getBarData().isEmpty()) {
      final Bar firstBar = getFirstBar();
      final Bar lastBar = getLastBar();
      sb.append(firstBar.endTime().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_DATE_TIME))
          .append(" - ")
          .append(lastBar.endTime().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_DATE_TIME));
    }
    return sb.toString();
  }
}
