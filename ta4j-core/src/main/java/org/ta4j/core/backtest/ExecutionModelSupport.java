/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Trade.TradeType;
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

    static ExecutionTarget resolveExecutionTarget(int signalIndex, BarSeries barSeries,
            TradeExecutionModel.PriceSource priceSource) {
        if (signalIndex < barSeries.getBeginIndex() || signalIndex > barSeries.getEndIndex()) {
            return null;
        }
        if (priceSource == TradeExecutionModel.PriceSource.CURRENT_CLOSE) {
            return new ExecutionTarget(signalIndex, barSeries.getBar(signalIndex).getClosePrice());
        }
        int executionIndex = signalIndex + 1;
        if (executionIndex > barSeries.getEndIndex()) {
            return null;
        }
        return new ExecutionTarget(executionIndex, barSeries.getBar(executionIndex).getOpenPrice());
    }

    static TradeType nextTradeType(TradingRecord tradingRecord) {
        if (tradingRecord.isClosed()) {
            return tradingRecord.getStartingType();
        }
        return tradingRecord.getCurrentPosition().getEntry().getType().complementType();
    }

    record ExecutionTarget(int index, Num price) {
    }
}
