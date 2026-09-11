/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import java.time.Instant;
import org.ta4j.core.BarSeries;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.Position;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradeFill;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * Shared helper functions for backtest execution models.
 *
 * <p>
 * Centralizes common execution concerns such as trade-side resolution and
 * mapping strategy signal bars to concrete execution index/price pairs.
 * </p>
 */
final class ExecutionModelSupport {

    private ExecutionModelSupport() {
    }

    static TradeExecutionModel.ExecutionTarget resolveExecutionTarget(int signalIndex, BarSeries barSeries,
            TradeExecutionModel.PriceSource priceSource) {
        if (signalIndex < barSeries.getBeginIndex()) {
            return null;
        }
        if (priceSource == TradeExecutionModel.PriceSource.CURRENT_CLOSE) {
            if (!hasAccessibleBar(signalIndex, barSeries)) {
                return null;
            }
            return createExecutionTarget(signalIndex, barSeries.getBar(signalIndex).getClosePrice());
        }
        // Executing on the next open requires a subsequent bar; using >= keeps the
        // check exact even when signalIndex is Integer.MAX_VALUE, where signalIndex + 1
        // would overflow and wrap around to a negative index.
        if (signalIndex >= barSeries.getEndIndex()) {
            return null;
        }
        int executionIndex = signalIndex + 1;
        if (executionIndex > barSeries.getEndIndex()) {
            return null;
        }
        return createExecutionTarget(executionIndex, barSeries.getBar(executionIndex).getOpenPrice());
    }

    private static boolean hasAccessibleBar(int signalIndex, BarSeries barSeries) {
        int rawIndex = signalIndex - barSeries.getRemovedBarsCount();
        return rawIndex >= 0 && rawIndex < barSeries.getBarData().size();
    }

    static TradeType nextTradeType(TradingRecord tradingRecord) {
        if (tradingRecord.isClosed()) {
            return tradingRecord.getStartingType();
        }
        Position currentPosition = tradingRecord.getCurrentPosition();
        if (currentPosition == null || currentPosition.getEntry() == null) {
            return tradingRecord.getStartingType();
        }
        return currentPosition.getEntry().getType().complementType();
    }

    private static TradeExecutionModel.ExecutionTarget createExecutionTarget(int index, Num price) {
        return new TradeExecutionModel.ExecutionTarget(index, price);
    }

    /**
     * Routes one execution to the trading record.
     *
     * <p>
     * Spot records keep the scalar operate path. Native futures records require a
     * complete fill because the scalar path is spot-only: the record contract, the
     * executed index and price, the next trade type and the timestamp of the
     * executed price source. Fees are deliberately left unrecorded so that the
     * record's configured contextual cost model prices the fill.
     * </p>
     *
     * @param tradingRecord target record
     * @param barSeries     executed series
     * @param target        executed index/price pair
     * @param amount        executed amount, in contracts for futures records
     * @param priceSource   source of the executed price
     * @throws IllegalStateException when a futures fill has no bar timestamp
     */
    static void execute(TradingRecord tradingRecord, BarSeries barSeries, TradeExecutionModel.ExecutionTarget target,
            Num amount, TradeExecutionModel.PriceSource priceSource) {
        FuturesContract futuresContract = tradingRecord.getFuturesContract();
        if (futuresContract == null) {
            tradingRecord.operate(target.index(), target.price(), amount);
            return;
        }
        FuturesOrderQuantitySupport.requireTradable(futuresContract, amount, target.price());
        TradeType tradeType = nextTradeType(tradingRecord);
        tradingRecord.operate(TradeFill.builder()
                .futuresContract(futuresContract)
                .index(target.index())
                .time(fillTime(barSeries, target.index(), priceSource))
                .price(target.price())
                .amount(amount)
                .side(tradeType == TradeType.BUY ? ExecutionSide.BUY : ExecutionSide.SELL)
                .build());
    }

    private static Instant fillTime(BarSeries barSeries, int index, TradeExecutionModel.PriceSource priceSource) {
        Instant time = priceSource == TradeExecutionModel.PriceSource.CURRENT_CLOSE
                ? barSeries.getBar(index).getEndTime()
                : barSeries.getBar(index).getBeginTime();
        if (time == null) {
            throw new IllegalStateException("native futures execution requires bar timestamps but bar " + index
                    + " has none; use a timestamped bar series or a spot trading record");
        }
        return time;
    }
}
