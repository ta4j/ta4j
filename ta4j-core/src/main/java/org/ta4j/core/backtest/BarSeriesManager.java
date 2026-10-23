/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import java.util.Objects;
import java.util.function.Consumer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.acceleration.AccelerationRuntime;
import org.ta4j.core.backtest.TradeExecutionModel.ExecutionTarget;
import org.ta4j.core.num.Num;
import org.ta4j.core.reports.TradingStatementGenerator;
import org.ta4j.core.walkforward.AnchoredExpandingWalkForwardSplitter;
import org.ta4j.core.walkforward.WalkForwardConfig;

/**
 * A manager for {@link BarSeries} objects used for backtesting. Allows to run a
 * {@link Strategy trading strategy} over the managed bar series.
 *
 * <p>
 * The manager borrows the caller's {@link BarSeries} without copying it:
 * strategies, indicators, execution models, and position sizing all observe the
 * same instance, so signals and fills read the same bars. The manager never
 * modifies the series.
 * </p>
 *
 * <p>
 * A run covers the bounds captured when it starts and never trades outside
 * them: execution models and position sizing see the series only up to the
 * run's last index, so a signal on that bar that needs a later bar to fill does
 * not fill, and a position still open at the end stays open for criteria to
 * mark to market or ignore (see
 * {@link org.ta4j.core.analysis.OpenPositionHandling}); wrap the execution
 * model in an {@link ExitOnRunEndModel} to close it at the last close instead.
 * Walk-forward folds always end flat this way. Bars after the window, whether
 * retained past a constrained series' logical end or appended by a live feed,
 * are never used. The manager holds no lock while strategies run, so a live
 * {@link org.ta4j.core.ConcurrentBarSeries} keeps accepting writes and reads
 * from other threads, and bars replaced or evicted inside the run's bounds are
 * observed as they change. Use {@link BacktestExecutor} when a result must be
 * tied to one unchanged window: it fails if the window changes.
 * </p>
 *
 * <p>
 * Default {@code run(...)} overloads create a fresh trading record through this
 * manager's configured {@link TradingRecordFactory}. Existing behavior remains
 * unchanged by default ({@link BaseTradingRecord}), while callers can inject a
 * custom record implementation for unified backtest/live execution paths.
 * </p>
 *
 * <p>
 * Use this class as the default backtest entrypoint when you are evaluating one
 * strategy over one series. For large strategy batches with ranking and runtime
 * telemetry, switch to {@link BacktestExecutor}.
 * </p>
 */
public class BarSeriesManager {

    /** The logger */
    private static final Logger log = LoggerFactory.getLogger(BarSeriesManager.class);

    /** Default trading record factory. */
    private static final TradingRecordFactory DEFAULT_TRADING_RECORD_FACTORY = (tradeType, startIndex, endIndex,
            transactionCostModel, holdingCostModel) -> new BaseTradingRecord(tradeType, startIndex, endIndex,
                    transactionCostModel, holdingCostModel);

    /** The managed bar series */
    private final BarSeries barSeries;

    /** The trading cost models */
    private final CostModel transactionCostModel;
    private final CostModel holdingCostModel;

    /** The trade execution model to use */
    private final TradeExecutionModel tradeExecutionModel;

    /** The trading record factory used by default run overloads. */
    private final TradingRecordFactory tradingRecordFactory;

    /**
     * Factory for creating trading records for backtest runs.
     *
     * <p>
     * Implementations must return a fresh mutable {@link TradingRecord} for each
     * invocation. Reusing the same instance across runs causes state leakage
     * between executions.
     * </p>
     *
     * @since 0.22.4
     */
    @FunctionalInterface
    public interface TradingRecordFactory {
        /**
         * Creates a trading record.
         *
         * @param tradeType            strategy entry type
         * @param startIndex           run start index (already clamped)
         * @param endIndex             run end index (already clamped)
         * @param transactionCostModel transaction cost model
         * @param holdingCostModel     holding cost model
         * @return a new trading record instance for the run
         * @since 0.22.4
         */
        TradingRecord create(TradeType tradeType, int startIndex, int endIndex, CostModel transactionCostModel,
                CostModel holdingCostModel);
    }

    /**
     * Constructor with {@link #tradeExecutionModel} = {@link TradeOnNextOpenModel}.
     *
     * @param barSeries the bar series to be managed
     */
    public BarSeriesManager(BarSeries barSeries) {
        this(barSeries, new ZeroCostModel(), new ZeroCostModel(), new TradeOnNextOpenModel());
    }

    /**
     * Constructor.
     *
     * @param barSeries           the bar series to be managed
     * @param tradeExecutionModel the trade execution model to use
     * @since 0.22.4
     */
    public BarSeriesManager(BarSeries barSeries, TradeExecutionModel tradeExecutionModel) {
        this(barSeries, new ZeroCostModel(), new ZeroCostModel(), tradeExecutionModel);
    }

    /**
     * Constructor with {@link #tradeExecutionModel} = {@link TradeOnNextOpenModel}.
     *
     * @param barSeries            the bar series to be managed
     * @param transactionCostModel the cost model for transactions of the asset
     * @param holdingCostModel     the cost model for holding the asset (e.g.
     *                             borrowing)
     */
    public BarSeriesManager(BarSeries barSeries, CostModel transactionCostModel, CostModel holdingCostModel) {
        this(barSeries, transactionCostModel, holdingCostModel, new TradeOnNextOpenModel(),
                DEFAULT_TRADING_RECORD_FACTORY);
    }

    /**
     * Constructor.
     *
     * @param barSeries            the bar series to be managed
     * @param transactionCostModel the cost model for transactions of the asset
     * @param holdingCostModel     the cost model for holding asset (e.g. borrowing)
     * @param tradeExecutionModel  the trade execution model to use
     */
    public BarSeriesManager(BarSeries barSeries, CostModel transactionCostModel, CostModel holdingCostModel,
            TradeExecutionModel tradeExecutionModel) {
        this(barSeries, transactionCostModel, holdingCostModel, tradeExecutionModel, DEFAULT_TRADING_RECORD_FACTORY);
    }

    /**
     * Constructor.
     *
     * @param barSeries            the bar series to be managed
     * @param transactionCostModel the cost model for transactions of the asset
     * @param holdingCostModel     the cost model for holding asset (e.g. borrowing)
     * @param tradeExecutionModel  the trade execution model to use
     * @param tradingRecordFactory factory for default run overloads
     * @since 0.22.4
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "The manager borrows the caller's live series so "
            + "strategies, indicators, execution models, and position sizing observe one coherent price revision; "
            + "copying desynchronized signal and fill pricing.")
    public BarSeriesManager(BarSeries barSeries, CostModel transactionCostModel, CostModel holdingCostModel,
            TradeExecutionModel tradeExecutionModel, TradingRecordFactory tradingRecordFactory) {
        Objects.requireNonNull(barSeries, "barSeries");
        Objects.requireNonNull(transactionCostModel, "transactionCostModel");
        Objects.requireNonNull(holdingCostModel, "holdingCostModel");
        Objects.requireNonNull(tradeExecutionModel, "tradeExecutionModel");
        Objects.requireNonNull(tradingRecordFactory, "tradingRecordFactory");
        this.barSeries = barSeries;
        this.transactionCostModel = transactionCostModel;
        this.holdingCostModel = holdingCostModel;
        this.tradeExecutionModel = tradeExecutionModel;
        this.tradingRecordFactory = tradingRecordFactory;
    }

    /**
     * Returns a manager over the same series, cost models and record factory whose
     * runs end flat: its execution model is wrapped in an {@link ExitOnRunEndModel}
     * unless it already is one.
     */
    BarSeriesManager exitingOnRunEnd() {
        if (tradeExecutionModel instanceof ExitOnRunEndModel) {
            return this;
        }
        return new BarSeriesManager(barSeries, transactionCostModel, holdingCostModel,
                new ExitOnRunEndModel(tradeExecutionModel), tradingRecordFactory);
    }

    /**
     * @return the managed bar series
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Returns the borrowed caller series by contract; see "
            + "the class Javadoc ownership note.")
    public BarSeries getBarSeries() {
        return barSeries;
    }

    /**
     * @return the transaction cost model
     */
    public CostModel getTransactionCostModel() {
        return transactionCostModel;
    }

    /**
     * @return the holding cost model
     */
    public CostModel getHoldingCostModel() {
        return holdingCostModel;
    }

    /**
     * Runs the provided strategy over the managed series.
     *
     * Opens the position with the strategy {@link TradeType starting type}.
     *
     * @return the trading record coming from the run
     */
    public TradingRecord run(Strategy strategy) {
        return run(strategy, strategy.getStartingType());
    }

    /**
     * Runs the provided strategy over the managed series (from startIndex to
     * finishIndex).
     *
     * Opens the position with the strategy {@link TradeType starting type}.
     *
     * @param strategy    the trading strategy to execute (read-only)
     * @param startIndex  the start index for the run (included)
     * @param finishIndex the finish index for the run (included)
     * @return a new trading record populated with the run's trades
     */
    public TradingRecord run(Strategy strategy, int startIndex, int finishIndex) {
        return run(strategy, strategy.getStartingType(), barSeries.numFactory().one(), startIndex, finishIndex);
    }

    /**
     * Runs the provided strategy over the managed series.
     *
     * Opens the position with a trade of {@link TradeType tradeType}.
     *
     * @param strategy  the trading strategy to execute (read-only)
     * @param tradeType the {@link TradeType} used to open the position
     * @return a new trading record populated with the run's trades
     */
    public TradingRecord run(Strategy strategy, TradeType tradeType) {
        return run(strategy, tradeType, barSeries.numFactory().one());
    }

    /**
     * Runs the provided strategy over the managed series (from startIndex to
     * finishIndex).
     *
     * Opens the position with a trade of {@link TradeType tradeType}.
     *
     * @param strategy    the trading strategy
     * @param tradeType   the {@link TradeType} used to open the position
     * @param startIndex  the start index for the run (included)
     * @param finishIndex the finish index for the run (included)
     * @return the trading record coming from the run
     */
    public TradingRecord run(Strategy strategy, TradeType tradeType, int startIndex, int finishIndex) {
        return run(strategy, tradeType, barSeries.numFactory().one(), startIndex, finishIndex);
    }

    /**
     * Runs the provided strategy over the managed series.
     *
     * @param strategy  the trading strategy
     * @param tradeType the {@link TradeType} used to open the position
     * @param amount    the amount used to open/close the trades
     * @return the trading record coming from the run
     */
    public TradingRecord run(Strategy strategy, TradeType tradeType, Num amount) {
        Bounds bounds = currentBounds();
        return run(strategy, tradeType, amount, bounds.begin(), bounds.end());
    }

    /**
     * Runs the provided strategy over the managed series (from startIndex to
     * finishIndex).
     *
     * @param strategy    the trading strategy
     * @param tradeType   the {@link TradeType} used to open the trades
     * @param amount      the amount used to open/close the trades
     * @param startIndex  the start index for the run (included)
     * @param finishIndex the finish index for the run (included)
     * @return the trading record coming from the run
     */
    public TradingRecord run(Strategy strategy, TradeType tradeType, Num amount, int startIndex, int finishIndex) {
        Bounds window = clampToCurrentBounds(startIndex, finishIndex);
        TradingRecord tradingRecord = createDefaultTradingRecord(tradeType, window);
        return run(strategy, tradingRecord, amount, window.begin(), window.end());
    }

    /**
     * Runs the provided strategy over the managed series using a dynamic entry
     * position sizer.
     *
     * @param strategy      strategy to execute
     * @param tradeType     the {@link TradeType} used to open the position
     * @param positionSizer dynamic entry position sizer
     * @return the trading record coming from the run
     * @since 0.22.9
     */
    public TradingRecord run(Strategy strategy, TradeType tradeType, PositionSizer positionSizer) {
        Bounds bounds = currentBounds();
        return run(strategy, tradeType, positionSizer, bounds.begin(), bounds.end());
    }

    /**
     * Runs the provided strategy over the managed series using a dynamic entry
     * position sizer.
     *
     * @param strategy      strategy to execute
     * @param positionSizer dynamic entry position sizer
     * @return the trading record coming from the run
     * @since 0.22.9
     */
    public TradingRecord run(Strategy strategy, PositionSizer positionSizer) {
        Objects.requireNonNull(strategy, "strategy");
        return run(strategy, strategy.getStartingType(), positionSizer);
    }

    /**
     * Runs the provided strategy over the managed series (from startIndex to
     * finishIndex) using the strategy starting type and a dynamic entry position
     * sizer.
     *
     * @param strategy      strategy to execute
     * @param positionSizer dynamic entry position sizer
     * @param startIndex    the start index for the run (included)
     * @param finishIndex   the finish index for the run (included)
     * @return the trading record coming from the run
     * @since 0.22.9
     */
    public TradingRecord run(Strategy strategy, PositionSizer positionSizer, int startIndex, int finishIndex) {
        Objects.requireNonNull(strategy, "strategy");
        return run(strategy, strategy.getStartingType(), positionSizer, startIndex, finishIndex);
    }

    /**
     * Runs the provided strategy over the managed series (from startIndex to
     * finishIndex) using a dynamic entry position sizer.
     *
     * @param strategy      strategy to execute
     * @param tradeType     the {@link TradeType} used to open the position
     * @param positionSizer dynamic entry position sizer
     * @param startIndex    the start index for the run (included)
     * @param finishIndex   the finish index for the run (included)
     * @return the trading record coming from the run
     * @since 0.22.9
     */
    public TradingRecord run(Strategy strategy, TradeType tradeType, PositionSizer positionSizer, int startIndex,
            int finishIndex) {
        Bounds window = clampToCurrentBounds(startIndex, finishIndex);
        TradingRecord tradingRecord = createDefaultTradingRecord(tradeType, window);
        return run(strategy, tradingRecord, positionSizer, window.begin(), window.end());
    }

    /**
     * Runs the provided strategy over the managed series using the supplied trading
     * record.
     *
     * <p>
     * This allows callers to backtest with alternate {@link TradingRecord}
     * implementations (for example a lot-aware {@code BaseTradingRecord}) while
     * reusing {@link BarSeriesManager}'s execution loop.
     * </p>
     *
     * @param strategy      the trading strategy
     * @param tradingRecord the trading record instance to mutate
     * @return the supplied trading record after execution
     * @since 0.22.4
     */
    public TradingRecord run(Strategy strategy, TradingRecord tradingRecord) {
        return run(strategy, tradingRecord, barSeries.numFactory().one());
    }

    /**
     * Runs the provided strategy over the managed series using the supplied trading
     * record.
     *
     * @param strategy      the trading strategy
     * @param tradingRecord the trading record instance to mutate
     * @param amount        the amount used to open/close the trades
     * @return the supplied trading record after execution
     * @since 0.22.4
     */
    public TradingRecord run(Strategy strategy, TradingRecord tradingRecord, Num amount) {
        Bounds bounds = currentBounds();
        return run(strategy, tradingRecord, amount, bounds.begin(), bounds.end());
    }

    /**
     * Runs the provided strategy over the managed series using the supplied trading
     * record (from startIndex to finishIndex).
     *
     * <p>
     * <strong>Thread safety:</strong> This {@code BarSeriesManager.run(...)}
     * overload mutates the supplied {@link TradingRecord}. Callers must ensure
     * exclusive access to that record, or synchronize externally, while the run is
     * executing. Concurrent reads or writes against the same {@link TradingRecord}
     * during execution lead to undefined behavior.
     * </p>
     *
     * @param strategy      the trading strategy
     * @param tradingRecord the trading record instance to mutate
     * @param amount        the amount used to open/close the trades
     * @param startIndex    the start index for the run (included)
     * @param finishIndex   the finish index for the run (included)
     * @return the supplied trading record after execution
     * @since 0.22.4
     */
    public TradingRecord run(Strategy strategy, TradingRecord tradingRecord, Num amount, int startIndex,
            int finishIndex) {
        Objects.requireNonNull(amount, "amount");
        return run(strategy, tradingRecord, startIndex, finishIndex, (index, runSeries) -> amount);
    }

    /**
     * Runs the provided strategy over the managed series using a dynamic entry
     * position sizer and a supplied trading record.
     *
     * @param strategy      strategy to execute
     * @param tradingRecord the trading record instance to mutate
     * @param positionSizer dynamic entry position sizer
     * @return the supplied trading record after execution
     * @since 0.22.9
     */
    public TradingRecord run(Strategy strategy, TradingRecord tradingRecord, PositionSizer positionSizer) {
        Bounds bounds = currentBounds();
        return run(strategy, tradingRecord, positionSizer, bounds.begin(), bounds.end());
    }

    /**
     * Runs the provided strategy over the managed series (from startIndex to
     * finishIndex) using a supplied trading record and dynamic entry position
     * sizer.
     *
     * @param strategy      strategy to execute
     * @param tradingRecord the trading record instance to mutate
     * @param positionSizer dynamic entry position sizer
     * @param startIndex    the start index for the run (included)
     * @param finishIndex   the finish index for the run (included)
     * @return the supplied trading record after execution
     * @since 0.22.9
     */
    public TradingRecord run(Strategy strategy, TradingRecord tradingRecord, PositionSizer positionSizer,
            int startIndex, int finishIndex) {
        return runWithPositionSizer(strategy, tradingRecord, positionSizer, startIndex, finishIndex);
    }

    /**
     * Reads the logical bounds under one read scope so a concurrent eviction or
     * append cannot pair a begin index with a different revision's end index.
     */
    private Bounds currentBounds() {
        return barSeries.withReadLock(() -> new Bounds(barSeries.getBeginIndex(), barSeries.getEndIndex()));
    }

    private record Bounds(int begin, int end) {
    }

    /**
     * Clamps a requested run window to the current series bounds. Default runs
     * create their record and iterate with this one window, so a bar appended or
     * evicted while the record is created cannot give the record and the run
     * different windows.
     */
    private Bounds clampToCurrentBounds(int startIndex, int finishIndex) {
        Bounds bounds = currentBounds();
        return new Bounds(Math.max(startIndex, bounds.begin()), Math.min(finishIndex, bounds.end()));
    }

    private TradingRecord createDefaultTradingRecord(TradeType tradeType, Bounds window) {
        TradingRecord tradingRecord = tradingRecordFactory.create(tradeType, window.begin(), window.end(),
                transactionCostModel, holdingCostModel);
        if (tradingRecord == null) {
            throw new IllegalStateException("tradingRecordFactory returned null");
        }
        return tradingRecord;
    }

    /**
     * Executes walk-forward testing for one strategy using the strategy starting
     * trade type and unit amount.
     *
     * @param strategy strategy to execute
     * @param config   walk-forward configuration
     * @return walk-forward execution result
     * @since 0.22.4
     */
    public StrategyWalkForwardExecutionResult runWalkForward(Strategy strategy, WalkForwardConfig config) {
        Objects.requireNonNull(strategy, "strategy");
        Num unitAmount = barSeries.numFactory().one();
        return runWalkForward(strategy, strategy.getStartingType(), unitAmount, config, null);
    }

    /**
     * Executes walk-forward testing for one strategy using the provided entry trade
     * type and unit amount.
     *
     * @param strategy  strategy to execute
     * @param tradeType trade type used to open positions
     * @param config    walk-forward configuration
     * @return walk-forward execution result
     * @since 0.22.4
     */
    public StrategyWalkForwardExecutionResult runWalkForward(Strategy strategy, TradeType tradeType,
            WalkForwardConfig config) {
        Num unitAmount = barSeries.numFactory().one();
        return runWalkForward(strategy, tradeType, unitAmount, config, null);
    }

    /**
     * Executes walk-forward testing for one strategy with explicit amount.
     *
     * @param strategy  strategy to execute
     * @param tradeType trade type used to open positions
     * @param amount    amount used to open/close trades
     * @param config    walk-forward configuration
     * @return walk-forward execution result
     * @since 0.22.4
     */
    public StrategyWalkForwardExecutionResult runWalkForward(Strategy strategy, TradeType tradeType, Num amount,
            WalkForwardConfig config) {
        return runWalkForward(strategy, tradeType, amount, config, null);
    }

    /**
     * Executes walk-forward testing for one strategy using the provided entry trade
     * type and dynamic entry position sizer.
     *
     * @param strategy      strategy to execute
     * @param tradeType     trade type used to open positions
     * @param positionSizer dynamic entry position sizer
     * @param config        walk-forward configuration
     * @return walk-forward execution result
     * @since 0.22.9
     */
    public StrategyWalkForwardExecutionResult runWalkForward(Strategy strategy, TradeType tradeType,
            PositionSizer positionSizer, WalkForwardConfig config) {
        return runWalkForward(strategy, tradeType, positionSizer, config, null);
    }

    /**
     * Executes walk-forward testing for one strategy with a dynamic entry position
     * sizer.
     *
     * @param strategy      strategy to execute
     * @param positionSizer dynamic entry position sizer
     * @param config        walk-forward configuration
     * @return walk-forward execution result
     * @since 0.22.9
     */
    public StrategyWalkForwardExecutionResult runWalkForward(Strategy strategy, PositionSizer positionSizer,
            WalkForwardConfig config) {
        Objects.requireNonNull(strategy, "strategy");
        return runWalkForward(strategy, strategy.getStartingType(), positionSizer, config, null);
    }

    /**
     * Executes walk-forward testing for one strategy with optional per-fold
     * progress updates.
     *
     * @param strategy         strategy to execute
     * @param tradeType        trade type used to open positions
     * @param amount           amount used to open/close trades
     * @param config           walk-forward configuration
     * @param progressCallback optional callback receiving completed fold count
     * @return walk-forward execution result
     * @since 0.22.4
     */
    public StrategyWalkForwardExecutionResult runWalkForward(Strategy strategy, TradeType tradeType, Num amount,
            WalkForwardConfig config, Consumer<Integer> progressCallback) {
        StrategyWalkForwardExecutor executor = new StrategyWalkForwardExecutor(this, new TradingStatementGenerator(),
                new AnchoredExpandingWalkForwardSplitter());
        return executor.execute(strategy, tradeType, amount, config, progressCallback);
    }

    /**
     * Executes walk-forward testing for one strategy with dynamic entry amount
     * provider and optional per-fold progress updates.
     *
     * @param strategy         strategy to execute
     * @param tradeType        trade type used to open positions
     * @param positionSizer    dynamic entry position sizer
     * @param config           walk-forward configuration
     * @param progressCallback optional callback receiving completed fold count
     * @return walk-forward execution result
     * @since 0.22.9
     */
    public StrategyWalkForwardExecutionResult runWalkForward(Strategy strategy, TradeType tradeType,
            PositionSizer positionSizer, WalkForwardConfig config, Consumer<Integer> progressCallback) {
        StrategyWalkForwardExecutor executor = new StrategyWalkForwardExecutor(this, new TradingStatementGenerator(),
                new AnchoredExpandingWalkForwardSplitter());
        return executor.execute(strategy, tradeType, positionSizer, config, progressCallback);
    }

    private TradingRecord runWithPositionSizer(Strategy strategy, TradingRecord tradingRecord,
            PositionSizer positionSizer, int startIndex, int finishIndex) {
        Objects.requireNonNull(tradingRecord, "tradingRecord");
        Objects.requireNonNull(positionSizer, "positionSizer");
        TradeType runTradeType = tradingRecord.getStartingType();
        return run(strategy, tradingRecord, startIndex, finishIndex,
                (index, runSeries) -> amountForNextOperation(positionSizer, index, strategy, tradingRecord,
                        runTradeType, runSeries));
    }

    /**
     * Runs the strategy over {@code [startIndex, finishIndex]}, clamped to the
     * series bounds when the run starts. Execution models and sizing see the series
     * only through that window, so no trade is ever placed after it: a position
     * still open at the window end stays open for criteria to handle through their
     * open-position policy, instead of being closed with prices the window never
     * saw.
     */
    private TradingRecord run(Strategy strategy, TradingRecord tradingRecord, int startIndex, int finishIndex,
            AmountResolver amountResolver) {
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(tradingRecord, "tradingRecord");
        Objects.requireNonNull(amountResolver, "amountResolver");
        // Both bounds come from one read scope, so an eviction between them
        // cannot pair a stale begin with a newer end.
        Bounds bounds = currentBounds();
        int runBeginIndex = Math.max(startIndex, bounds.begin());
        int runEndIndex = Math.min(finishIndex, bounds.end());
        RunWindowBarSeries runSeries = new RunWindowBarSeries(barSeries, runEndIndex);

        if (log.isTraceEnabled()) {
            log.trace("Running strategy (indexes: {} -> {}): {} (starting with {})", runBeginIndex, runEndIndex,
                    strategy, tradingRecord.getStartingType());
        }

        try (AccelerationRuntime.Scope ignored = AccelerationRuntime.open(barSeries, runBeginIndex, runEndIndex)) {
            int lastProcessedIndex = runEndIndex;
            if (runBeginIndex <= runEndIndex) {
                for (int i = runBeginIndex;; i++) {
                    lastProcessedIndex = i;
                    runSeries.markBarProcessed();
                    tradeExecutionModel.onBar(i, tradingRecord, runSeries);
                    // For each bar between both indexes...
                    if (strategy.shouldOperate(i, tradingRecord)) {
                        tradeExecutionModel.execute(i, tradingRecord, runSeries, amountResolver.amount(i, runSeries));
                    }
                    if (i == runEndIndex) {
                        break;
                    }
                }
            }
            tradeExecutionModel.onRunEnd(lastProcessedIndex, tradingRecord, runSeries);
            return tradingRecord;
        }
    }

    /** Resolves the amount for an operation at an index of the run window. */
    @FunctionalInterface
    private interface AmountResolver {
        Num amount(int index, BarSeries runSeries);
    }

    private Num amountForIndex(PositionSizer positionSizer, int index, Strategy strategy, TradingRecord tradingRecord,
            TradeType tradeType, BarSeries runSeries) {
        Num amount = positionSizer.amount(positionSizerContext(index, strategy, tradingRecord, tradeType, runSeries));
        validateAmount(amount);
        return amount;
    }

    private static void validateAmount(Num amount) {
        if (amount == null || amount.isNaN()) {
            throw new IllegalArgumentException("Amount must be positive and finite");
        }

        if (amount.isNegativeOrZero() || !Double.isFinite(amount.doubleValue())) {
            throw new IllegalArgumentException("Amount must be positive and finite");
        }
    }

    private Num amountForNextOperation(PositionSizer positionSizer, int index, Strategy strategy,
            TradingRecord tradingRecord, TradeType tradeType, BarSeries runSeries) {
        if (tradingRecord.isClosed()) {
            return amountForIndex(positionSizer, index, strategy, tradingRecord, tradeType, runSeries);
        }
        return tradingRecord.getCurrentPosition().amount();
    }

    private PositionSizer.Context positionSizerContext(int index, Strategy strategy, TradingRecord tradingRecord,
            TradeType tradeType, BarSeries runSeries) {
        ExecutionTarget target = tradeExecutionModel.estimateEntryTarget(index, runSeries, tradeType);
        if (target == null) {
            target = fallbackSizingTarget(index, runSeries);
        }
        return new PositionSizer.Context(index, target.index(), target.price(), strategy, runSeries, tradeType,
                tradingRecord, transactionCostModel, holdingCostModel);
    }

    private static ExecutionTarget fallbackSizingTarget(int index, BarSeries barSeries) {
        int fallbackIndex = index;
        if (barSeries.isEmpty()) {
            return new ExecutionTarget(index, barSeries.numFactory().one());
        }
        int safeBegin = barSeries.getBeginIndex();
        int safeEnd = barSeries.getEndIndex();
        if (fallbackIndex < safeBegin) {
            fallbackIndex = safeBegin;
        } else if (fallbackIndex > safeEnd) {
            fallbackIndex = safeEnd;
        }
        return new ExecutionTarget(fallbackIndex, barSeries.getBar(fallbackIndex).getClosePrice());
    }

}
