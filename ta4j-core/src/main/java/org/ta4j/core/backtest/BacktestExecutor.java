/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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

import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.Num;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.reports.TradingStatementGenerator;

import java.time.Duration;
import java.util.*;
import java.util.stream.IntStream;

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
     * @param series the bar series
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
        return executeWithRuntimeReport(strategies, amount, tradeType).tradingStatements();
    }

    /**
     * Executes strategies while collecting runtime measurements and trading
     * statements.
     *
     * @param strategies the strategies
     * @param amount     the amount used to open/close the position
     * @return execution result with trading statements and runtime report
     *
     * @since 0.19
     */
    public BacktestExecutionResult executeWithRuntimeReport(List<Strategy> strategies, Num amount) {
        return executeWithRuntimeReport(strategies, amount, Trade.TradeType.BUY);
    }

    /**
     * Executes strategies while collecting runtime measurements and trading
     * statements.
     *
     * @param strategies the strategies
     * @param amount     the amount used to open/close the position
     * @param tradeType  the {@link Trade.TradeType} used to open the position
     * @return execution result with trading statements and runtime report
     *
     * @since 0.19
     */
    public BacktestExecutionResult executeWithRuntimeReport(List<Strategy> strategies, Num amount,
            Trade.TradeType tradeType) {
        Objects.requireNonNull(strategies, "strategies must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(tradeType, "tradeType must not be null");

        if (strategies.isEmpty()) {
            return new BacktestExecutionResult(new ArrayList<>(), BacktestRuntimeReport.empty());
        }

        Strategy[] strategyArray = strategies.toArray(Strategy[]::new);
        int strategyCount = strategyArray.length;
        TradingStatement[] statements = new TradingStatement[strategyCount];
        long[] durations = new long[strategyCount];

        long overallStart = System.nanoTime();

        IntStream indexStream = IntStream.range(0, strategyCount);
        if (strategyCount > 1) {
            indexStream = indexStream.parallel();
        }

        indexStream.forEach(index -> {
            Strategy strategy = strategyArray[index];
            long strategyStart = System.nanoTime();
            TradingRecord tradingRecord = seriesManager.run(strategy, tradeType, amount);
            TradingStatement statement = tradingStatementGenerator.generate(strategy, tradingRecord,
                    seriesManager.getBarSeries());
            statements[index] = statement;
            durations[index] = System.nanoTime() - strategyStart;
        });

        Duration overallRuntime = Duration.ofNanos(System.nanoTime() - overallStart);

        List<TradingStatement> tradingStatements = new ArrayList<>(strategyCount);
        for (TradingStatement statement : statements) {
            tradingStatements.add(statement);
        }

        List<BacktestRuntimeReport.StrategyRuntime> strategyRuntimes = new ArrayList<>(strategyCount);
        for (int i = 0; i < strategyCount; i++) {
            strategyRuntimes
                    .add(new BacktestRuntimeReport.StrategyRuntime(strategyArray[i], Duration.ofNanos(durations[i])));
        }

        BacktestRuntimeReport runtimeReport = buildRuntimeReport(durations, overallRuntime, strategyRuntimes);
        return new BacktestExecutionResult(tradingStatements, runtimeReport);
    }

    private BacktestRuntimeReport buildRuntimeReport(long[] durations, Duration overallRuntime,
            List<BacktestRuntimeReport.StrategyRuntime> strategyRuntimes) {
        LongSummaryStatistics summaryStatistics = Arrays.stream(durations).summaryStatistics();
        if (summaryStatistics.getCount() == 0) {
            return new BacktestRuntimeReport(overallRuntime, Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO,
                    strategyRuntimes);
        }

        long[] sortedDurations = durations.clone();
        Arrays.sort(sortedDurations);
        int midPoint = sortedDurations.length / 2;
        long medianNanos;
        if (sortedDurations.length % 2 == 0) {
            medianNanos = (sortedDurations[midPoint - 1] + sortedDurations[midPoint]) / 2;
        } else {
            medianNanos = sortedDurations[midPoint];
        }

        Duration min = Duration.ofNanos(summaryStatistics.getMin());
        Duration max = Duration.ofNanos(summaryStatistics.getMax());
        Duration average = Duration.ofNanos(Math.round(summaryStatistics.getAverage()));
        Duration median = Duration.ofNanos(medianNanos);

        return new BacktestRuntimeReport(overallRuntime, min, max, average, median, strategyRuntimes);
    }
}
