/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.util.List;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradeFill;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Analysis criterion that returns the unrealized profit/loss for the position
 * active at the end of the effective series or trading-record window.
 *
 * <p>
 * Positions exited after the window are treated as open and marked at the last
 * included bar. Returns zero when no position is active at the window end.
 * </p>
 *
 * <p>
 * A native futures position is marked via
 * {@link Position#getUnrealizedProfit(Num, int)}, which measures the remaining
 * contract exposure in settlement currency and excludes the already settled
 * variation margin, execution fees and funding. An unavailable mark yields NaN
 * for remaining exposure and zero for exhausted exposure. With an empty series
 * and no defined cutoff, all recorded executions determine whether exposure
 * remains.
 * </p>
 *
 * @since 0.22.2
 */
public class OpenPositionUnrealizedProfitCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(BarSeries series, Position position) {
        return calculateAt(series, position,
                series.isEmpty() && position.getFuturesContract() != null ? Integer.MAX_VALUE : series.getEndIndex());
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        int finalIndex = series.isEmpty() && tradingRecord.getFuturesContract() != null
                ? tradingRecord.getEndIndex() == null ? Integer.MAX_VALUE : tradingRecord.getEndIndex()
                : Math.min(series.getEndIndex(), tradingRecord.getEndIndex(series));
        Num totalProfit = series.numFactory().zero();
        for (Position position : tradingRecord.getPositions()) {
            if (isOpenAt(position, finalIndex)) {
                totalProfit = totalProfit.plus(calculateAt(series, position, finalIndex));
            }
        }
        for (Position openLot : openLots(tradingRecord)) {
            if (isOpenAt(openLot, finalIndex)) {
                totalProfit = totalProfit.plus(calculateAt(series, openLot, finalIndex));
            }
        }
        return totalProfit;
    }

    /**
     * Per-lot open positions, so a lot entered after the logical end is excluded on
     * its own; the aggregated current position keeps the earliest entry.
     */
    static List<Position> openLots(TradingRecord tradingRecord) {
        List<Position> openLots = tradingRecord.getOpenPositions();
        if (!openLots.isEmpty()) {
            return openLots;
        }
        Position current = tradingRecord.getCurrentPosition();
        return current != null && (current.isOpened() || current.getFuturesContract() != null) ? List.of(current)
                : List.of();
    }

    private boolean isOpenAt(Position position, int finalIndex) {
        if (position == null || position.getEntry() == null || position.getEntry().getIndex() > finalIndex) {
            return false;
        }
        return position.getFuturesContract() != null || position.getExit() == null
                || position.getExit().getIndex() > finalIndex;
    }

    private Num calculateAt(BarSeries series, Position position, int finalIndex) {
        NumFactory factory = series.numFactory();
        if (position.getEntry() == null || position.getEntry().getIndex() > finalIndex) {
            return factory.zero();
        }
        if (position.getFuturesContract() != null) {
            Num mark = series.isEmpty() || finalIndex < series.getBeginIndex() ? NaN.NaN
                    : series.getBar(finalIndex).getClosePrice();
            return toSeriesNum(factory, unrealizedProfit(position, mark, finalIndex));
        }
        if (finalIndex < series.getBeginIndex()) {
            return factory.zero();
        }
        if (!position.isOpened()) {
            if (position.getExit().getIndex() <= finalIndex) {
                return factory.zero();
            }
            position = new Position(position.getEntry(), position.getTransactionCostModel(),
                    position.getHoldingCostModel());
        }
        Num closePrice = series.getBar(finalIndex).getClosePrice();
        Num profit = position.getProfit(finalIndex, closePrice);
        return toSeriesNum(factory, profit);
    }

    @Override
    public boolean betterThan(Num v1, Num v2) {
        return v1.isGreaterThan(v2);
    }

    private Num unrealizedProfit(Position position, Num closePrice, int endIndex) {
        if (position.getFuturesContract() == null) {
            return position.getProfit(endIndex, closePrice);
        }
        if (closePrice.isNaN()) {
            NumFactory factory = position.getEntry().getAmount().getNumFactory();
            Num entryAmount = executedAmount(position.getEntry(), endIndex, factory);
            Num exitAmount = position.getExit() == null ? factory.zero()
                    : executedAmount(position.getExit(), endIndex, factory);
            return entryAmount.isGreaterThan(exitAmount) ? NaN.NaN : factory.zero();
        }
        return position.getUnrealizedProfit(closePrice, endIndex);
    }

    private Num executedAmount(Trade trade, int endIndex, NumFactory factory) {
        Num amount = factory.zero();
        for (TradeFill fill : Trade.executionFillsOf(trade)) {
            if (fill.index() >= 0 && fill.index() <= endIndex) {
                amount = amount.plus(factory.numOf(fill.amount().getDelegate()));
            }
        }
        return amount;
    }

    private Num toSeriesNum(NumFactory factory, Num value) {
        if (value == null) {
            return factory.zero();
        }
        if (value.isNaN()) {
            return NaN.NaN;
        }
        return factory.numOf(value.getDelegate());
    }
}
