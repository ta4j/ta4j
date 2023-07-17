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
     * Opens the position with a {@link TradeType} BUY trade.
     *
     * @return the trading record coming from the run
     */
    public TradingRecord run(Strategy strategy) {
        return run(strategy, TradeType.BUY);
    }

    /**
     * Runs the provided strategy over the managed series (from startIndex to
     * finishIndex).
     *
     * Opens the position with a {@link TradeType} BUY trade.
     *
     * @param strategy    the trading strategy
     * @param startIndex  the start index for the run (included)
     * @param finishIndex the finish index for the run (included)
     * @return the trading record coming from the run
     */
    public TradingRecord run(Strategy strategy, int startIndex, int finishIndex) {
        return run(strategy, TradeType.BUY, barSeries.one(), startIndex, finishIndex);
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
        return run(strategy, tradeType, barSeries.one());
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
        return run(strategy, tradeType, barSeries.one(), startIndex, finishIndex);
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

}
