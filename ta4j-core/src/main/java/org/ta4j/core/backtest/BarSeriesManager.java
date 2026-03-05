/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import java.util.Objects;
import java.util.function.Consumer;

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
 */
public class BarSeriesManager {

    /** The logger */
    private static final Logger log = LoggerFactory.getLogger(BarSeriesManager.class);

    /** The managed bar series */
    private final BarSeries barSeries;

    /** The trading cost models */
    private final CostModel transactionCostModel;
    private final CostModel holdingCostModel;

    /** The trade execution model to use */
    private final TradeExecutionModel tradeExecutionModel;

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
        this.barSeries = barSeries;
        this.transactionCostModel = transactionCostModel;
        this.holdingCostModel = holdingCostModel;
        this.tradeExecutionModel = new TradeOnNextOpenModel();
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
        this.barSeries = barSeries;
        this.transactionCostModel = transactionCostModel;
        this.holdingCostModel = holdingCostModel;
        this.tradeExecutionModel = tradeExecutionModel;
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

        int runBeginIndex = Math.max(startIndex, barSeries.getBeginIndex());
        int runEndIndex = Math.min(finishIndex, barSeries.getEndIndex());

        if (log.isTraceEnabled()) {
            log.trace("Running strategy (indexes: {} -> {}): {} (starting with {})", runBeginIndex, runEndIndex,
                    strategy, tradeType);
        }

        TradingRecord tradingRecord = new BaseTradingRecord(tradeType, runBeginIndex, runEndIndex, transactionCostModel,
                holdingCostModel);

        for (int i = runBeginIndex; i <= runEndIndex; i++) {
            // For each bar between both indexes...
            if (strategy.shouldOperate(i, tradingRecord)) {
                tradeExecutionModel.execute(i, tradingRecord, barSeries, amount);
            }
        }

        if (!tradingRecord.isClosed() && runEndIndex == barSeries.getEndIndex()) {
            // If the last position is still open and there are still bars after the
            // endIndex of the barSeries, then we execute the strategy on these bars
            // to give an opportunity to close this position.
            int seriesMaxSize = Math.max(barSeries.getEndIndex() + 1, barSeries.getBarData().size());
            for (int i = runEndIndex + 1; i < seriesMaxSize; i++) {
                // For each bar after the end index of this run...
                // --> Trying to close the last position
                if (strategy.shouldOperate(i, tradingRecord)) {
                    tradeExecutionModel.execute(i, tradingRecord, barSeries, amount);
                    break;
                }
            }
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

}
