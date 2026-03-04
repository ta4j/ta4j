/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import java.util.Objects;
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
         * @return a trading record instance for the run
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
     * Runs the provided strategy over the managed series using the supplied trading
     * record.
     *
     * <p>
     * This allows callers to backtest with alternate {@link TradingRecord}
     * implementations (for example {@code LiveTradingRecord}) while reusing
     * {@link BarSeriesManager}'s execution loop.
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
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(tradingRecord, "tradingRecord");
        Objects.requireNonNull(amount, "amount");
        int runBeginIndex = Math.max(startIndex, barSeries.getBeginIndex());
        int runEndIndex = Math.min(finishIndex, barSeries.getEndIndex());

        if (log.isTraceEnabled()) {
            log.trace("Running strategy (indexes: {} -> {}): {} (starting with {})", runBeginIndex, runEndIndex,
                    strategy, tradingRecord.getStartingType());
        }

        for (int i = runBeginIndex; i <= runEndIndex; i++) {
            tradeExecutionModel.onBar(i, tradingRecord, barSeries);
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
                tradeExecutionModel.onBar(i, tradingRecord, barSeries);
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

}
