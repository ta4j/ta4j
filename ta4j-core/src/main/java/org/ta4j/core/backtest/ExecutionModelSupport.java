/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
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
        if (signalIndex < barSeries.getBeginIndex()) {
            return null;
        }
        if (priceSource == TradeExecutionModel.PriceSource.CURRENT_CLOSE) {
            if (!hasAccessibleBar(signalIndex, barSeries)) {
                return null;
            }
            return new ExecutionTarget(signalIndex, barSeries.getBar(signalIndex).getClosePrice());
        }
        if (signalIndex > barSeries.getEndIndex()) {
            return null;
        }
        int executionIndex = signalIndex + 1;
        if (executionIndex > barSeries.getEndIndex()) {
            return null;
        }
        return new ExecutionTarget(executionIndex, barSeries.getBar(executionIndex).getOpenPrice());
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

    record ExecutionTarget(int index, Num price) {
    }
}
