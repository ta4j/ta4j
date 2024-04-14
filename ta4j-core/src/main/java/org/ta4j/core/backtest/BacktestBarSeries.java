/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.BacktestBarConvertibleBuilder;
import org.ta4j.core.Bar;
import org.ta4j.core.BarBuilderFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.reports.TradingStatementGenerator;

/**
 * Base implementation of a {@link BarSeries}.
 */
public class BacktestBarSeries implements BarSeries {

    private static final long serialVersionUID = -1878027009398790126L;

    /** The logger. */
    private final transient Logger log = LoggerFactory.getLogger(getClass());

    /** The name of the bar series. */
    private final String name;

    /** The list of bars of the bar series. */
    private final List<Bar> bars = new ArrayList<>();
    private final BarBuilderFactory barBuilderFactory;
    private final List<BacktestStrategy> strategies;

    private final NumFactory numFactory;

    /**
     * Where we are located now
     */
    private int currentBarIndex = -1;

    /**
     * Constructor.
     *
     * @param name              the name of the bar series cannot change), false
     *                          otherwise
     * @param numFactory        the factory of numbers used in series {@link Num Num
     *                          implementation}
     * @param barBuilderFactory factory for creating bars of this series
     * @param strategies        strategies to backtest
     */
    BacktestBarSeries(final String name, final NumFactory numFactory, final BarBuilderFactory barBuilderFactory,
            final List<Function<BacktestBarSeries, BacktestStrategy>> strategies) {
        this.name = name;
        this.numFactory = numFactory;

        this.barBuilderFactory = Objects.requireNonNull(barBuilderFactory);
        this.strategies = strategies.stream().map(strategy -> strategy.apply(this)).collect(Collectors.toList());
    }

    /**
     * Cuts a list of bars into a new list of bars that is a subset of it.
     *
     * @param bars       the list of {@link Bar bars}
     * @param startIndex start index of the subset
     * @param endIndex   end index of the subset
     * @return a new list of bars with tick from startIndex (inclusive) to endIndex
     *         (exclusive)
     */
    private static List<Bar> cut(final List<Bar> bars, final int startIndex, final int endIndex) {
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
     * @param endIndex   the endIndex (exclusive)
     * @return a new BarSeries with Bars from startIndex to endIndex-1
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

    @Override
    public boolean advance() {
        if (canAdvance()) {
            ++this.currentBarIndex;
            this.strategies.forEach(Strategy::refresh);
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
    public BacktestBarConvertibleBuilder barBuilder() {
        return this.barBuilderFactory.createBarBuilder(this);
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Bar getBar() {
        return this.bars.get(this.currentBarIndex);
    }

    public Bar getBar(final int index) {
        return this.bars.get(index);
    }

    @Override
    public int getBarCount() {
        return this.bars.size();
    }

    @Override
    public List<Bar> getBarData() {
        return this.bars;
    }

    @Override
    public int getBeginIndex() {
        return 0;
    }

    @Override
    public int getEndIndex() {
        return this.bars.size() - 1;
    }

    /**
     * @throws NullPointerException if {@code bar} is {@code null}
     */
    @Override
    public void addBar(final Bar bar, final boolean replace) {
        Objects.requireNonNull(bar, "bar must not be null");
        if (!this.numFactory.produces(bar.getClosePrice())) {
            throw new IllegalArgumentException(
                    String.format("Cannot add Bar with data type: %s to series with datatype: %s",
                            bar.getClosePrice().getClass(), this.numFactory.one().getClass()));
        }

        if (!this.bars.isEmpty()) {
            if (replace) {
                this.bars.set(this.bars.size() - 1, bar);
                return;
            }
            final int lastBarIndex = this.bars.size() - 1;
            final ZonedDateTime seriesEndTime = this.bars.get(lastBarIndex).getEndTime();
            if (!bar.getEndTime().isAfter(seriesEndTime)) {
                throw new IllegalArgumentException(
                        String.format("Cannot add a bar with end time:%s that is <= to series end time: %s",
                                bar.getEndTime(), seriesEndTime));
            }
        }

        this.bars.add(bar);
    }

    @Override
    public void addTrade(final Number price, final Number amount) {
        addTrade(numFactory().numOf(price), numFactory().numOf(amount));
    }

    @Override
    public void addTrade(final Num tradeVolume, final Num tradePrice) {
        getLastBar().addTrade(tradeVolume, tradePrice);
    }

    @Override
    public void addPrice(final Num price) {
        getLastBar().addPrice(price);
    }

    public List<TradingStatement> replay(final TradeExecutionModel tradeExecutionModel, final Trade.TradeType tradeType,
            final Num amount, final CostModel transactionCostModel, final CostModel holdingCostMOdel) {
        if (!canAdvance()) {
            throw new IllegalArgumentException("Cannot advance further. Rewind bar series before replaying.");
        }
        this.strategies.stream()
                .forEach(strategy -> strategy
                        .register(new BaseTradingRecord(tradeType, transactionCostModel, holdingCostMOdel)));

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

    public void replaceStrategy(final BacktestStrategy strategy) {
        strategies.clear();
        strategies.add(strategy);
    }

    public void replaceStrategies(final List<BacktestStrategy> strategies) {
        this.strategies.clear();
        this.strategies.addAll(strategies);
    }

    public void rewind() {
        this.currentBarIndex = -1;
    }
}
