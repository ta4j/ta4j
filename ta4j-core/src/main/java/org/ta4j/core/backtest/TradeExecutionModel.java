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
     * Processes per-bar execution state before strategy signals are evaluated.
     *
     * <p>
     * Implementations can use this hook to progress pending orders (for example,
     * stop/limit orders with partial fills) even when no new strategy signal is
     * emitted on the current bar.
     * </p>
     *
     * @param index         current bar index
     * @param tradingRecord trading record to mutate
     * @param barSeries     bar series
     * @since 0.22.3
     */
    default void onBar(int index, TradingRecord tradingRecord, BarSeries barSeries) {
        // Default no-op for immediate execution models.
    }

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
