/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Analysis criterion that returns the cost basis of the open position.
 *
 * <p>
 * This returns the current open position's entry value plus entry transaction
 * cost. Returns zero when no open position exists.
 * </p>
 *
 * @since 0.22.2
 */
public class OpenPositionCostBasisCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(BarSeries series, Position position) {
        return calculateAt(series, position, series.getEndIndex());
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        int finalIndex = Math.min(series.getEndIndex(), tradingRecord.getEndIndex(series));
        Num totalCostBasis = series.numFactory().zero();
        for (Position position : tradingRecord.getPositions()) {
            if (isOpenAt(position, finalIndex)) {
                totalCostBasis = totalCostBasis.plus(calculateAt(series, position, finalIndex));
            }
        }
        for (Position openLot : OpenPositionUnrealizedProfitCriterion.openLots(tradingRecord)) {
            if (isOpenAt(openLot, finalIndex)) {
                totalCostBasis = totalCostBasis.plus(calculateAt(series, openLot, finalIndex));
            }
        }
        return totalCostBasis;
    }

    private Num calculateAt(BarSeries series, Position position, int finalIndex) {
        NumFactory factory = series.numFactory();
        if (finalIndex < series.getBeginIndex() || !isOpenAt(position, finalIndex)) {
            return factory.zero();
        }
        Trade entry = position.getEntry();
        Num entryPrice = entry.getPricePerAsset(series);
        Num entryCost = entryPrice.multipliedBy(entry.getAmount()).plus(entry.getCost());
        return toSeriesNum(factory, entryCost);
    }

    private boolean isOpenAt(Position position, int finalIndex) {
        if (position == null || position.getEntry() == null || position.getEntry().getIndex() > finalIndex) {
            return false;
        }
        return position.getExit() == null || position.getExit().getIndex() > finalIndex;
    }

    @Override
    public boolean betterThan(Num v1, Num v2) {
        return v1.isLessThan(v2);
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
