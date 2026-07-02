/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.num.Num;

/**
 * An execution model for {@link BarSeriesManager} objects.
 *
 * Used for backtesting. Instructs {@link BarSeriesManager} on how to execute
 * trades.
 *
 * <p>
 * Selection guidance:
 * </p>
 * <ul>
 * <li>Use {@link TradeOnNextOpenModel} for conservative signal-at-close,
 * fill-next-open simulation.</li>
 * <li>Use {@link TradeOnCurrentCloseModel} when your strategy intentionally
 * fills on bar close.</li>
 * <li>Use {@link SlippageExecutionModel} when you need directional price-impact
 * assumptions.</li>
 * <li>Use {@link StopLimitExecutionModel} when pending-order lifecycle and
 * partial fills matter.</li>
 * </ul>
 */
public interface TradeExecutionModel {

    /**
     * Represents the estimated execution bar and fill price for one dynamic sizing
     * context lookup.
     *
     * @since 0.22.9
     */
    record ExecutionTarget(int index, Num price) {
    }

    /**
     * Common price-source contract for execution models.
     *
     * @since 0.22.4
     */
    enum PriceSource {
        /** Use the current bar close price. */
        CURRENT_CLOSE,
        /** Use the next bar open price. */
        NEXT_OPEN
    }

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
     * @since 0.22.4
     */
    default void onBar(int index, TradingRecord tradingRecord, BarSeries barSeries) {
        // Default no-op for immediate execution models.
    }

    /**
     * Finalizes model state when a {@link BarSeriesManager} run ends.
     *
     * <p>
     * Implementations can use this hook to expire or flush pending orders that
     * would otherwise be stranded when no more bars will be processed.
     * </p>
     *
     * @param lastProcessedIndex last bar index examined during the run
     * @param tradingRecord      trading record to mutate
     * @since 0.22.4
     */
    default void onRunEnd(int lastProcessedIndex, TradingRecord tradingRecord) {
        // Default no-op for immediate execution models.
    }

    /**
     * Estimates the bar index and price used by dynamic position sizing for a
     * strategy signal.
     *
     * <p>
     * The returned context is only required by {@link BarSeriesManager} when a
     * {@link PositionSizer} is used. Implementations that execute on a different
     * bar or price than next-open must override this method.
     * </p>
     *
     * @param signalIndex signal index emitted by the strategy
     * @param barSeries   bar series being backtested
     * @param tradeType   trade type of the prospective entry
     * @return an execution target used for sizing, or {@code null} if no target can
     *         be resolved
     * @since 0.22.9
     */
    default ExecutionTarget estimateEntryTarget(int signalIndex, BarSeries barSeries, TradeType tradeType) {
        return ExecutionModelSupport.resolveExecutionTarget(signalIndex, barSeries, PriceSource.NEXT_OPEN);
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
