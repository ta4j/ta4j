/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.Num;
import org.ta4j.core.reports.TradingStatementGenerator;
import org.ta4j.core.walkforward.AnchoredExpandingWalkForwardSplitter;
import org.ta4j.core.walkforward.WalkForwardConfig;

/**
 * A manager for {@link BarSeries} objects used for backtesting. Allows to run a
 * {@link Strategy trading strategy} over the managed bar series.
 *
 * <p>
 * Default {@code run(...)} overloads create a fresh trading record through this
 * manager's configured {@link TradingRecordFactory}. Existing behavior remains
 * unchanged by default ({@link BaseTradingRecord}), while callers can inject a
 * custom record implementation for unified backtest/live execution paths.
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
     * Provides a dynamic entry amount for each strategy operation opportunity.
     *
     * <p>
     * The index corresponds to the bar index passed to
     * {@link TradeExecutionModel#execute}. Implementations should return a strictly
     * positive amount when a strategy opens a position. Exits close the currently
     * open amount so a changing provider does not over-close an existing position.
     * </p>
     * <p>
     * Implementations may use any subset of the context parameters; they are
     * included so one provider can size entries differently across strategies,
     * series, or trade directions.
     * </p>
     * <p>
     * When used with {@link BacktestExecutor} methods that evaluate strategies in
     * parallel, implementations may be called concurrently and should be
     * thread-safe.
     * </p>
     *
     * @since 0.22.7
     */
    @FunctionalInterface
    public interface AmountProvider {
        /**
         * Provides the amount used to open a new position.
         *
         * @param index     bar index where execution is attempted
         * @param strategy  strategy being evaluated
         * @param barSeries backtested bar series
         * @param tradeType strategy entry trade type
         * @return amount used for entry execution
         * @since 0.22.7
         */
        @SuppressWarnings("unused")
        Num amount(int index, Strategy strategy, BarSeries barSeries, TradeType tradeType);
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
     * @return the managed bar series
     */
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
     * @param strategy    the trading strategy
     * @param startIndex  the start index for the run (included)
     * @param finishIndex the finish index for the run (included)
     * @return the trading record coming from the run
     */
    public TradingRecord run(Strategy strategy, int startIndex, int finishIndex) {
        return run(strategy, strategy.getStartingType(), barSeries.numFactory().one(), startIndex, finishIndex);
    }

    /**
     * Runs the provided strategy over the managed series.
     *
     * Opens the position with a trade of {@link TradeType tradeType}.
     *
     * @param strategy  the trading strategy
     * @param tradeType the {@link TradeType} used to open the position
     * @return the trading record coming from the run
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
        return run(strategy, tradeType, amount, barSeries.getBeginIndex(), barSeries.getEndIndex());
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
        TradingRecord tradingRecord = createDefaultTradingRecord(tradeType, startIndex, finishIndex);
        return run(strategy, tradingRecord, amount, startIndex, finishIndex);
    }

    /**
     * Runs the provided strategy over the managed series using a dynamic entry
     * amount provider.
     *
     * @param strategy       strategy to execute
     * @param tradeType      the {@link TradeType} used to open the position
     * @param amountProvider provider returning the amount used to open trades
     * @return the trading record coming from the run
     * @since 0.22.7
     */
    public TradingRecord run(Strategy strategy, TradeType tradeType, AmountProvider amountProvider) {
        return run(strategy, tradeType, amountProvider, barSeries.getBeginIndex(), barSeries.getEndIndex());
    }

    /**
     * Runs the provided strategy over the managed series using a dynamic entry
     * amount provider.
     *
     * @param strategy       strategy to execute
     * @param amountProvider provider returning the amount used to open trades
     * @return the trading record coming from the run
     * @since 0.22.7
     */
    public TradingRecord run(Strategy strategy, AmountProvider amountProvider) {
        Objects.requireNonNull(strategy, "strategy");
        return run(strategy, strategy.getStartingType(), amountProvider);
    }

    /**
     * Runs the provided strategy over the managed series (from startIndex to
     * finishIndex) using the strategy starting type and a dynamic entry amount
     * provider.
     *
     * @param strategy       strategy to execute
     * @param amountProvider provider returning the amount used to open trades
     * @param startIndex     the start index for the run (included)
     * @param finishIndex    the finish index for the run (included)
     * @return the trading record coming from the run
     * @since 0.22.7
     */
    public TradingRecord run(Strategy strategy, AmountProvider amountProvider, int startIndex, int finishIndex) {
        Objects.requireNonNull(strategy, "strategy");
        return run(strategy, strategy.getStartingType(), amountProvider, startIndex, finishIndex);
    }

    /**
     * Runs the provided strategy over the managed series (from startIndex to
     * finishIndex) using a dynamic entry amount provider.
     *
     * @param strategy       strategy to execute
     * @param tradeType      the {@link TradeType} used to open the position
     * @param amountProvider provider returning the amount used to open trades
     * @param startIndex     the start index for the run (included)
     * @param finishIndex    the finish index for the run (included)
     * @return the trading record coming from the run
     * @since 0.22.7
     */
    public TradingRecord run(Strategy strategy, TradeType tradeType, AmountProvider amountProvider, int startIndex,
            int finishIndex) {
        TradingRecord tradingRecord = createDefaultTradingRecord(tradeType, startIndex, finishIndex);
        return run(strategy, tradingRecord, amountProvider, startIndex, finishIndex);
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
        return run(strategy, tradingRecord, amount, barSeries.getBeginIndex(), barSeries.getEndIndex());
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
        return run(strategy, tradingRecord, startIndex, finishIndex, index -> amount);
    }

    /**
     * Runs the provided strategy over the managed series using a dynamic entry
     * amount provider and a supplied trading record.
     *
     * @param strategy       strategy to execute
     * @param tradingRecord  the trading record instance to mutate
     * @param amountProvider provider returning the amount used to open trades
     * @return the supplied trading record after execution
     * @since 0.22.7
     */
    public TradingRecord run(Strategy strategy, TradingRecord tradingRecord, AmountProvider amountProvider) {
        return run(strategy, tradingRecord, amountProvider, barSeries.getBeginIndex(), barSeries.getEndIndex());
    }

    /**
     * Runs the provided strategy over the managed series (from startIndex to
     * finishIndex) using a supplied trading record and dynamic entry amount
     * provider.
     *
     * @param strategy       strategy to execute
     * @param tradingRecord  the trading record instance to mutate
     * @param amountProvider provider returning the amount used to open trades
     * @param startIndex     the start index for the run (included)
     * @param finishIndex    the finish index for the run (included)
     * @return the supplied trading record after execution
     * @since 0.22.7
     */
    public TradingRecord run(Strategy strategy, TradingRecord tradingRecord, AmountProvider amountProvider,
            int startIndex, int finishIndex) {
        return runWithAmountProvider(strategy, tradingRecord, amountProvider, startIndex, finishIndex);
    }

    private TradingRecord createDefaultTradingRecord(TradeType tradeType, int startIndex, int finishIndex) {
        int clampedStartIndex = Math.max(startIndex, barSeries.getBeginIndex());
        int clampedEndIndex = Math.min(finishIndex, barSeries.getEndIndex());
        TradingRecord tradingRecord = tradingRecordFactory.create(tradeType, clampedStartIndex, clampedEndIndex,
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
     * type and dynamic entry amount provider.
     *
     * @param strategy       strategy to execute
     * @param tradeType      trade type used to open positions
     * @param amountProvider dynamic entry amount provider
     * @param config         walk-forward configuration
     * @return walk-forward execution result
     * @since 0.22.7
     */
    public StrategyWalkForwardExecutionResult runWalkForward(Strategy strategy, TradeType tradeType,
            AmountProvider amountProvider, WalkForwardConfig config) {
        return runWalkForward(strategy, tradeType, amountProvider, config, null);
    }

    /**
     * Executes walk-forward testing for one strategy with dynamic entry amount
     * provider.
     *
     * @param strategy       strategy to execute
     * @param amountProvider dynamic entry amount provider
     * @param config         walk-forward configuration
     * @return walk-forward execution result
     * @since 0.22.7
     */
    public StrategyWalkForwardExecutionResult runWalkForward(Strategy strategy, AmountProvider amountProvider,
            WalkForwardConfig config) {
        Objects.requireNonNull(strategy, "strategy");
        return runWalkForward(strategy, strategy.getStartingType(), amountProvider, config, null);
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
     * @param amountProvider   dynamic entry amount provider
     * @param config           walk-forward configuration
     * @param progressCallback optional callback receiving completed fold count
     * @return walk-forward execution result
     * @since 0.22.7
     */
    public StrategyWalkForwardExecutionResult runWalkForward(Strategy strategy, TradeType tradeType,
            AmountProvider amountProvider, WalkForwardConfig config, Consumer<Integer> progressCallback) {
        StrategyWalkForwardExecutor executor = new StrategyWalkForwardExecutor(this, new TradingStatementGenerator(),
                new AnchoredExpandingWalkForwardSplitter());
        return executor.execute(strategy, tradeType, amountProvider, config, progressCallback);
    }

    private TradingRecord runWithAmountProvider(Strategy strategy, TradingRecord tradingRecord,
            AmountProvider amountProvider, int startIndex, int finishIndex) {
        Objects.requireNonNull(tradingRecord, "tradingRecord");
        Objects.requireNonNull(amountProvider, "amountProvider");
        TradeType runTradeType = tradingRecord.getStartingType();
        return run(strategy, tradingRecord, startIndex, finishIndex,
                index -> amountForNextOperation(amountProvider, index, strategy, tradingRecord, runTradeType));
    }

    private TradingRecord run(Strategy strategy, TradingRecord tradingRecord, int startIndex, int finishIndex,
            IntFunction<Num> amountResolver) {
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(tradingRecord, "tradingRecord");
        Objects.requireNonNull(amountResolver, "amountResolver");
        int runBeginIndex = Math.max(startIndex, barSeries.getBeginIndex());
        int runEndIndex = Math.min(finishIndex, barSeries.getEndIndex());

        if (log.isTraceEnabled()) {
            log.trace("Running strategy (indexes: {} -> {}): {} (starting with {})", runBeginIndex, runEndIndex,
                    strategy, tradingRecord.getStartingType());
        }

        int lastProcessedIndex = runEndIndex;
        for (int i = runBeginIndex; i <= runEndIndex; i++) {
            lastProcessedIndex = i;
            tradeExecutionModel.onBar(i, tradingRecord, barSeries);
            // For each bar between both indexes...
            if (strategy.shouldOperate(i, tradingRecord)) {
                tradeExecutionModel.execute(i, tradingRecord, barSeries, amountResolver.apply(i));
            }
        }

        if (!tradingRecord.isClosed() && runEndIndex == barSeries.getEndIndex()) {
            // If the last position is still open and there are still bars after the
            // endIndex of the barSeries, then we execute the strategy on these bars
            // to give an opportunity to close this position.
            int seriesMaxSize = Math.max(barSeries.getEndIndex() + 1, barSeries.getBarData().size());
            for (int i = runEndIndex + 1; i < seriesMaxSize; i++) {
                lastProcessedIndex = i;
                tradeExecutionModel.onBar(i, tradingRecord, barSeries);
                // For each bar after the end index of this run...
                // --> Trying to close the last position
                if (strategy.shouldOperate(i, tradingRecord)) {
                    tradeExecutionModel.execute(i, tradingRecord, barSeries, amountResolver.apply(i));
                    break;
                }
            }
        }
        tradeExecutionModel.onRunEnd(lastProcessedIndex, tradingRecord);
        return tradingRecord;
    }

    private Num amountForIndex(AmountProvider amountProvider, int index, Strategy strategy, TradeType tradeType) {
        Num amount = amountProvider.amount(index, strategy, barSeries, tradeType);
        if (amount == null) {
            throw new IllegalArgumentException("Amount provider returned null");
        }
        if (amount.isNaN()) {
            throw new IllegalArgumentException("Amount provider returned NaN");
        }
        Num zero = barSeries.numFactory().zero();
        if (amount.isLessThanOrEqual(zero)) {
            throw new IllegalArgumentException("Amount provider returned non-positive amount");
        }
        return amount;
    }

    private Num amountForNextOperation(AmountProvider amountProvider, int index, Strategy strategy,
            TradingRecord tradingRecord, TradeType tradeType) {
        if (tradingRecord.isClosed()) {
            return amountForIndex(amountProvider, index, strategy, tradeType);
        }
        Num amount = tradingRecord.getCurrentPosition().amount();
        if (amount == null || amount.isNaN() || !amount.isPositive()) {
            throw new IllegalStateException("Current position amount must be positive");
        }
        return amount;
    }

}
