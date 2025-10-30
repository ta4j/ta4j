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

import org.ta4j.core.AnalysisCriterion;
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/**
 * Allows backtesting multiple strategies and comparing them to find out which
 * is the best.
 */
public class BacktestExecutor {

    private final BarSeriesManager seriesManager;
    private final TradingStatementGenerator tradingStatementGenerator;

    /**
     * Default batch size for processing strategies. When the number of strategies
     * exceeds this threshold, they will be processed in batches to prevent memory
     * exhaustion. Default is 500.
     */
    private static final int DEFAULT_BATCH_SIZE = 500;

    /**
     * Smaller batch size for very large strategy counts (>5000) to reduce memory
     * pressure. Default is 250.
     */
    private static final int SMALL_BATCH_SIZE = 250;

    /**
     * Threshold for switching from parallel to sequential execution. When the
     * number of strategies exceeds this value, batched sequential processing is
     * used instead of unbounded parallel execution. Default is 1000.
     */
    private static final int PARALLEL_THRESHOLD = 1000;

    /**
     * Threshold for using smaller batch size. When strategy count exceeds this, use
     * SMALL_BATCH_SIZE instead of DEFAULT_BATCH_SIZE. Default is 5000.
     */
    private static final int LARGE_COUNT_THRESHOLD = 5000;

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
        return executeWithRuntimeReport(strategies, amount, tradeType, null);
    }

    /**
     * Executes strategies while collecting runtime measurements and trading
     * statements, with optional progress reporting.
     * <p>
     * For large strategy counts (> {@value #PARALLEL_THRESHOLD}), automatically
     * uses batched sequential processing with a batch size of
     * {@value #DEFAULT_BATCH_SIZE} to prevent memory exhaustion. For smaller
     * counts, uses standard parallel execution.
     * </p>
     *
     * @param strategies       the strategies
     * @param amount           the amount used to open/close the position
     * @param tradeType        the {@link Trade.TradeType} used to open the position
     * @param progressCallback optional callback for progress updates (receives
     *                         completed count). May be null.
     * @return execution result with trading statements and runtime report
     *
     * @since 0.19
     */
    public BacktestExecutionResult executeWithRuntimeReport(List<Strategy> strategies, Num amount,
            Trade.TradeType tradeType, Consumer<Integer> progressCallback) {
        return executeWithRuntimeReport(strategies, amount, tradeType, progressCallback, DEFAULT_BATCH_SIZE);
    }

    /**
     * Executes strategies while collecting runtime measurements and trading
     * statements, with configurable batch size and optional progress reporting.
     * <p>
     * When the strategy count exceeds {@value #PARALLEL_THRESHOLD}, uses batched
     * sequential processing to prevent memory exhaustion. Each batch is processed
     * in parallel, but batches are executed sequentially with explicit GC hints
     * between batches to manage memory pressure.
     * </p>
     *
     * @param strategies       the strategies
     * @param amount           the amount used to open/close the position
     * @param tradeType        the {@link Trade.TradeType} used to open the position
     * @param progressCallback optional callback for progress updates (receives
     *                         completed count). May be null.
     * @param batchSize        the maximum number of strategies to process in each
     *                         batch. Ignored if strategy count <=
     *                         {@value #PARALLEL_THRESHOLD}.
     * @return execution result with trading statements and runtime report
     *
     * @since 0.19
     */
    public BacktestExecutionResult executeWithRuntimeReport(List<Strategy> strategies, Num amount,
            Trade.TradeType tradeType, Consumer<Integer> progressCallback, int batchSize) {
        Objects.requireNonNull(strategies, "strategies must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(tradeType, "tradeType must not be null");

        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }

        if (strategies.isEmpty()) {
            return new BacktestExecutionResult(seriesManager.getBarSeries(), new ArrayList<>(),
                    BacktestRuntimeReport.empty());
        }

        Strategy[] strategyArray = strategies.toArray(Strategy[]::new);
        int strategyCount = strategyArray.length;
        TradingStatement[] statements = new TradingStatement[strategyCount];
        long[] durations = new long[strategyCount];

        long overallStart = System.nanoTime();

        // For large strategy counts, use batched processing to prevent memory
        // exhaustion. Use smaller batches for very large counts.
        if (strategyCount > PARALLEL_THRESHOLD) {
            int effectiveBatchSize = strategyCount > LARGE_COUNT_THRESHOLD ? Math.min(batchSize, SMALL_BATCH_SIZE)
                    : batchSize;
            executeBatched(strategyArray, statements, durations, amount, tradeType, progressCallback,
                    effectiveBatchSize);
        } else {
            executeUnbounded(strategyArray, statements, durations, amount, tradeType, progressCallback);
        }

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
        return new BacktestExecutionResult(seriesManager.getBarSeries(), tradingStatements, runtimeReport);
    }

    /**
     * Executes strategies and returns only the top K results based on a criterion,
     * using a streaming approach that minimizes memory usage.
     * <p>
     * This method is ideal for very large strategy counts (10,000+) where you only
     * need the best performers. It uses a min-heap to track only the top K
     * strategies, discarding worse performers immediately to minimize memory
     * pressure.
     * </p>
     * <p>
     * Memory usage is O(K + batchSize) instead of O(strategyCount), making it
     * suitable for massive parameter sweeps.
     * </p>
     *
     * @param strategies       the strategies to evaluate
     * @param amount           the amount used to open/close the position
     * @param tradeType        the {@link Trade.TradeType} used to open the position
     * @param criterion        the criterion used to rank strategies (higher is
     *                         better)
     * @param topK             the maximum number of top strategies to return
     * @param progressCallback optional callback for progress updates (receives
     *                         completed count). May be null.
     * @return execution result containing only the top K strategies and runtime
     *         report
     *
     * @since 0.19
     */
    public BacktestExecutionResult executeAndKeepTopK(List<Strategy> strategies, Num amount, Trade.TradeType tradeType,
            AnalysisCriterion criterion, int topK, Consumer<Integer> progressCallback) {
        Objects.requireNonNull(strategies, "strategies must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(tradeType, "tradeType must not be null");
        Objects.requireNonNull(criterion, "criterion must not be null");

        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be positive");
        }

        if (strategies.isEmpty()) {
            return new BacktestExecutionResult(seriesManager.getBarSeries(), new ArrayList<>(),
                    BacktestRuntimeReport.empty());
        }

        int strategyCount = strategies.size();
        int effectiveTopK = Math.min(topK, strategyCount);

        // Use min-heap to track only top K strategies
        // (min element at head, so we can quickly discard worse performers)
        PriorityQueue<TradingStatement> topStrategies = new PriorityQueue<>(effectiveTopK + 1,
                Comparator.comparing(ts -> criterion.calculate(seriesManager.getBarSeries(), ts.getTradingRecord())));

        ConcurrentLinkedQueue<TradingStatement> batchResults = new ConcurrentLinkedQueue<>();
        AtomicInteger completedCount = new AtomicInteger(0);

        long overallStart = System.nanoTime();

        // Determine batch size based on strategy count
        int batchSize = strategyCount > LARGE_COUNT_THRESHOLD ? SMALL_BATCH_SIZE : DEFAULT_BATCH_SIZE;

        Strategy[] strategyArray = strategies.toArray(Strategy[]::new);
        List<Duration> allDurations = new ArrayList<>(strategyCount);

        // Process in batches
        for (int batchStart = 0; batchStart < strategyCount; batchStart += batchSize) {
            int batchEnd = Math.min(batchStart + batchSize, strategyCount);
            final int batchStartFinal = batchStart;

            batchResults.clear();

            // Evaluate batch in parallel
            IntStream.range(0, batchEnd - batchStart).parallel().forEach(localIndex -> {
                int globalIndex = batchStartFinal + localIndex;
                Strategy strategy = strategyArray[globalIndex];

                long strategyStart = System.nanoTime();
                TradingRecord tradingRecord = seriesManager.run(strategy, tradeType, amount);
                TradingStatement statement = tradingStatementGenerator.generate(strategy, tradingRecord,
                        seriesManager.getBarSeries());
                long duration = System.nanoTime() - strategyStart;

                synchronized (allDurations) {
                    allDurations.add(Duration.ofNanos(duration));
                }

                batchResults.add(statement);

                if (progressCallback != null) {
                    int completed = completedCount.incrementAndGet();
                    progressCallback.accept(completed);
                }
            });

            // Merge batch results into top-K heap
            for (TradingStatement statement : batchResults) {
                Num criterionValue = criterion.calculate(seriesManager.getBarSeries(), statement.getTradingRecord());

                if (topStrategies.size() < effectiveTopK) {
                    topStrategies.offer(statement);
                } else {
                    // Heap is full - compare with worst strategy
                    TradingStatement worst = topStrategies.peek();
                    if (worst != null) {
                        Num worstValue = criterion.calculate(seriesManager.getBarSeries(), worst.getTradingRecord());
                        if (criterionValue.isGreaterThan(worstValue)) {
                            topStrategies.poll(); // Remove worst
                            topStrategies.offer(statement); // Add new
                        }
                    }
                }
            }

            // Clear batch results and suggest GC
            batchResults.clear();
            if (batchEnd < strategyCount) {
                System.gc();
                Thread.yield(); // Give GC a chance to run
            }
        }

        Duration overallRuntime = Duration.ofNanos(System.nanoTime() - overallStart);

        // Extract top strategies in descending order
        List<TradingStatement> resultStatements = new ArrayList<>(topStrategies.size());
        while (!topStrategies.isEmpty()) {
            resultStatements.add(0, topStrategies.poll()); // Insert at front to reverse order
        }

        // Build runtime report (approximate, since we don't track all individual times)
        List<BacktestRuntimeReport.StrategyRuntime> strategyRuntimes = new ArrayList<>(resultStatements.size());
        for (int i = 0; i < resultStatements.size(); i++) {
            Duration approxDuration = i < allDurations.size() ? allDurations.get(i) : Duration.ZERO;
            strategyRuntimes.add(
                    new BacktestRuntimeReport.StrategyRuntime(resultStatements.get(i).getStrategy(), approxDuration));
        }

        // Calculate summary statistics from saved durations
        long[] durationArray = allDurations.stream().mapToLong(Duration::toNanos).toArray();
        BacktestRuntimeReport runtimeReport = buildRuntimeReport(durationArray, overallRuntime, strategyRuntimes);

        return new BacktestExecutionResult(seriesManager.getBarSeries(), resultStatements, runtimeReport);
    }

    /**
     * Executes strategies using unbounded parallel execution (standard behavior).
     */
    private void executeUnbounded(Strategy[] strategyArray, TradingStatement[] statements, long[] durations, Num amount,
            Trade.TradeType tradeType, Consumer<Integer> progressCallback) {
        int strategyCount = strategyArray.length;
        AtomicInteger completedCount = new AtomicInteger(0);

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

            if (progressCallback != null) {
                int completed = completedCount.incrementAndGet();
                progressCallback.accept(completed);
            }
        });
    }

    /**
     * Executes strategies in batches to prevent memory exhaustion. Each batch is
     * processed in parallel, but batches are executed sequentially with explicit GC
     * hints between batches.
     */
    private void executeBatched(Strategy[] strategyArray, TradingStatement[] statements, long[] durations, Num amount,
            Trade.TradeType tradeType, Consumer<Integer> progressCallback, int batchSize) {
        int strategyCount = strategyArray.length;
        AtomicInteger completedCount = new AtomicInteger(0);

        for (int batchStart = 0; batchStart < strategyCount; batchStart += batchSize) {
            int batchEnd = Math.min(batchStart + batchSize, strategyCount);
            final int batchStartFinal = batchStart;

            IntStream.range(0, batchEnd - batchStart).parallel().forEach(localIndex -> {
                int globalIndex = batchStartFinal + localIndex;
                Strategy strategy = strategyArray[globalIndex];
                long strategyStart = System.nanoTime();
                TradingRecord tradingRecord = seriesManager.run(strategy, tradeType, amount);
                TradingStatement statement = tradingStatementGenerator.generate(strategy, tradingRecord,
                        seriesManager.getBarSeries());
                statements[globalIndex] = statement;
                durations[globalIndex] = System.nanoTime() - strategyStart;

                if (progressCallback != null) {
                    int completed = completedCount.incrementAndGet();
                    progressCallback.accept(completed);
                }
            });

            // Aggressively suggest GC between batches to manage memory pressure
            // For very large counts, be more aggressive
            if (batchEnd < strategyCount) {
                System.gc();
                Thread.yield(); // Give GC a chance to run
                if (strategyCount > LARGE_COUNT_THRESHOLD) {
                    // For very large strategy counts, try even harder
                    try {
                        Thread.sleep(10); // Brief pause to let GC complete
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
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
