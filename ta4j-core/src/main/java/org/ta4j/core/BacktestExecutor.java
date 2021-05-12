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

import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.Num;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.reports.TradingStatementGenerator;

/**
 * This class enables backtesting of multiple strategies and comparing them to
 * see which is the best.
 */
public class BacktestExecutor {

    private final TradingStatementGenerator tradingStatementGenerator;
    private final BarSeriesManager seriesManager;

    public BacktestExecutor(BarSeries series) {
        this(series, new TradingStatementGenerator());
    }

    public BacktestExecutor(BarSeries series, CostModel transactionCostModel, CostModel holdingCostModel) {
        this(series, new TradingStatementGenerator(), transactionCostModel, holdingCostModel);
    }

    public BacktestExecutor(BarSeries series, TradingStatementGenerator tradingStatementGenerator) {
        this(series, tradingStatementGenerator, new ZeroCostModel(), new ZeroCostModel());
    }

    public BacktestExecutor(BarSeries series, TradingStatementGenerator tradingStatementGenerator,
            CostModel transactionCostModel, CostModel holdingCostModel) {
        this.seriesManager = new BarSeriesManager(series, transactionCostModel, holdingCostModel);
        this.tradingStatementGenerator = tradingStatementGenerator;
    }

    /**
     * Executes given strategies and returns trading statements.
     *
     * @param strategies the strategies
     * @param amount     the amount used to open/close the position
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
     */
    public List<TradingStatement> execute(List<Strategy> strategies, Num amount, Trade.TradeType tradeType) {
        final List<TradingStatement> tradingStatements = new ArrayList<>(strategies.size());
        for (Strategy strategy : strategies) {
            final TradingRecord tradingRecord = seriesManager.run(strategy, tradeType, amount);
            final TradingStatement tradingStatement = tradingStatementGenerator.generate(strategy, tradingRecord,
                    seriesManager.getBarSeries());
            tradingStatements.add(tradingStatement);
        }
        return tradingStatements;
    }
}
