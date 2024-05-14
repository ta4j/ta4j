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

import java.util.List;

import org.ta4j.core.Trade;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.Num;
import org.ta4j.core.reports.TradingStatement;

/**
 * Allows backtesting multiple strategies and comparing them to find out which
 * is the best.
 */
public class BacktestExecutor {

    /** The managed bar series */
    private final BacktestBarSeries barSeries;

    /** The trading cost models */
    private final CostModel transactionCostModel;
    private final CostModel holdingCostModel;

    /** The trade execution model to use */
    private final TradeExecutionModel tradeExecutionModel;

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public BacktestExecutor(final BacktestBarSeries series) {
        this(series, new ZeroCostModel(), new ZeroCostModel(), new TradeOnCurrentCloseModel());
    }

    /**
     * Constructor.
     *
     * @param series               the bar series
     * @param transactionCostModel the cost model for transactions of the asset
     * @param holdingCostModel     the cost model for holding the asset (e.g.
     *                             borrowing)
     */
    // TODO builder
    public BacktestExecutor(final BacktestBarSeries series, final CostModel transactionCostModel,
            final CostModel holdingCostModel, final TradeExecutionModel tradeExecutionModel) {
        this.barSeries = series;
        this.transactionCostModel = transactionCostModel;
        this.holdingCostModel = holdingCostModel;
        this.tradeExecutionModel = tradeExecutionModel;
    }

    /**
     * Executes given strategies and returns trading statements with
     * {@code tradeType} (to open the position) = BUY.
     *
     * @return a list of TradingStatements
     */
    public List<TradingStatement> execute(final BacktestStrategy strategy) {
        return execute(strategy, this.barSeries.numFactory().one(), Trade.TradeType.BUY);
    }

    /**
     * Executes given strategies and returns trading statements with
     * {@code tradeType} (to open the position) = BUY.
     *
     * @return a list of TradingStatements
     */
    public List<TradingStatement> execute(final List<BacktestStrategy> strategies, final Trade.TradeType tradeType) {
        return execute(strategies, this.barSeries.numFactory().one(), tradeType);
    }

    /**
     * Executes given strategies and returns trading statements with
     * {@code tradeType} (to open the position) = BUY.
     *
     * @return a list of TradingStatements
     */
    public List<TradingStatement> execute(final BacktestStrategy strategy, final Trade.TradeType tradeType) {
        return execute(strategy, this.barSeries.numFactory().one(), tradeType);
    }

    /**
     * Executes given strategies and returns trading statements with
     * {@code tradeType} (to open the position) = BUY.
     *
     * @param strategies strategies to test
     * @param amount     the amount used to open/close the position
     *
     * @return a list of TradingStatements
     */
    public List<TradingStatement> execute(final List<BacktestStrategy> strategies, final Num amount) {
        return execute(strategies, amount, Trade.TradeType.BUY);
    }

    /**
     * Executes given strategies with specified trade type to open the position and
     * return the trading statements.
     *
     * @param strategy  to test
     * @param amount    the amount used to open/close the position
     * @param tradeType the {@link Trade.TradeType} used to open the position
     *
     * @return a list of TradingStatements
     */
    public List<TradingStatement> execute(final BacktestStrategy strategy, final Num amount,
            final Trade.TradeType tradeType) {
        return execute(List.of(strategy), amount, tradeType);
    }

    /**
     * Executes given strategies with specified trade type to open the position and
     * return the trading statements.
     *
     * @param strategies strategies to test
     * @param amount     the amount used to open/close the position
     * @param tradeType  the {@link Trade.TradeType} used to open the position
     *
     * @return a list of TradingStatements
     */
    public List<TradingStatement> execute(final List<BacktestStrategy> strategies, final Num amount,
            final Trade.TradeType tradeType) {
        this.barSeries.replaceStrategies(strategies);
        this.barSeries.rewind();
        return this.barSeries.replay(this.tradeExecutionModel, tradeType, amount, this.transactionCostModel,
                this.holdingCostModel);
    }

    public BacktestBarSeries getBarSeries() {
        return this.barSeries;
    }
}
