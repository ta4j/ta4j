/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * Execution model that closes any position still open when a run ends, at the
 * close of the run's last bar, and otherwise executes like the model it wraps.
 *
 * <p>
 * By default a run leaves a position open at its window end, and criteria mark
 * it to market or ignore it through their
 * {@link org.ta4j.core.analysis.OpenPositionHandling}. Marking values the
 * position at the last close but charges no exit cost, and a walk-forward fold
 * or any run whose records are chained with the next window's then drops the
 * position without ever paying to leave it. Wrapping the execution model in
 * this class ends every run flat instead: the exit is a regular trade on the
 * last bar, priced at its close like {@link TradeOnCurrentCloseModel} fills and
 * charged by the record's transaction cost model, so every criterion sees a
 * closed position. The exit never uses a bar after the run.
 * </p>
 *
 * <p>
 * {@link StrategyWalkForwardExecutor} applies this model to every fold, so
 * walk-forward folds always end flat. Wrap the model passed to
 * {@link BarSeriesManager} or {@link BacktestExecutor} to end their runs flat
 * too:
 * </p>
 *
 * <pre>{@code
 * BarSeriesManager manager = new BarSeriesManager(series, transactionCostModel, holdingCostModel,
 *         new ExitOnRunEndModel(new TradeOnNextOpenModel()));
 * }</pre>
 *
 * @since 0.25.1
 */
public final class ExitOnRunEndModel implements TradeExecutionModel {

    private final TradeExecutionModel delegate;

    /**
     * @param delegate the model executing every trade before the run ends
     * @since 0.25.1
     */
    public ExitOnRunEndModel(TradeExecutionModel delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void onBar(int index, TradingRecord tradingRecord, BarSeries barSeries) {
        delegate.onBar(index, tradingRecord, barSeries);
    }

    @Override
    public void execute(int index, TradingRecord tradingRecord, BarSeries barSeries, Num amount) {
        delegate.execute(index, tradingRecord, barSeries, amount);
    }

    @Override
    public ExecutionTarget estimateEntryTarget(int signalIndex, BarSeries barSeries, TradeType tradeType) {
        return delegate.estimateEntryTarget(signalIndex, barSeries, tradeType);
    }

    /**
     * Lets the wrapped model finalize without the run's series; no position is
     * closed, because no closing price is available.
     */
    @Override
    public void onRunEnd(int lastProcessedIndex, TradingRecord tradingRecord) {
        delegate.onRunEnd(lastProcessedIndex, tradingRecord);
    }

    /**
     * Lets the wrapped model finalize first (for example expiring pending orders,
     * which may commit a partial fill), then closes the position still open at the
     * close of {@code lastProcessedIndex}.
     */
    @Override
    public void onRunEnd(int lastProcessedIndex, TradingRecord tradingRecord, BarSeries barSeries) {
        delegate.onRunEnd(lastProcessedIndex, tradingRecord, barSeries);
        if (tradingRecord.isClosed()) {
            return;
        }
        ExecutionTarget target = ExecutionModelSupport.resolveExecutionTarget(lastProcessedIndex, barSeries,
                PriceSource.CURRENT_CLOSE);
        if (target != null) {
            tradingRecord.operate(target.index(), target.price(), tradingRecord.getCurrentPosition().amount());
        }
    }
}
