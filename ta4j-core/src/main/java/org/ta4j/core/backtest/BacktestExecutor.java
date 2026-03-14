/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.num.Num;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.reports.TradingStatementGenerator;
import org.ta4j.core.walkforward.AnchoredExpandingWalkForwardSplitter;
import org.ta4j.core.walkforward.WalkForwardConfig;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
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
        this(new BarSeriesManager(series), new TradingStatementGenerator());
    }

    /**
     * Constructor.
     *
     * @param series              the bar series
     * @param tradeExecutionModel the trade execution model
     * @since 0.22.4
     */
    public BacktestExecutor(BarSeries series, TradeExecutionModel tradeExecutionModel) {
        this(new BarSeriesManager(series, tradeExecutionModel), new TradingStatementGenerator());
    }

    /**
     * Constructor.
     *
     * @param series               the bar series
     * @param transactionCostModel the cost model for transactions of the asset
     * @param holdingCostModel     the cost model for holding the asset (e.g.
     *                             borrowing)
     * @param tradeExecutionModel  the trade execution model
     * @since 0.22.4
     */
    public BacktestExecutor(BarSeries series, CostModel transactionCostModel, CostModel holdingCostModel,
            TradeExecutionModel tradeExecutionModel) {
        this(new BarSeriesManager(series, transactionCostModel, holdingCostModel, tradeExecutionModel),
                new TradingStatementGenerator());
    }

    /**
     * Constructor.
     *
     * @param series                    the bar series
     * @param tradingStatementGenerator the TradingStatementGenerator
     */
    public BacktestExecutor(BarSeries series, TradingStatementGenerator tradingStatementGenerator) {
        this(new BarSeriesManager(series), tradingStatementGenerator);
    }

    /**
     * Constructor.
     *
     * @param series                    the bar series
     * @param tradingStatementGenerator the TradingStatementGenerator
     * @param tradeExecutionModel       the trade execution model
     * @since 0.22.4
     */
    public BacktestExecutor(BarSeries series, TradingStatementGenerator tradingStatementGenerator,
            TradeExecutionModel tradeExecutionModel) {
        this(new BarSeriesManager(series, tradeExecutionModel), tradingStatementGenerator);
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
        this(new BarSeriesManager(series, transactionCostModel, holdingCostModel, tradeExecutionModel),
                tradingStatementGenerator);
    }

    /**
     * Constructor.
     *
     * @param seriesManager the preconfigured series manager including its cost
     *                      models, trade execution model, and default
     *                      trading-record creation policy
     * @since 0.22.4
     */
    public BacktestExecutor(BarSeriesManager seriesManager) {
        this(seriesManager, new TradingStatementGenerator());
    }

    /**
     * Constructor.
     *
     * @param seriesManager             the preconfigured series manager including
     *                                  its cost models, trade execution model, and
     *                                  default trading-record creation policy
     * @param tradingStatementGenerator the TradingStatementGenerator
     * @since 0.22.4
     */
    public BacktestExecutor(BarSeriesManager seriesManager, TradingStatementGenerator tradingStatementGenerator) {
        this.seriesManager = Objects.requireNonNull(seriesManager, "seriesManager");
        this.tradingStatementGenerator = Objects.requireNonNull(tradingStatementGenerator, "tradingStatementGenerator");
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
     * <p>
     * If {@code progressCallback} is null, uses {@link ProgressCompletion#noOp()}
     * as the default (no progress reporting). To use default logging progress, pass
     * {@link ProgressCompletion#logging(Class)} or
     * {@link ProgressCompletion#logging(String)} with your class or logger name.
     * </p>
     *
     * @param strategies       the strategies
     * @param amount           the amount used to open/close the position
     * @param tradeType        the {@link Trade.TradeType} used to open the position
     * @param progressCallback optional callback for progress updates (receives
     *                         completed count). May be null, in which case
     *                         {@link ProgressCompletion#noOp()} is used.
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
     *                         batch. Ignored if strategy count {@literal <=}
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

        // Use default no-op callback if none provided, and set total strategies for
        // logging callbacks
        Consumer<Integer> effectiveCallback = ProgressCompletion.withTotalStrategies(
                progressCallback != null ? progressCallback : ProgressCompletion.noOp(), strategyCount);

        long overallStart = System.nanoTime();

        // For large strategy counts, use batched processing to prevent memory
        // exhaustion. Use smaller batches for very large counts.
        if (strategyCount > PARALLEL_THRESHOLD) {
            int effectiveBatchSize = strategyCount > LARGE_COUNT_THRESHOLD ? Math.min(batchSize, SMALL_BATCH_SIZE)
                    : batchSize;
            executeBatched(strategyArray, statements, durations, amount, tradeType, effectiveCallback,
                    effectiveBatchSize);
        } else {
            executeUnbounded(strategyArray, statements, durations, amount, tradeType, effectiveCallback);
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
     * Executes walk-forward testing for one strategy using strategy starting type
     * and unit amount.
     *
     * @param strategy strategy to execute
     * @param config   walk-forward configuration
     * @return walk-forward execution result
     * @since 0.22.4
     */
    public StrategyWalkForwardExecutionResult executeWalkForward(Strategy strategy, WalkForwardConfig config) {
        Objects.requireNonNull(strategy, "strategy");
        Num unitAmount = seriesManager.getBarSeries().numFactory().one();
        return executeWalkForward(strategy, unitAmount, strategy.getStartingType(), config, null);
    }

    /**
     * Executes walk-forward testing for one strategy with explicit amount and
     * strategy starting trade type.
     *
     * @param strategy strategy to execute
     * @param amount   amount used to open/close trades
     * @param config   walk-forward configuration
     * @return walk-forward execution result
     * @since 0.22.4
     */
    public StrategyWalkForwardExecutionResult executeWalkForward(Strategy strategy, Num amount,
            WalkForwardConfig config) {
        Objects.requireNonNull(strategy, "strategy");
        return executeWalkForward(strategy, amount, strategy.getStartingType(), config, null);
    }

    /**
     * Executes walk-forward testing for one strategy with explicit amount and trade
     * type.
     *
     * @param strategy  strategy to execute
     * @param amount    amount used to open/close trades
     * @param tradeType trade type used to open positions
     * @param config    walk-forward configuration
     * @return walk-forward execution result
     * @since 0.22.4
     */
    public StrategyWalkForwardExecutionResult executeWalkForward(Strategy strategy, Num amount,
            Trade.TradeType tradeType, WalkForwardConfig config) {
        return executeWalkForward(strategy, amount, tradeType, config, null);
    }

    /**
     * Executes walk-forward testing for one strategy with optional per-fold
     * progress callback.
     *
     * @param strategy         strategy to execute
     * @param amount           amount used to open/close trades
     * @param tradeType        trade type used to open positions
     * @param config           walk-forward configuration
     * @param progressCallback optional callback receiving completed fold count
     * @return walk-forward execution result
     * @since 0.22.4
     */
    public StrategyWalkForwardExecutionResult executeWalkForward(Strategy strategy, Num amount,
            Trade.TradeType tradeType, WalkForwardConfig config, Consumer<Integer> progressCallback) {
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(tradeType, "tradeType");
        Objects.requireNonNull(config, "config");
        StrategyWalkForwardExecutor executor = new StrategyWalkForwardExecutor(seriesManager, tradingStatementGenerator,
                new AnchoredExpandingWalkForwardSplitter());
        return executor.execute(strategy, tradeType, amount, config, progressCallback);
    }

    /**
     * Runs both standard backtest and walk-forward evaluation for one strategy
     * using strategy starting type and unit amount.
     *
     * @param strategy strategy to execute
     * @param config   walk-forward configuration
     * @return combined backtest and walk-forward result
     * @since 0.22.4
     */
    public BacktestAndWalkForwardResult executeWithWalkForward(Strategy strategy, WalkForwardConfig config) {
        Objects.requireNonNull(strategy, "strategy");
        Num unitAmount = seriesManager.getBarSeries().numFactory().one();
        return executeWithWalkForward(strategy, unitAmount, strategy.getStartingType(), config);
    }

    /**
     * Runs both standard backtest and walk-forward evaluation for one strategy with
     * explicit amount and strategy starting trade type.
     *
     * @param strategy strategy to execute
     * @param amount   amount used to open/close trades
     * @param config   walk-forward configuration
     * @return combined backtest and walk-forward result
     * @since 0.22.4
     */
    public BacktestAndWalkForwardResult executeWithWalkForward(Strategy strategy, Num amount,
            WalkForwardConfig config) {
        Objects.requireNonNull(strategy, "strategy");
        return executeWithWalkForward(strategy, amount, strategy.getStartingType(), config);
    }

    /**
     * Runs both standard backtest and walk-forward evaluation for one strategy.
     *
     * @param strategy  strategy to execute
     * @param amount    amount used to open/close trades
     * @param tradeType trade type used to open positions
     * @param config    walk-forward configuration
     * @return combined backtest and walk-forward result
     * @since 0.22.4
     */
    public BacktestAndWalkForwardResult executeWithWalkForward(Strategy strategy, Num amount, Trade.TradeType tradeType,
            WalkForwardConfig config) {
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(tradeType, "tradeType");
        Objects.requireNonNull(config, "config");

        BacktestExecutionResult backtestResult = executeWithRuntimeReport(List.of(strategy), amount, tradeType);
        StrategyWalkForwardExecutionResult walkForwardResult = executeWalkForward(strategy, amount, tradeType, config);
        return new BacktestAndWalkForwardResult(backtestResult, walkForwardResult);
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

        Comparator<StrategyEvaluation> bestFirstComparator = createBestFirstComparator(criterion);
        PriorityQueue<StrategyEvaluation> topStrategies = new PriorityQueue<>(effectiveTopK + 1,
                bestFirstComparator.reversed());

        ConcurrentLinkedQueue<StrategyEvaluation> batchResults = new ConcurrentLinkedQueue<>();
        // Use default no-op callback if none provided, and set total strategies for
        // logging callbacks
        Consumer<Integer> effectiveCallback = ProgressCompletion.withTotalStrategies(
                progressCallback != null ? progressCallback : ProgressCompletion.noOp(), strategyCount);
        ProgressTracker progressTracker = ProgressTracker.create(effectiveCallback);

        long overallStart = System.nanoTime();

        // Determine batch size based on strategy count
        int batchSize = strategyCount > LARGE_COUNT_THRESHOLD ? SMALL_BATCH_SIZE : DEFAULT_BATCH_SIZE;

        Strategy[] strategyArray = strategies.toArray(Strategy[]::new);
        long[] durationNanos = new long[strategyCount];

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
                durationNanos[globalIndex] = duration;
                Num criterionValue = criterion.calculate(seriesManager.getBarSeries(), statement.getTradingRecord());
                batchResults.add(new StrategyEvaluation(statement, criterionValue, globalIndex));

                if (progressTracker != null) {
                    progressTracker.reportCompletion();
                }
            });

            // Merge batch results into top-K heap
            for (StrategyEvaluation evaluation : batchResults) {
                if (topStrategies.size() < effectiveTopK) {
                    topStrategies.offer(evaluation);
                } else {
                    // Heap is full - compare with worst strategy
                    StrategyEvaluation worst = topStrategies.peek();
                    if (worst != null) {
                        if (bestFirstComparator.compare(evaluation, worst) < 0) {
                            topStrategies.poll(); // Remove worst
                            topStrategies.offer(evaluation); // Add new
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

        // Extract top strategies and sort them in correct order (best first)
        List<StrategyEvaluation> sortedEvaluations = new ArrayList<>(topStrategies);
        sortedEvaluations.sort(bestFirstComparator); // Sort using non-reversed comparator (best first)
        List<TradingStatement> resultStatements = new ArrayList<>(sortedEvaluations.size());
        for (StrategyEvaluation evaluation : sortedEvaluations) {
            resultStatements.add(evaluation.statement());
        }

        // Build runtime report (approximate, since we don't track all individual times)
        List<BacktestRuntimeReport.StrategyRuntime> strategyRuntimes = new ArrayList<>(sortedEvaluations.size());
        for (StrategyEvaluation evaluation : sortedEvaluations) {
            Duration runtime = Duration.ofNanos(durationNanos[evaluation.index()]);
            strategyRuntimes
                    .add(new BacktestRuntimeReport.StrategyRuntime(evaluation.statement().getStrategy(), runtime));
        }

        // Calculate summary statistics from saved durations
        BacktestRuntimeReport runtimeReport = buildRuntimeReport(durationNanos, overallRuntime, strategyRuntimes);

        return new BacktestExecutionResult(seriesManager.getBarSeries(), resultStatements, runtimeReport);
    }

    private Comparator<StrategyEvaluation> createBestFirstComparator(AnalysisCriterion criterion) {
        return (left, right) -> {
            Num leftValue = left.criterionValue();
            Num rightValue = right.criterionValue();

            boolean leftNaN = leftValue.isNaN();
            boolean rightNaN = rightValue.isNaN();
            if (leftNaN && rightNaN) {
                return Integer.compare(left.index(), right.index());
            }
            if (leftNaN) {
                return 1;
            }
            if (rightNaN) {
                return -1;
            }

            if (criterion.betterThan(leftValue, rightValue)) {
                return -1;
            }
            if (criterion.betterThan(rightValue, leftValue)) {
                return 1;
            }

            int naturalComparison = leftValue.compareTo(rightValue);
            if (naturalComparison != 0) {
                return naturalComparison;
            }

            return Integer.compare(left.index(), right.index());
        };
    }

    private static final class StrategyEvaluation {

        private final TradingStatement statement;
        private final Num criterionValue;
        private final int index;

        private StrategyEvaluation(TradingStatement statement, Num criterionValue, int index) {
            this.statement = statement;
            this.criterionValue = criterionValue;
            this.index = index;
        }

        private TradingStatement statement() {
            return statement;
        }

        private Num criterionValue() {
            return criterionValue;
        }

        private int index() {
            return index;
        }
    }

    /**
     * Combined result for one-strategy backtest and walk-forward execution.
     *
     * @param backtest    standard backtest output
     * @param walkForward walk-forward output
     * @since 0.22.4
     */
    public record BacktestAndWalkForwardResult(BacktestExecutionResult backtest,
            StrategyWalkForwardExecutionResult walkForward) {

        /**
         * Creates a validated combined result.
         *
         * @param backtest    standard backtest output
         * @param walkForward walk-forward output
         * @since 0.22.4
         */
        public BacktestAndWalkForwardResult {
            backtest = Objects.requireNonNull(backtest, "backtest");
            walkForward = Objects.requireNonNull(walkForward, "walkForward");
        }
    }

    /**
     * Executes strategies using unbounded parallel execution (standard behavior).
     */
    private void executeUnbounded(Strategy[] strategyArray, TradingStatement[] statements, long[] durations, Num amount,
            Trade.TradeType tradeType, Consumer<Integer> progressCallback) {
        int strategyCount = strategyArray.length;
        ProgressTracker progressTracker = ProgressTracker.create(progressCallback);

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

            if (progressTracker != null) {
                progressTracker.reportCompletion();
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
        ProgressTracker progressTracker = ProgressTracker.create(progressCallback);

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

                if (progressTracker != null) {
                    progressTracker.reportCompletion();
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

    private static final class ProgressTracker {

        private final Consumer<Integer> callback;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition ready = lock.newCondition();
        private int completedCount = 0;
        private int nextToReport = 1;

        private ProgressTracker(Consumer<Integer> callback) {
            this.callback = callback;
        }

        static ProgressTracker create(Consumer<Integer> callback) {
            return callback == null ? null : new ProgressTracker(callback);
        }

        void reportCompletion() {
            int completionOrder;
            lock.lock();
            try {
                completionOrder = ++completedCount;
                while (completionOrder != nextToReport) {
                    ready.await();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while reporting progress", e);
            } finally {
                lock.unlock();
            }

            try {
                callback.accept(completionOrder);
            } finally {
                lock.lock();
                try {
                    nextToReport++;
                    ready.signalAll();
                } finally {
                    lock.unlock();
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
