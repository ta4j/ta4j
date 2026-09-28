/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
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
 * @since 0.22.2
 */
public class OpenPositionUnrealizedProfitCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(BarSeries series, Position position) {
        return calculateAt(series, position, series.getEndIndex());
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        int finalIndex = Math.min(series.getEndIndex(), tradingRecord.getEndIndex(series));
        Num totalProfit = series.numFactory().zero();
        for (Position position : tradingRecord.getPositions()) {
            if (isOpenAt(position, finalIndex)) {
                totalProfit = totalProfit.plus(calculateAt(series, position, finalIndex));
            }
        }
        Position current = tradingRecord.getCurrentPosition();
        if (current != null && current.isOpened() && isOpenAt(current, finalIndex)) {
            totalProfit = totalProfit.plus(calculateAt(series, current, finalIndex));
        }
        return totalProfit;
    }

    private boolean isOpenAt(Position position, int finalIndex) {
        if (position == null || position.getEntry() == null || position.getEntry().getIndex() > finalIndex) {
            return false;
        }
        return position.getExit() == null || position.getExit().getIndex() > finalIndex;
    }

    private Num calculateAt(BarSeries series, Position position, int finalIndex) {
        NumFactory factory = series.numFactory();
        if (finalIndex < series.getBeginIndex() || position.getEntry() == null
                || position.getEntry().getIndex() > finalIndex) {
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
