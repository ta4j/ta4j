/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.Num;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.reports.TradingStatementGenerator;
import org.ta4j.core.walkforward.AnchoredExpandingWalkForwardSplitter;
import org.ta4j.core.walkforward.WalkForwardConfig;
import org.ta4j.core.walkforward.WalkForwardRuntimeReport;
import org.ta4j.core.walkforward.WalkForwardSplit;
import org.ta4j.core.walkforward.WalkForwardSplitter;

/**
 * Executes one strategy in walk-forward mode with a backtest-symmetric API.
 *
 * @since 0.22.4
 */
public class StrategyWalkForwardExecutor {

    private final BarSeriesManager seriesManager;
    private final TradingStatementGenerator tradingStatementGenerator;
    private final WalkForwardSplitter splitter;

    /**
     * Creates an executor with default cost and trade execution models.
     *
     * @param series input bar series
     * @since 0.22.4
     */
    public StrategyWalkForwardExecutor(BarSeries series) {
        this(series, new TradingStatementGenerator(), new ZeroCostModel(), new ZeroCostModel(),
                new TradeOnNextOpenModel());
    }

    /**
     * Creates an executor with explicit cost and trade execution models.
     *
     * @param series               input bar series
     * @param transactionCostModel transaction cost model
     * @param holdingCostModel     holding cost model
     * @param tradeExecutionModel  trade execution model
     * @since 0.22.4
     */
    public StrategyWalkForwardExecutor(BarSeries series, CostModel transactionCostModel, CostModel holdingCostModel,
            TradeExecutionModel tradeExecutionModel) {
        this(series, new TradingStatementGenerator(), transactionCostModel, holdingCostModel, tradeExecutionModel);
    }

    /**
     * Creates an executor with explicit statement generator.
     *
     * @param series                    input bar series
     * @param tradingStatementGenerator trading statement generator
     * @since 0.22.4
     */
    public StrategyWalkForwardExecutor(BarSeries series, TradingStatementGenerator tradingStatementGenerator) {
        this(series, tradingStatementGenerator, new ZeroCostModel(), new ZeroCostModel(), new TradeOnNextOpenModel());
    }

    /**
     * Creates an executor with explicit statement generator, cost models, and trade
     * execution model.
     *
     * @param series                    input bar series
     * @param tradingStatementGenerator trading statement generator
     * @param transactionCostModel      transaction cost model
     * @param holdingCostModel          holding cost model
     * @param tradeExecutionModel       trade execution model
     * @since 0.22.4
     */
    public StrategyWalkForwardExecutor(BarSeries series, TradingStatementGenerator tradingStatementGenerator,
            CostModel transactionCostModel, CostModel holdingCostModel, TradeExecutionModel tradeExecutionModel) {
        this(new BarSeriesManager(series, transactionCostModel, holdingCostModel, tradeExecutionModel),
                tradingStatementGenerator, new AnchoredExpandingWalkForwardSplitter());
    }

    StrategyWalkForwardExecutor(BarSeriesManager seriesManager, TradingStatementGenerator tradingStatementGenerator,
            WalkForwardSplitter splitter) {
        this.seriesManager = Objects.requireNonNull(seriesManager, "seriesManager");
        this.tradingStatementGenerator = Objects.requireNonNull(tradingStatementGenerator, "tradingStatementGenerator");
        this.splitter = Objects.requireNonNull(splitter, "splitter");
    }

    /**
     * Executes walk-forward testing using strategy starting trade type and unit
     * amount.
     *
     * @param strategy strategy to execute
     * @param config   walk-forward configuration
     * @return execution result
     * @since 0.22.4
     */
    public StrategyWalkForwardExecutionResult execute(Strategy strategy, WalkForwardConfig config) {
        Objects.requireNonNull(strategy, "strategy");
        Num amount = seriesManager.getBarSeries().numFactory().one();
        return execute(strategy, strategy.getStartingType(), amount, config, null);
    }

    /**
     * Executes walk-forward testing with explicit entry trade type and unit amount.
     *
     * @param strategy  strategy to execute
     * @param tradeType trade type used to open positions
     * @param config    walk-forward configuration
     * @return execution result
     * @since 0.22.4
     */
    public StrategyWalkForwardExecutionResult execute(Strategy strategy, Trade.TradeType tradeType,
            WalkForwardConfig config) {
        Num amount = seriesManager.getBarSeries().numFactory().one();
        return execute(strategy, tradeType, amount, config, null);
    }

    /**
     * Executes walk-forward testing with explicit amount.
     *
     * @param strategy  strategy to execute
     * @param tradeType trade type used to open positions
     * @param amount    amount used for entries/exits
     * @param config    walk-forward configuration
     * @return execution result
     * @since 0.22.4
     */
    public StrategyWalkForwardExecutionResult execute(Strategy strategy, Trade.TradeType tradeType, Num amount,
            WalkForwardConfig config) {
        return execute(strategy, tradeType, amount, config, null);
    }

    /**
     * Executes walk-forward testing with optional per-fold progress callback.
     *
     * @param strategy         strategy to execute
     * @param tradeType        trade type used to open positions
     * @param amount           amount used for entries/exits
     * @param config           walk-forward configuration
     * @param progressCallback optional callback receiving completed fold count
     * @return execution result
     * @since 0.22.4
     */
    public StrategyWalkForwardExecutionResult execute(Strategy strategy, Trade.TradeType tradeType, Num amount,
            WalkForwardConfig config, Consumer<Integer> progressCallback) {
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(tradeType, "tradeType");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(config, "config");

        BarSeries series = seriesManager.getBarSeries();
        List<WalkForwardSplit> splits = splitter.split(series, config);
        if (splits.isEmpty()) {
            return new StrategyWalkForwardExecutionResult(series, strategy, config, List.of(),
                    WalkForwardRuntimeReport.empty());
        }

        Consumer<Integer> effectiveCallback = progressCallback == null ? ProgressCompletion.noOp() : progressCallback;
        List<StrategyWalkForwardExecutionResult.FoldResult> foldResults = new ArrayList<>(splits.size());
        List<WalkForwardRuntimeReport.FoldRuntime> foldRuntimes = new ArrayList<>(splits.size());

        long overallStart = System.nanoTime();
        int completed = 0;
        for (WalkForwardSplit split : splits) {
            long foldStart = System.nanoTime();
            TradingRecord foldRecord = seriesManager.run(strategy, tradeType, amount, split.testStart(),
                    split.testEnd());
            TradingStatement statement = tradingStatementGenerator.generate(strategy, foldRecord, series);
            Duration foldRuntime = Duration.ofNanos(System.nanoTime() - foldStart);

            foldResults.add(new StrategyWalkForwardExecutionResult.FoldResult(split, statement, foldRuntime));
            foldRuntimes
                    .add(new WalkForwardRuntimeReport.FoldRuntime(split.foldId(), foldRuntime, split.testBarCount()));

            completed++;
            effectiveCallback.accept(completed);
        }

        Duration overallRuntime = Duration.ofNanos(System.nanoTime() - overallStart);
        WalkForwardRuntimeReport runtimeReport = buildRuntimeReport(foldRuntimes, overallRuntime);
        return new StrategyWalkForwardExecutionResult(series, strategy, config, foldResults, runtimeReport);
    }

    private WalkForwardRuntimeReport buildRuntimeReport(List<WalkForwardRuntimeReport.FoldRuntime> foldRuntimes,
            Duration overallRuntime) {
        if (foldRuntimes.isEmpty()) {
            return WalkForwardRuntimeReport.empty();
        }

        List<Duration> durations = new ArrayList<>(foldRuntimes.size());
        for (WalkForwardRuntimeReport.FoldRuntime foldRuntime : foldRuntimes) {
            durations.add(foldRuntime.runtime());
        }

        Duration min = Collections.min(durations);
        Duration max = Collections.max(durations);
        long totalNanos = 0L;
        for (Duration duration : durations) {
            totalNanos += duration.toNanos();
        }
        Duration average = Duration.ofNanos(totalNanos / durations.size());

        List<Duration> sorted = new ArrayList<>(durations);
        sorted.sort(Comparator.naturalOrder());
        int middle = sorted.size() / 2;
        Duration median;
        if (sorted.size() % 2 == 0) {
            long medianNanos = (sorted.get(middle - 1).toNanos() + sorted.get(middle).toNanos()) / 2;
            median = Duration.ofNanos(medianNanos);
        } else {
            median = sorted.get(middle);
        }

        return new WalkForwardRuntimeReport(overallRuntime, min, max, average, median, foldRuntimes);
    }
}
