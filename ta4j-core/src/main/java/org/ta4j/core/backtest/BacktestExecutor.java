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
import java.util.stream.Collectors;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.Num;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.reports.TradingStatementGenerator;

/**
 * Allows backtesting multiple strategies and comparing them to find out which
 * is the best.
 */
public class BacktestExecutor {

    private final BarSeriesManager seriesManager;
    private final TradingStatementGenerator tradingStatementGenerator;

    /**
     * Constructor.
     *
     * @param series               the bar series
     * @param transactionCostModel the cost model for transactions of the asset
     * @param holdingCostModel     the cost model for holding the asset (e.g.
     *                             borrowing)
     */
    public BacktestExecutor(BarSeries series) {
        this(series, new TradingStatementGenerator());
    }

    public BacktestExecutor(BarSeries series, CostModel transactionCostModel, CostModel holdingCostModel,
            TradeExecutionModel tradeExecutionModel) {
        this(series, new TradingStatementGenerator(), transactionCostModel, holdingCostModel, tradeExecutionModel);
    }

    /**
     * Constructor.
     *
     * @param series                    the bar series
     * @param tradingStatementGenerator the TradingStatementGenerator
     */
    public BacktestExecutor(BarSeries series, TradingStatementGenerator tradingStatementGenerator) {
        this(series, tradingStatementGenerator, new ZeroCostModel(), new ZeroCostModel(), new TradeOnNextOpenModel());
    }

    /**
     * Constructor.
     *
     * @param series                    the bar series
     * @param tradingStatementGenerator the TradingStatementGenerator
     * @param transactionCostModel      the cost model for transactions of the asset
     * @param holdingCostModel          the cost model for holding the asset (e.g.
     *                                  borrowing)
     */
    public BacktestExecutor(BarSeries series, TradingStatementGenerator tradingStatementGenerator,
            CostModel transactionCostModel, CostModel holdingCostModel, TradeExecutionModel tradeExecutionModel) {
        this.seriesManager = new BarSeriesManager(series, transactionCostModel, holdingCostModel, tradeExecutionModel);
        this.tradingStatementGenerator = tradingStatementGenerator;
    }

    /**
     * Executes given strategies and returns trading statements with
     * {@code tradeType} (to open the position) = BUY.
     *
     * @param strategies the strategies
     * @param amount     the amount used to open/close the position
     * @return a list of TradingStatements
     */
    public List<TradingStatement> execute(List<Strategy> strategies, Num amount) {
        return execute(strategies, amount, Trade.TradeType.BUY);
    }

    /**
     * Executes given strategies with specified trade type to open the position and
     * return the trading statements.
     *
     * @param strategies the strategies
     * @param amount     the amount used to open/close the position
     * @param tradeType  the {@link Trade.TradeType} used to open the position
     * @return a list of TradingStatements
     */
    public List<TradingStatement> execute(List<Strategy> strategies, Num amount, Trade.TradeType tradeType) {
        return strategies.parallelStream().map(strategy -> {
            TradingRecord tradingRecord = seriesManager.run(strategy, tradeType, amount);
            return tradingStatementGenerator.generate(strategy, tradingRecord, seriesManager.getBarSeries());
        }).collect(Collectors.toList());
    }
}
