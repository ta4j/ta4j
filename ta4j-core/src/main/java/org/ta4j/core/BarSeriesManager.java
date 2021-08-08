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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.Num;

/**
 * A manager for {@link BarSeries} objects.
 *
 * Used for backtesting. Allows to run a {@link Strategy trading strategy} over
 * the managed bar series.
 */
public class BarSeriesManager {

    /** The logger */
    private final Logger log = LoggerFactory.getLogger(getClass());

    /** The managed bar series */
    private final BarSeries barSeries;

    /** The trading cost models */
    private CostModel transactionCostModel;
    private CostModel holdingCostModel;

    /**
     * Constructor.
     * 
     * @param barSeries the bar series to be managed
     */
    public BarSeriesManager(BarSeries barSeries) {
        this(barSeries, new ZeroCostModel(), new ZeroCostModel());
    }

    /**
     * Constructor.
     * 
     * @param barSeries            the bar series to be managed
     * @param transactionCostModel the cost model for transactions of the asset
     * @param holdingCostModel     the cost model for holding asset (e.g. borrowing)
     */
    public BarSeriesManager(BarSeries barSeries, CostModel transactionCostModel, CostModel holdingCostModel) {
        this.barSeries = barSeries;
        this.transactionCostModel = transactionCostModel;
        this.holdingCostModel = holdingCostModel;
    }

    /**
     * @return the managed bar series
     */
    public BarSeries getBarSeries() {
        return barSeries;
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
        return run(strategy, TradeType.BUY, barSeries.numOf(1), startIndex, finishIndex);
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
        return run(strategy, tradeType, barSeries.numOf(1));
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
        return run(strategy, tradeType, barSeries.numOf(1), startIndex, finishIndex);
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
        TradingRecord tradingRecord = new BaseTradingRecord(tradeType, transactionCostModel, holdingCostModel);
        for (int i = runBeginIndex; i <= runEndIndex; i++) {
            // For each bar between both indexes...
            if (strategy.shouldOperate(i, tradingRecord)) {
                tradingRecord.operate(i, barSeries.getBar(i).getClosePrice(), amount);
            }
        }

        if (!tradingRecord.isClosed()) {
            // If the last position is still opened, we search out of the run end index.
            // May works if the end index for this run was inferior to the actual number of
            // bars
            int seriesMaxSize = Math.max(barSeries.getEndIndex() + 1, barSeries.getBarData().size());
            for (int i = runEndIndex + 1; i < seriesMaxSize; i++) {
                // For each bar after the end index of this run...
                // --> Trying to close the last position
                if (strategy.shouldOperate(i, tradingRecord)) {
                    tradingRecord.operate(i, barSeries.getBar(i).getClosePrice(), amount);
                    break;
                }
            }
        }
        return tradingRecord;
    }

}
