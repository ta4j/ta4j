/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan, Ta4j Organization & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.Order.OrderType;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * A manager for {@link TimeSeries} objects.
 * <p></p>
 * Used for backtesting.
 * Allows to run a {@link Strategy trading strategy} over the managed time series.
 */
public class TimeSeriesManager {

    /** The logger */
    private final Logger log = LoggerFactory.getLogger(getClass());

    /** The managed time series */
    private TimeSeries timeSeries;

    /**
     * Constructor.
     */
    public TimeSeriesManager() {
	}

    /**
     * Constructor.
     * @param timeSeries the time series to be managed
     */
    public TimeSeriesManager(TimeSeries timeSeries) {
    	this.timeSeries = timeSeries;
    }

    /**
     * @param timeSeries the time series to be managed
     */
    public void setTimeSeries(TimeSeries timeSeries) {
		this.timeSeries = timeSeries;
	}

    /**
     * @return the managed time series
     */
    public TimeSeries getTimeSeries() {
		return timeSeries;
	}

    /**
     * Runs the provided strategy over the managed series.
     * <p>
     * Opens the trades with {@link OrderType} BUY @return the trading record coming from the run
     */
    public TradingRecord run(Strategy strategy) {
        return run(strategy, OrderType.BUY);
    }

    /**
     * Runs the provided strategy over the managed series (from startIndex to finishIndex).
     * <p>
     * Opens the trades with {@link OrderType} BUY orders.
     * @param strategy the trading strategy
     * @param startIndex the start index for the run (included)
     * @param finishIndex the finish index for the run (included)
     * @return the trading record coming from the run
     */
    public TradingRecord run(Strategy strategy, int startIndex, int finishIndex) {
        return run(strategy, OrderType.BUY, NaN, startIndex, finishIndex);
    }

    /**
     * Runs the provided strategy over the managed series.
     * <p>
     * Opens the trades with {@link OrderType} BUY orders.
     * @param strategy the trading strategy
     * @param orderType the {@link OrderType} used to open the trades
     * @return the trading record coming from the run
     */
    public TradingRecord run(Strategy strategy, OrderType orderType) {
        return run(strategy, orderType, NaN);
    }

    /**
     * Runs the provided strategy over the managed series (from startIndex to finishIndex).
     * <p>
     * Opens the trades with {@link OrderType} BUYorders.
     * @param strategy the trading strategy
     * @param orderType the {@link OrderType} used to open the trades
     * @param startIndex the start index for the run (included)
     * @param finishIndex the finish index for the run (included)
     * @return the trading record coming from the run
     */
    public TradingRecord run(Strategy strategy, OrderType orderType, int startIndex, int finishIndex) {
        return run(strategy, orderType, NaN, startIndex, finishIndex);
    }

    /**
     * Runs the provided strategy over the managed series.
     * <p>
     * @param strategy the trading strategy
     * @param orderType the {@link OrderType} used to open the trades
     * @param amount the amount used to open/close the trades
     * @return the trading record coming from the run
     */
    public TradingRecord run(Strategy strategy, OrderType orderType, Num amount) {
        return run(strategy, orderType, amount, timeSeries.getBeginIndex(), timeSeries.getEndIndex());
    }

    /**
     * Runs the provided strategy over the managed series (from startIndex to finishIndex).
     * <p>
     * @param strategy the trading strategy
     * @param orderType the {@link OrderType} used to open the trades
     * @param amount the amount used to open/close the trades
     * @param startIndex the start index for the run (included)
     * @param finishIndex the finish index for the run (included)
     * @return the trading record coming from the run
     */
    public TradingRecord run(Strategy strategy, OrderType orderType, Num amount, int startIndex, int finishIndex) {

        int runBeginIndex = Math.max(startIndex, timeSeries.getBeginIndex());
        int runEndIndex = Math.min(finishIndex, timeSeries.getEndIndex());

        log.trace("Running strategy (indexes: {} -> {}): {} (starting with {})", runBeginIndex, runEndIndex, strategy, orderType);
        TradingRecord tradingRecord = new BaseTradingRecord(orderType);
        for (int i = runBeginIndex; i <= runEndIndex; i++) {
            // For each bar between both indexes...
            if (strategy.shouldOperate(i, tradingRecord)) {
                tradingRecord.operate(i, timeSeries.getBar(i).getClosePrice(), amount);
            }
        }

        if (!tradingRecord.isClosed()) {
            // If the last trade is still opened, we search out of the run end index.
            // May works if the end index for this run was inferior to the actual number of bars
        	int seriesMaxSize = Math.max(timeSeries.getEndIndex() + 1, timeSeries.getBarData().size());
            for (int i = runEndIndex + 1; i < seriesMaxSize; i++) {
                // For each bar after the end index of this run...
                // --> Trying to close the last trade
                if (strategy.shouldOperate(i, tradingRecord)) {
                    tradingRecord.operate(i, timeSeries.getBar(i).getClosePrice(), amount);
                    break;
                }
            }
        }
        return tradingRecord;
    }

}
