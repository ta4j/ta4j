/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * An execution model for {@link BarSeriesManager} objects.
 *
 * Used for backtesting. Instructs {@link BarSeriesManager} on how to execute
 * trades.
 */
public interface TradeExecutionModel {

    /**
     * Executes a trade in the given {@code tradingRecord}.
     *
     * @param index         the trade index from {@code barSeries}
     * @param tradingRecord the trading record to place the trade
     * @param barSeries     the bar series
     * @param amount        the trade amount
     */
    void execute(int index, TradingRecord tradingRecord, BarSeries barSeries, Num amount);

}
